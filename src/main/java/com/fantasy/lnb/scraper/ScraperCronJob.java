package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.equipo.EquipoRealRepository;
import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.scraper.dto.JugadorStatsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScraperCronJob {

    private final ScraperService scraperService;
    private final JornadaRepository jornadaRepo;
    private final EquipoRealRepository equipoRepo;

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
    public void procesarPartidosDeJornadaActiva() {

        log.info("================================================");
        log.info("[CRON] Iniciando scraper de jornada activa...");
        log.info("================================================");

        // Verificar que haya jornada activa antes de hacer requests a GES
        boolean hayJornadaActiva = jornadaRepo
                .findByEstado(EstadoJornada.EN_JUEGO)
                .isPresent();

        if (!hayJornadaActiva) {
            log.info("[CRON] Sin jornada EN_JUEGO. Scraper en espera.");
            return;
        }

        // ── Lista de partidos a scrapear ────────────────────────────────────
        // En el estado actual, los partidos se configuran manualmente aquí.
        // En una iteración futura esto vendrá de una tabla `partido_real`
        // poblada por un scraper previo del fixture de la LNB.
        List<PartidoConfig> partidos = obtenerPartidosDeJornadaActual();

        if (partidos.isEmpty()) {
            log.warn("[CRON] No hay partidos configurados para esta jornada.");
            return;
        }

        for (PartidoConfig partido : partidos) {
            log.info("[CRON] Procesando: {} | URL: {}",
                    partido.descripcion(), partido.url());
            try {
                List<JugadorStatsDto> dtos = scraperService.extraerEstadisticasDePartido(partido.url());

                scraperService.persistirEstadisticas(
                        dtos,
                        partido.gesPartidoId(),
                        partido.fechaPartido(),
                        partido.equipoLocalId(),
                        partido.equipoVisitanteId(),
                        partido.equipoLocalGano());
            } catch (Exception e) {
                // Un partido que falla no debe cortar el procesamiento del resto
                log.error("[CRON] Error procesando partido {}: {}",
                        partido.gesPartidoId(), e.getMessage(), e);
            }
        }

        log.info("[CRON] Ciclo de scraping completado.");
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
}