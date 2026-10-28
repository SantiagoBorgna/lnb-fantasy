package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.estadisticas.EstadisticaPartido;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository;
import com.fantasy.lnb.feature.estadisticas.MotorPuntuacion;
import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.jornada.Partido;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.feature.plantel.PuntuacionService;
import com.fantasy.lnb.scraper.dto.JugadorStatsDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Set;

/**
 * Suma el bono de MVP a los partidos de una jornada EN_JUEGO que se cargaron
 * antes de que el bono existiera.
 *
 * La valoración de GES no se guarda, así que cada partido se vuelve a leer de
 * GES para saber quién fue el MVP. Las estadísticas no se borran: solo se le
 * suma el bono a quien corresponde y se marca fueMvp. Un partido que ya tiene
 * algún MVP marcado se saltea, así que se puede correr las veces que haga falta
 * sin duplicar puntos.
 *
 * Solo para jornadas EN_JUEGO: el ranking se suma al cierre desde las
 * estadísticas, así que alcanza con recalcular el puntaje parcial. En una
 * jornada FINALIZADA el ranking ya quedó sumado sin el bono.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MvpJornadaService {

        private final JornadaRepository jornadaRepo;
        private final PartidoRepository partidoRepo;
        private final EstadisticaPartidoRepository estadisticaRepo;
        private final ScraperService scraperService;
        private final PuntuacionService puntuacionService;
        private final CacheManager cacheManager;

        /**
         * Chequeo rápido para poder responder 400/409 antes de lanzar el proceso en
         * background.
         */
        public Jornada validarAplicable(Long jornadaId) {
                Jornada jornada = jornadaRepo.findById(jornadaId)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Jornada no encontrada: " + jornadaId));

                if (jornada.getEstado() != EstadoJornada.EN_JUEGO) {
                        throw new IllegalStateException(
                                        "El MVP solo se puede aplicar a una jornada EN_JUEGO. La jornada "
                                                        + jornada.getNumero() + " está " + jornada.getEstado() + ".");
                }
                return jornada;
        }

        @Transactional
        public void aplicarMvpJornada(Long jornadaId) {
                Jornada jornada = validarAplicable(jornadaId);
                log.info("[MVP] Aplicando el bono de MVP a la jornada {}...", jornada.getNumero());

                int bonos = 0;
                for (Partido partido : partidoRepo.findByJornada_Id(jornadaId)) {
                        if (!Boolean.TRUE.equals(partido.getEstadisticasProcesadas())) {
                                continue;
                        }
                        bonos += aplicarMvpPartido(partido);
                }

                puntuacionService.calcularPuntajesDeJornada(jornadaId, false);

                limpiarCachesAlConfirmar("mercado", "lideres", "partidos",
                                "ranking_jornada", "ranking_jornada_torneo",
                                "plantel_historico", "plantel_historico_torneo");

                log.info("[MVP] Jornada {} lista. Bonos de MVP sumados: {}.", jornada.getNumero(), bonos);
        }

        /** @return cantidad de jugadores a los que se les sumó el bono */
        int aplicarMvpPartido(Partido partido) {
                List<EstadisticaPartido> estadisticas = estadisticaRepo.findByGesPartidoId(partido.getGesHash());

                if (estadisticas.isEmpty()) {
                        return 0;
                }
                if (estadisticas.stream().anyMatch(e -> Boolean.TRUE.equals(e.getFueMvp()))) {
                        log.info("[MVP] {} ya tiene el MVP aplicado. Omitido.", partido.getGesHash());
                        return 0;
                }

                List<JugadorStatsDto> planilla = scraperService.extraerEstadisticasDePartido(partido.getGesUrl());
                if (planilla.isEmpty()) {
                        log.error("[MVP] No se pudo leer la planilla de {}. Omitido.", partido.getGesUrl());
                        return 0;
                }

                Set<Long> mvps = MotorPuntuacion.mvpsDelPartido(planilla);

                int bonos = 0;
                for (EstadisticaPartido estadistica : estadisticas) {
                        if (!mvps.contains(estadistica.getJugadorReal().getGesId())) {
                                continue;
                        }

                        double puntaje = estadistica.getPuntajeFantasyCalculado() + MotorPuntuacion.PTS_MVP;
                        estadistica.setPuntajeFantasyCalculado(Math.round(puntaje * 100.0) / 100.0);
                        estadistica.setFueMvp(true);
                        estadisticaRepo.save(estadistica);
                        bonos++;

                        log.info("[MVP] {} | {} → {} pts",
                                        estadistica.getJugadorReal().getNombreCompleto(), partido.getGesHash(),
                                        estadistica.getPuntajeFantasyCalculado());
                }

                if (bonos == 0) {
                        // El MVP no suma: no está en nuestra BD o su equipo ya había jugado
                        log.info("[MVP] {}: el MVP de la planilla no tiene estadísticas que sumen.",
                                        partido.getGesHash());
                }
                return bonos;
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
