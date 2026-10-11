package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.jornada.EstadoPartido;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.jornada.JornadaService;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.feature.notificaciones.PushNotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.cache.CacheManager;

import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class JornadaTransicionCronJob {

        // Cuánto se espera, pasada la fechaFin, a que GES publique las estadísticas de
        // los partidos que faltan antes de cerrar la jornada igual (por ejemplo, un
        // partido suspendido que nunca va a tener acta).
        private static final Duration ESPERA_MAXIMA_ESTADISTICAS = Duration.ofHours(3);

        private final JornadaRepository jornadaRepo;
        private final PartidoRepository partidoRepo;
        private final ScraperCronJob scraperCronJob;
        private final JornadaService jornadaService;
        private final JornadaCierreService jornadaCierreService;
        private final PushNotificationService pushNotificationService;
        private final CacheManager cacheManager;

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
         * → Carga las estadísticas pendientes y la transiciona a FINALIZADA
         * (habilita el CronJob de precios)
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
                                                if (!estadisticasListasParaCerrar(jornada, ahora)) {
                                                        return;
                                                }

                                                log.info("[TRANSICION] Jornada {} → FINALIZADA", jornada.getNumero());

                                                // Todo lo que sigue es un único @Transactional (ver
                                                // JornadaCierreService): si algo falla a mitad de camino,
                                                // hace rollback de TODO este bloque — incluida finalizarJornada.
                                                // Así, la jornada sigue EN_JUEGO y el próximo ciclo (5 min
                                                // después) reintenta el cierre completo de nuevo, en vez de
                                                // quedar a mitad de camino sin reintento posible (una vez
                                                // FINALIZADA, este mismo query ya no la vuelve a encontrar).
                                                jornadaCierreService.cerrarJornadaCompleta(jornada);

                                                log.info("[TRANSICION] Puntajes definitivos calculados para J{}.",
                                                                jornada.getNumero());

                                                // Notificación de final de jornada. Deliberadamente afuera de
                                                // la transacción de arriba: nunca debe poder frenar ni
                                                // revertir el cierre real de la jornada si falla.
                                                try {
                                                        pushNotificationService.enviarNotificacionMasiva(
                                                                "Jornada finalizada",
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
                                                        "¡Prepará tu equipo!",
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
         * El scraper de estadísticas corre una vez por noche (2 AM), pero el cierre
         * puede dispararse antes: si se cierra con partidos sin cargar, los puntajes
         * definitivos, el ranking, los promedios y los precios se calculan sin esas
         * estadísticas y después nadie los recalcula. Por eso, antes de cerrar se
         * cargan los partidos pendientes y se espera a que estén todos procesados,
         * hasta ESPERA_MAXIMA_ESTADISTICAS después del fin de la jornada.
         */
        private boolean estadisticasListasParaCerrar(Jornada jornada, LocalDateTime ahora) {
                scraperCronJob.procesarPartidosDeJornadaActiva();

                long sinProcesar = partidoRepo.findByJornada_Id(jornada.getId()).stream()
                                .filter(p -> p.getEstado() != EstadoPartido.PROCESADO)
                                .count();

                if (sinProcesar == 0) {
                        return true;
                }

                if (ahora.isAfter(jornada.getFechaFin().plus(ESPERA_MAXIMA_ESTADISTICAS))) {
                        log.error("[TRANSICION] J{} se cierra con {} partido(s) sin estadísticas: pasaron más de {} h desde su fin.",
                                        jornada.getNumero(), sinProcesar, ESPERA_MAXIMA_ESTADISTICAS.toHours());
                        return true;
                }

                log.warn("[TRANSICION] J{} no se cierra todavía: {} partido(s) sin estadísticas cargadas. Se reintenta en 5 min.",
                                jornada.getNumero(), sinProcesar);
                return false;
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
