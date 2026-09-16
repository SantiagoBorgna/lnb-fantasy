package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.jornada.EstadoPartido;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.jornada.JornadaService;
import com.fantasy.lnb.feature.jornada.Partido;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.feature.plantel.PlantelClonadoService;
import com.fantasy.lnb.feature.plantel.PuntuacionService;
import com.fantasy.lnb.feature.notificaciones.PushNotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.cache.CacheManager;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JornadaTransicionCronJob {

        private final JornadaRepository jornadaRepo;
        private final JornadaService jornadaService;
        private final PuntuacionService puntuacionService;
        private final PlantelClonadoService plantelClonadoService;
        private final PartidoRepository partidoRepo;
        private final PushNotificationService pushNotificationService;
        private final CacheManager cacheManager;
        private final com.fantasy.lnb.feature.dt.DirectorTecnicoService directorTecnicoService;
        private final PreciosCronJob preciosCronJob;

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

                                                // 1. Finalizar jornada (transacción propia)
                                                jornadaService.finalizarJornada(jornada.getId());

                                                // 2. Calcular puntajes de todos los planteles (Cierre definitivo)
                                                puntuacionService.calcularPuntajesDeJornada(jornada.getId(), true);

                                                // 2.1 Actualizar promedios históricos de los DTs
                                                directorTecnicoService.actualizarPromediosDts();

                                                // 2.2 Recalcular precios de mercado — una única vez, con la
                                                // jornada ya finalizada y sus puntajes ya calculados.
                                                preciosCronJob.actualizarPrecios();

                                                log.info("[TRANSICION] Puntajes definitivos calculados para J{}.",
                                                                jornada.getNumero());

                                                // Notificación de final de jornada. Nunca debe poder frenar el
                                                // resto del cierre (clonado de planteles, etc.) si falla.
                                                try {
                                                        pushNotificationService.enviarNotificacionMasiva(
                                                                "Jornada finalizada 🏀",
                                                                "La jornada terminó, vení a ver cómo sumó tu equipo."
                                                        );
                                                } catch (Exception e) {
                                                        log.error("[TRANSICION] Falló el envío de la notificación de cierre de J{} (no bloqueante): {}",
                                                                        jornada.getNumero(), e.getMessage(), e);
                                                }

                                                // 3. Clona los equipos de la jornada que acaba de terminar hacia la próxima.
                                                int clonados = plantelClonadoService.clonarDesdeJornadaFinalizada(jornada);
                                                if (clonados > 0) {
                                                        log.info("[TRANSICION] Clonado masivo completado. J{} fue base para {} planteles nuevos.",
                                                                        jornada.getNumero(), clonados);
                                                }

                                                limpiarCache("jornadas");
                                        });
                } catch (Exception e) {
                        log.error("[TRANSICION] Error finalizando jornada: {}", e.getMessage(), e);
                }

                // ── C: PROGRAMADO → FINALIZADO (partidos) ───────────────────────────
                try {
                        List<Partido> programados = partidoRepo.findByEstado(EstadoPartido.PROGRAMADO);
                        LocalDateTime hace3Horas = ahora.minusHours(3);

                        programados.stream()
                                        .filter(p -> p.getFechaHora() != null && p.getFechaHora().isBefore(hace3Horas))
                                        .forEach(p -> {
                                                p.setEstado(EstadoPartido.FINALIZADO);
                                                partidoRepo.save(p);
                                                log.info("[TRANSICION] Partido {} vs {} → FINALIZADO (fecha: {})",
                                                                p.getEquipoLocal().getSigla(),
                                                                p.getEquipoVisitante().getSigla(),
                                                                p.getFechaHora());
                                        });
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

        private void limpiarCache(String... nombres) {
                for (String nombre : nombres) {
                        if (cacheManager.getCache(nombre) != null) {
                                cacheManager.getCache(nombre).clear();
                        }
                }
                log.info("[CACHE] Cachés limpiados: {}", String.join(", ", nombres));
        }
}
