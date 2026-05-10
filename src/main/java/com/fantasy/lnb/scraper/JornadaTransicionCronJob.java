package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.jornada.EstadoPartido;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.jornada.JornadaService;
import com.fantasy.lnb.feature.jornada.Partido;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.feature.plantel.PlantelClonadoService;
import com.fantasy.lnb.feature.plantel.PuntuacionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
        @Scheduled(cron = "0 */20 * * * *")
        @Transactional
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
                                .findByEstadoAndFechaFinLessThan(EstadoJornada.EN_JUEGO, ahora)
                                .ifPresent(jornada -> {
                                        log.info("[TRANSICION] Jornada {} → FINALIZADA", jornada.getNumero());

                                        // 1. Finalizar jornada
                                        jornadaService.finalizarJornada(jornada.getId());

                                        // 2. Calcular puntajes de todos los planteles
                                        puntuacionService.calcularPuntajesDeJornada(jornada.getId());

                                        log.info("[TRANSICION] Puntajes calculados para J{}.",
                                                        jornada.getNumero());
                                });
                // C — clonar al abrir la nueva jornada
                jornadaRepo
                                .findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.ABIERTA_A_CAMBIOS)
                                .ifPresent(jornadaAbierta -> {
                                        int clonados = plantelClonadoService
                                                        .clonarJornadaFinalizadaHaciaAbierta();

                                        if (clonados > 0) {
                                                log.info("[TRANSICION] Clonado masivo completado: {} planteles → J{}",
                                                                clonados, jornadaAbierta.getNumero());
                                        }
                                });
                // ── D: PROGRAMADO → FINALIZADO (partidos que ya deberían haber terminado) ──
                List<Partido> programados = partidoRepo
                                .findByEstado(EstadoPartido.PROGRAMADO);

                LocalDateTime hace3Horas = ahora.minusHours(3);

                programados.stream()
                                .filter(p -> p.getFechaHora() != null &&
                                                p.getFechaHora().isBefore(hace3Horas))
                                .forEach(p -> {
                                        p.setEstado(EstadoPartido.FINALIZADO);
                                        partidoRepo.save(p);
                                        log.info("[TRANSICION] Partido {} vs {} → FINALIZADO (fecha: {})",
                                                        p.getEquipoLocal().getSigla(),
                                                        p.getEquipoVisitante().getSigla(),
                                                        p.getFechaHora());
                                });
        }
}