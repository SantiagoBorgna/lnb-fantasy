package com.fantasy.lnb.scraper;

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

    private static final String URL_PARTIDO_PRUEBA = "https://www.laliganacional.com.ar/laliga/partido/estadisticas/YybUoJvn64Jz4tY986BMmQ==";

    /**
     * Se ejecuta cada 1 minuto para validar la PoC.
     * Una vez confirmado el funcionamiento, cambiar el cron a la
     * expresión definitiva (ej: "0 0 2 * * *" = todos los días a las 2 AM).
     *
     * fixedDelay vs cron: usamos cron para tener control horario preciso
     * en producción desde el mismo campo sin cambiar código.
     */
    // @Scheduled(cron = "0 * * * * *")
    public void ejecutarScraperDePrueba() {

        log.info("==================================================");
        log.info("[CRON] Iniciando scraper con persistencia...");
        log.info("==================================================");

        List<JugadorStatsDto> jugadores = scraperService.extraerEstadisticasDePartido(URL_PARTIDO_PRUEBA);

        if (jugadores.isEmpty()) {
            log.warn("[CRON] Sin jugadores extraídos. Abortando persistencia.");
            return;
        }

        // ── Parámetros de prueba hardcodeados ──────────────────────────────────
        // En el Módulo 4 estos valores vendrán del fixture real de la Jornada.
        scraperService.persistirEstadisticas(
                jugadores,
                "PARTIDO-GES-PRUEBA-001", // gesPartidoId
                LocalDateTime.now(), // fechaPartido
                true, // equipoLocalGano (hardcoded para PoC)
                1L // jornadaId (debe existir en tu BD)
        );

        log.info("[CRON] Ciclo completado.");
    }
}