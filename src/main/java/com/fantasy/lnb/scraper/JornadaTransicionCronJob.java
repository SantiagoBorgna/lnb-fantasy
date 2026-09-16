package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.jornada.JornadaService;
import com.fantasy.lnb.feature.plantel.PlantelClonadoService;
import com.fantasy.lnb.feature.plantel.PuntuacionService;
import com.fantasy.lnb.feature.notificaciones.PushNotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class JornadaTransicionCronJob {

        private final JornadaRepository jornadaRepo;
        private final JornadaService jornadaService;
        private final PuntuacionService puntuacionService;
        private final PlantelClonadoService plantelClonadoService;
        private final PushNotificationService pushNotificationService;
        private final CacheManager cacheManager;
        private final com.fantasy.lnb.feature.dt.DirectorTecnicoService directorTecnicoService;
        private final PreciosCronJob preciosCronJob;

        // Referencia lazy al propio bean (proxy de Spring): la necesitamos para
        // que cerrarJornadaCompleta() pase por el proxy y su @Transactional se
        // aplique de verdad — una llamada directa (this.cerrarJornadaCompleta())
        // se saltea el proxy y no abre transacción.
        @Lazy
        private final JornadaTransicionCronJob self;

        /**
         * Corre cada 5 minutos.
         * Evalúa cuatro condiciones independientes en cada ciclo. Cada sección
         * corre en su propio try/catch: si una sección falla, las demás igual
         * se evalúan en el mismo ciclo, y el próximo ciclo (5 min después) la
         * vuelve a intentar. Antes esto era todo un único @Transactional, así
         * que un error en cualquier punto (por ejemplo, mandar una notificación
         * push) hacía rollback de TODO, incluyendo transiciones de otras
         * jornadas que ya se habían aplicado en el mismo ciclo.
         *
         * A) ¿Hay alguna jornada ABIERTA cuya fechaInicio ya pasó?
         * → Transiciona a EN_JUEGO (bloquea cambios de plantel)
         *
         * B) ¿Hay alguna jornada EN_JUEGO cuya fechaFin ya pasó?
         * → Transiciona a FINALIZADA (habilita el CronJob de precios)
         */
        @Scheduled(cron = "0 */5 * * * *")
        public void evaluarTransiciones() {
                LocalDateTime ahora = LocalDateTime.now();
                log.debug("[TRANSICION] Evaluando estados de jornadas en {}", ahora);

                // ── A: ABIERTA → EN_JUEGO ───────────────────────────────────────────
                try {
                        jornadaRepo
                                        .findByEstadoAndFechaInicioLessThanEqual(EstadoJornada.ABIERTA_A_CAMBIOS, ahora)
                                        .ifPresent(jornada -> {
                                                log.info("[TRANSICION] Jornada {} alcanzó su fechaInicio. Iniciando ventana de juego...",
                                                                jornada.getNumero());
                                                // iniciarJornada es @Transactional propio: para cuando esta
                                                // llamada vuelve, ya hizo commit.
                                                jornadaService.iniciarJornada(jornada.getId());
                                                limpiarCache("jornadas", "partidos");
                                        });
                } catch (Exception e) {
                        log.error("[TRANSICION] Error abriendo ventana de juego: {}", e.getMessage(), e);
                }

                // ── B: EN_JUEGO → FINALIZADA ────────────────────────────────────────
                try {
                        jornadaRepo
                                        .findByEstadoAndFechaFinLessThan(EstadoJornada.EN_JUEGO, ahora)
                                        .ifPresent(jornada -> {
                                                log.info("[TRANSICION] Jornada {} → FINALIZADA", jornada.getNumero());

                                                // Todo lo que sigue es un único @Transactional (ver
                                                // cerrarJornadaCompleta): si algo falla a mitad de camino,
                                                // hace rollback de TODO este bloque — incluida finalizarJornada.
                                                // Así, la jornada sigue EN_JUEGO y el próximo ciclo (5 min
                                                // después) reintenta el cierre completo de nuevo, en vez de
                                                // quedar a mitad de camino sin reintento posible (una vez
                                                // FINALIZADA, este mismo query ya no la vuelve a encontrar).
                                                self.cerrarJornadaCompleta(jornada);

                                                log.info("[TRANSICION] Puntajes definitivos calculados para J{}.",
                                                                jornada.getNumero());

                                                // Notificación de final de jornada. Deliberadamente afuera de
                                                // la transacción de arriba: nunca debe poder frenar ni
                                                // revertir el cierre real de la jornada si falla.
                                                try {
                                                        pushNotificationService.enviarNotificacionMasiva(
                                                                "Jornada finalizada 🏀",
                                                                "La jornada terminó, vení a ver cómo sumó tu equipo."
                                                        );
                                                } catch (Exception e) {
                                                        log.error("[TRANSICION] Falló el envío de la notificación de cierre de J{} (no bloqueante): {}",
                                                                        jornada.getNumero(), e.getMessage(), e);
                                                }

                                                limpiarCache("jornadas");
                                        });
                } catch (Exception e) {
                        log.error("[TRANSICION] Error finalizando jornada: {}", e.getMessage(), e);
                }

                // ── C: PROGRAMADO → FINALIZADO (partidos) ───────────────────────────
                try {
                        // Método transaccional propio: recorre EquipoReal en relación lazy
                        // de cada Partido para el log, y eso necesita una sesión abierta.
                        jornadaService.cerrarPartidosVencidos(ahora);
                } catch (Exception e) {
                        log.error("[TRANSICION] Error cerrando partidos programados: {}", e.getMessage(), e);
                }

                // ── D: RECORDATORIO 5 HORAS ANTES ──────────────────────────────────────────
                try {
                        LocalDateTime en5Horas = ahora.plusHours(5);
                        jornadaRepo.findFirstByEstadoAndFechaInicioLessThanEqualAndNotificacionPreviaEnviadaFalse(
                                        EstadoJornada.ABIERTA_A_CAMBIOS, en5Horas)
                                        .ifPresent(jornada -> {
                                                log.info("[TRANSICION] Enviando notificación de 5 horas para jornada {}", jornada.getNumero());
                                                String horaStr = String.format("%02d:%02d", jornada.getFechaInicio().getHour(), jornada.getFechaInicio().getMinute());
                                                pushNotificationService.enviarNotificacionMasiva(
                                                        "¡Prepará tu equipo! ⏱️",
                                                        "Hoy a las " + horaStr + " horas empieza una nueva jornada, no olvides alistar tu equipo"
                                                );
                                                jornada.setNotificacionPreviaEnviada(true);
                                                jornadaRepo.save(jornada);
                                                limpiarCache("jornadas");
                                        });
                } catch (Exception e) {
                        log.error("[TRANSICION] Error enviando recordatorio de 5 horas: {}", e.getMessage(), e);
                }
        }

        /**
         * Cierre "real" de una jornada: cambia su estado, calcula puntajes,
         * actualiza promedios de DTs, recalcula precios y clona planteles hacia
         * la próxima jornada — todo en una única transacción. Si cualquier paso
         * falla, se revierte todo (la jornada vuelve a quedar EN_JUEGO) para que
         * el próximo ciclo del cron reintente el cierre completo desde cero.
         *
         * Llamar siempre a través de "self" (el proxy), nunca con this., para
         * que el @Transactional se aplique.
         */
        @Transactional
        public void cerrarJornadaCompleta(Jornada jornada) {
                jornadaService.finalizarJornada(jornada.getId());
                puntuacionService.calcularPuntajesDeJornada(jornada.getId(), true);
                directorTecnicoService.actualizarPromediosDts();
                preciosCronJob.actualizarPrecios();

                int clonados = plantelClonadoService.clonarDesdeJornadaFinalizada(jornada);
                if (clonados > 0) {
                        log.info("[TRANSICION] Clonado masivo completado. J{} fue base para {} planteles nuevos.",
                                        jornada.getNumero(), clonados);
                }
        }

        private void limpiarCache(String... nombres) {
                for (String nombre : nombres) {
                        if (cacheManager.getCache(nombre) != null) {
                                cacheManager.getCache(nombre).clear();
                        }
                }
                log.info("[CACHE] Cachés limpiados: {}", String.join(", ", nombres));
        }
}
