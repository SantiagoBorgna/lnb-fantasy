package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.dt.DirectorTecnicoService;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository;
import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.jornada.EstadoPartido;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.jornada.Partido;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.feature.mercado.MercadoService;
import com.fantasy.lnb.feature.plantel.PlantelJornadaRepository;
import com.fantasy.lnb.feature.plantel.PuntuacionService;
import com.fantasy.lnb.feature.torneo.TipoPuntuacion;
import com.fantasy.lnb.feature.torneo.TorneoEquipo;
import com.fantasy.lnb.feature.torneo.TorneoEquipoRepository;
import com.fantasy.lnb.feature.torneo.TorneoH2HService;
import com.fantasy.lnb.feature.usuario.EquipoVirtual;
import com.fantasy.lnb.feature.usuario.EquipoVirtualRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rehace el cierre de una jornada que ya está FINALIZADA, para corregirla cuando
 * se cerró con estadísticas faltantes o mal cargadas.
 *
 * El cierre original suma el puntaje de cada plantel al ranking en el momento, así
 * que volver a correrlo duplicaría esos puntos. Por eso acá el ranking no se
 * ajusta por diferencia: se reconstruye sumando los puntajes de todos los
 * planteles de las jornadas FINALIZADAS. Todo el recálculo se puede repetir las
 * veces que haga falta con el mismo resultado.
 *
 * No toca los precios del mercado: su ajuste por jornada acumula un delta cada vez
 * que corre, así que no es repetible. Los promedios de los jugadores sí se
 * recalculan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JornadaRecalculoService {

        private static final double TOLERANCIA = 0.001;

        private final JornadaRepository jornadaRepo;
        private final PartidoRepository partidoRepo;
        private final EstadisticaPartidoRepository estadisticaRepo;
        private final ScraperCronJob scraperCronJob;
        private final PuntuacionService puntuacionService;
        private final PlantelJornadaRepository plantelRepo;
        private final EquipoVirtualRepository equipoVirtualRepo;
        private final TorneoEquipoRepository torneoEquipoRepo;
        private final TorneoH2HService h2hService;
        private final DirectorTecnicoService directorTecnicoService;
        private final MercadoService mercadoService;
        private final CacheManager cacheManager;

        /**
         * Chequeo rápido para poder responder 400/409 antes de lanzar el recálculo en
         * background.
         */
        public Jornada validarRecalculable(Long jornadaId) {
                Jornada jornada = jornadaRepo.findById(jornadaId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Jornada no encontrada: " + jornadaId));

                if (!jornada.estaFinalizada()) {
                        throw new IllegalStateException(
                                        "Solo se puede recalcular una jornada FINALIZADA. La jornada "
                                                        + jornada.getNumero() + " está " + jornada.getEstado() + ".");
                }
                return jornada;
        }

        /**
         * Todo en una única transacción: si algo falla se revierte entero y los datos
         * quedan como estaban.
         *
         * 1. Completa las estadísticas de los equipos que quedaron sin ninguna
         * 2. Recalcula el puntaje de todos los planteles de la jornada
         * 3. Reconstruye el ranking global y el de los torneos
         * 4. Rehace la tabla de los torneos H2H
         * 5. Actualiza los promedios de DTs y de jugadores
         */
        @Transactional
        public void recalcularJornadaFinalizada(Long jornadaId) {
                Jornada jornada = validarRecalculable(jornadaId);
                log.info("[RECALCULO] Iniciando el recálculo de la jornada {}...", jornada.getNumero());

                int partidosCompletados = completarEstadisticasFaltantes(jornada);

                puntuacionService.calcularPuntajesDeJornada(jornadaId, false);
                reconstruirRankings();
                h2hService.recalcularTodoH2H();
                directorTecnicoService.actualizarPromediosDts();
                mercadoService.actualizarPromediosTodos();

                // Después del commit: si se limpiaran antes, una lectura concurrente podría
                // volver a cachear los datos viejos. Son todos los cachés que guardan
                // puntajes de jornada, promedios o el estado de los partidos.
                limpiarCachesAlConfirmar("mercado", "lideres", "partidos",
                                "ranking_jornada", "ranking_jornada_torneo",
                                "plantel_historico", "plantel_historico_torneo");

                log.info("[RECALCULO] Jornada {} recalculada. Partidos completados: {}.",
                                jornada.getNumero(), partidosCompletados);
        }

        /**
         * Vuelve a procesar los partidos de la jornada en los que algún equipo no tiene
         * ninguna estadística cargada, en orden cronológico. La regla del fixture
         * asimétrico se aplica por equipo, así que solo se cargan los equipos que
         * faltan y los que ya tienen stats no se duplican.
         *
         * @return cantidad de partidos reprocesados con éxito
         */
        int completarEstadisticasFaltantes(Jornada jornada) {
                List<Partido> partidos = partidoRepo.findByJornada_Id(jornada.getId()).stream()
                                .filter(p -> p.getEstado() != EstadoPartido.PROGRAMADO)
                                .sorted(Comparator.comparing(Partido::getFechaHora))
                                .toList();

                int completados = 0;
                for (Partido partido : partidos) {
                        boolean localSinStats = !estadisticaRepo.existsByEquipoRealIdAndJornadaId(
                                        partido.getEquipoLocal().getId(), jornada.getId());
                        boolean visitanteSinStats = !estadisticaRepo.existsByEquipoRealIdAndJornadaId(
                                        partido.getEquipoVisitante().getId(), jornada.getId());

                        if (!localSinStats && !visitanteSinStats) {
                                continue;
                        }

                        try {
                                scraperCronJob.procesarPartido(partido);
                                completados++;
                        } catch (Exception e) {
                                log.error("[RECALCULO] No se pudieron completar las estadísticas de {}: {}",
                                                partido.getGesUrl(), e.getMessage());
                        }
                }
                return completados;
        }

        /**
         * Deja el ranking global y el de los torneos NO H2H igual a la suma de los
         * puntajes de los planteles de las jornadas FINALIZADAS. El de los H2H lo
         * reconstruye TorneoH2HService.recalcularTodoH2H.
         */
        void reconstruirRankings() {
                Map<Long, Double> globalPorUsuario = new HashMap<>();
                for (Object[] fila : plantelRepo.sumarPuntajesGlobalesPorUsuario(EstadoJornada.FINALIZADA)) {
                        globalPorUsuario.put(((Number) fila[0]).longValue(), redondear(((Number) fila[1]).doubleValue()));
                }

                int globalesCorregidos = 0;
                for (EquipoVirtual equipo : equipoVirtualRepo.findAll()) {
                        double correcto = globalPorUsuario.getOrDefault(equipo.getUsuario().getId(), 0.0);
                        if (Math.abs(equipo.getPuntajeGlobal() - correcto) > TOLERANCIA) {
                                log.info("[RECALCULO] Ranking global usuarioId={}: {} → {}",
                                                equipo.getUsuario().getId(), equipo.getPuntajeGlobal(), correcto);
                                equipo.setPuntajeGlobal(correcto);
                                equipoVirtualRepo.save(equipo);
                                globalesCorregidos++;
                        }
                }

                Map<String, Double> torneoPorUsuario = new HashMap<>();
                for (Object[] fila : plantelRepo.sumarPuntajesDeTorneosPorUsuario(EstadoJornada.FINALIZADA)) {
                        torneoPorUsuario.put(
                                        claveTorneoUsuario(((Number) fila[0]).longValue(), ((Number) fila[1]).longValue()),
                                        redondear(((Number) fila[2]).doubleValue()));
                }

                int torneosCorregidos = 0;
                for (TorneoEquipo te : torneoEquipoRepo.findAll()) {
                        if (te.getTorneo().getTipoPuntuacion() == TipoPuntuacion.H2H) {
                                continue;
                        }

                        double correcto = torneoPorUsuario.getOrDefault(
                                        claveTorneoUsuario(te.getTorneo().getId(),
                                                        te.getEquipoVirtual().getUsuario().getId()),
                                        0.0);
                        if (Math.abs(te.getPuntajeGlobal() - correcto) > TOLERANCIA) {
                                log.info("[RECALCULO] Ranking torneoId={} equipoVirtualId={}: {} → {}",
                                                te.getTorneo().getId(), te.getEquipoVirtual().getId(),
                                                te.getPuntajeGlobal(), correcto);
                                te.setPuntajeGlobal(correcto);
                                torneoEquipoRepo.save(te);
                                torneosCorregidos++;
                        }
                }

                log.info("[RECALCULO] Rankings reconstruidos. Corregidos: {} equipos globales, {} participaciones en torneos.",
                                globalesCorregidos, torneosCorregidos);
        }

        private String claveTorneoUsuario(Long torneoId, Long usuarioId) {
                return torneoId + ":" + usuarioId;
        }

        private double redondear(double valor) {
                return Math.round(valor * 100.0) / 100.0;
        }

        private void limpiarCachesAlConfirmar(String... nombres) {
                Runnable limpiar = () -> {
                        for (String nombre : nombres) {
                                Cache cache = cacheManager.getCache(nombre);
                                if (cache != null) {
                                        cache.clear();
                                }
                        }
                        log.info("[CACHE] Cachés limpiados: {}", String.join(", ", nombres));
                };

                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                                @Override
                                public void afterCommit() {
                                        limpiar.run();
                                }
                        });
                } else {
                        limpiar.run();
                }
        }
}
