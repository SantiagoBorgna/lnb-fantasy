package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.jornada.JornadaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class JornadaTransicionCronJob {

        private final JornadaRepository jornadaRepo;
        private final JornadaService jornadaService;

        /**
         * Corre cada 5 minutos.
         * Evalúa dos condiciones independientes en cada ciclo:
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
                jornadaRepo
                                .findByEstadoAndFechaInicioLessThanEqual(
                                                EstadoJornada.ABIERTA_A_CAMBIOS, ahora)
                                .ifPresent(jornada -> {
                                        log.info("[TRANSICION] Jornada {} alcanzó su fechaInicio. " +
                                                        "Iniciando ventana de juego...", jornada.getNumero());
                                        jornadaService.iniciarJornada(jornada.getId());
                                });

                // ── B: EN_JUEGO → FINALIZADA ────────────────────────────────────────
                jornadaRepo
                                .findByEstadoAndFechaFinLessThan(
                                                EstadoJornada.EN_JUEGO, ahora)
                                .ifPresent(jornada -> {
                                        log.info("[TRANSICION] Jornada {} superó su fechaFin. " +
                                                        "Cerrando ventana...", jornada.getNumero());
                                        jornadaService.finalizarJornada(jornada.getId());
                                });
        }
}