package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.equipo.EquipoRealRepository;
import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.jornada.EstadoPartido;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.jornada.Partido;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.feature.plantel.MotorPuntuacionPlantel;
import com.fantasy.lnb.feature.plantel.PlantelJornadaRepository;
import com.fantasy.lnb.feature.plantel.PuntuacionService;
import com.fantasy.lnb.scraper.dto.JugadorStatsDto;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScraperCronJob {

        private final ScraperService scraperService;
        private final JornadaRepository jornadaRepo;
        private final EquipoRealRepository equipoRepo;
        private final PartidoRepository partidoRepo;
        private final PlantelJornadaRepository plantelJornadaRepo;
        private final MotorPuntuacionPlantel motorPuntuacionPlantel;
        private final PuntuacionService puntuacionService;

        /**
         * Corre todos los días a las 2 AM.
         * Los partidos de LNB suelen terminar antes de la medianoche,
         * dando 2 horas de margen para que GES publique el acta digital.
         *
         * Flujo:
         * 1. Verifica que haya una jornada EN_JUEGO (si no, no hace nada)
         * 2. Itera sobre los partidos configurados para esa jornada
         * 3. Scraping → aplicar regla de fixture asimétrico → persistir
         */
        @Scheduled(cron = "0 0 2 * * *")
        @Transactional
        public void procesarPartidosDeJornadaActiva() {
                log.info("[CRON] Iniciando scraper de jornada activa...");

                // Buscar partidos FINALIZADOS no procesados
                List<Partido> pendientes = partidoRepo
                                .findByEstadoAndEstadisticasProcesadasFalse(EstadoPartido.FINALIZADO);

                if (pendientes.isEmpty()) {
                        log.info("[CRON] No hay partidos pendientes de procesar.");
                        return;
                }

                log.info("[CRON] Partidos pendientes: {}", pendientes.size());

                Long jornadaIdActiva = pendientes.get(0).getJornada().getId();

                for (Partido partido : pendientes) {
                        try {
                                procesarPartido(partido);
                        } catch (Exception e) {
                                log.error("[CRON] Error procesando partido {}: {}",
                                                partido.getGesHash(), e.getMessage(), e);
                        }
                }

                // Una vez procesados los partidos pendientes, recalculamos toda la fecha
                log.info("[CRON] Actualizando puntajes parciales en vivo para la Jornada {}...", jornadaIdActiva);
                // Le pasamos "false" para indicarle que es un cálculo parcial, no el cierre
                // definitivo
                puntuacionService.calcularPuntajesDeJornada(jornadaIdActiva, false);
        }

        private void procesarPartido(Partido partido) {
                // 1. Extraer marcador
                Optional<MarcadorParser.ResultadoPartido> marcador = MarcadorParser
                                .extraerMarcador(partido.getGesUrl());

                boolean equipoLocalGano = marcador
                                .map(MarcadorParser.ResultadoPartido::localGano)
                                .orElse(false);

                // Persistir resultado en la entidad Partido
                marcador.ifPresent(m -> {
                        partido.setPuntosLocal(m.puntosLocal());
                        partido.setPuntosVisitante(m.puntosVisitante());
                });

                // 2. Extraer estadísticas de jugadores
                List<JugadorStatsDto> dtos = scraperService
                                .extraerEstadisticasDePartido(partido.getGesUrl());

                // 3. Persistir estadísticas (método ya existente)
                scraperService.persistirEstadisticas(
                                dtos,
                                partido.getGesHash(),
                                partido.getFechaHora(),
                                partido.getEquipoLocal().getId(),
                                partido.getEquipoVisitante().getId(),
                                equipoLocalGano,
                                partido.getJornada());

                // 4. Calcular puntos del DT
                // marcador.ifPresent(m -> calcularPuntajeDt(partido, m));

                // 5. Marcar partido como procesado
                partido.setEstadisticasProcesadas(true);
                partido.setEstado(EstadoPartido.PROCESADO);
                partidoRepo.save(partido);

                log.info("[CRON] Partido procesado: {} {} - {} {}",
                                partido.getEquipoLocal().getSigla(), partido.getPuntosLocal(),
                                partido.getPuntosVisitante(), partido.getEquipoVisitante().getSigla());
        }

        /**
         * Configuración manual de los partidos de la jornada activa.
         *
         * IMPORTANTE: Estos valores se actualizan manualmente antes de cada jornada
         * hasta que implementemos el scraper de fixture en una iteración futura.
         * Los IDs de equipos deben coincidir con los de la tabla equipo_real en BD.
         */
        private List<PartidoConfig> obtenerPartidosDeJornadaActual() {
                return List.of(
                                new PartidoConfig(
                                                "YybUoJvn64Jz4tY986BMmQ==", // gesPartidoId
                                                "https://www.laliganacional.com.ar/laliga/partido/estadisticas/YybUoJvn64Jz4tY986BMmQ==", // url
                                                LocalDateTime.of(2026, 4, 21, 21, 0), // fechaPartido
                                                1L, // equipoLocalId
                                                3L, // equipoVisitanteId
                                                false, // equipoLocalGano
                                                "San Lorenzo vs Quimsa" // descripcion
                                )
                // Agregar más partidos de la jornada aquí:
                // new PartidoConfig(...)
                );
        }

        /**
         * Record inmutable para configurar cada partido a scrapear.
         * Reemplaza los parámetros sueltos del Módulo 1.
         */
        record PartidoConfig(
                        String gesPartidoId,
                        String url,
                        LocalDateTime fechaPartido,
                        Long equipoLocalId,
                        Long equipoVisitanteId,
                        boolean equipoLocalGano,
                        String descripcion) {
        }

        private void calcularPuntajeDt(
                        Partido partido,
                        MarcadorParser.ResultadoPartido marcador) {

                // Buscar todos los planteles que tienen como DT a alguien
                // del equipo local o visitante en la jornada activa
                Jornada jornada = partido.getJornada();

                plantelJornadaRepo
                                .findAllByJornadaIdWithJugadores(jornada.getId())
                                .forEach(plantel -> {
                                        if (plantel.getDt() == null)
                                                return;

                                        Long equipoDtId = plantel.getDt().getEquipoReal().getId();
                                        Long localId = partido.getEquipoLocal().getId();
                                        Long visitanteId = partido.getEquipoVisitante().getId();

                                        if (!equipoDtId.equals(localId) &&
                                                        !equipoDtId.equals(visitanteId))
                                                return;

                                        // Calcular puntaje según diferencia
                                        boolean dtGano = equipoDtId.equals(localId)
                                                        ? marcador.localGano()
                                                        : !marcador.localGano();

                                        double puntajeDt = motorPuntuacionPlantel
                                                        .calcularPuntajeDt(
                                                                        dtGano ? marcador.puntosLocal()
                                                                                        : marcador.puntosVisitante(),
                                                                        dtGano ? marcador.puntosVisitante()
                                                                                        : marcador.puntosLocal());

                                        // Sumar al puntaje de la jornada del plantel
                                        plantel.setPuntajeObtenidoFecha(
                                                        plantel.getPuntajeObtenidoFecha() + puntajeDt);
                                        plantelJornadaRepo.save(plantel);

                                        log.info("[DT] {} → {:.2f} pts (equipo {} | diferencia {})",
                                                        plantel.getDt().getNombreCompleto(),
                                                        puntajeDt,
                                                        plantel.getDt().getEquipoReal().getSigla(),
                                                        marcador.diferencia());
                                });
        }
}