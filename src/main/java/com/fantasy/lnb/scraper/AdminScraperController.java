package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.jornada.Partido;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/admin/scraper")
@RequiredArgsConstructor
public class AdminScraperController {

    private final JugadorCrawlerService jugadorCrawlerService;
    private final FixtureCrawlerService fixtureCrawlerService;
    private final ScraperCronJob scraperCronJob;
    private final JornadaTransicionCronJob jornadaTransicionCronJob;

    /**
     * POST /api/admin/scraper/jugadores/sincronizar
     *
     * Dispara el crawler de jugadores de forma ASÍNCRONA.
     * Devuelve 202 Accepted inmediatamente — el proceso corre en background.
     * El resultado se loguea en la consola del servidor.
     *
     * Proteger con rol ADMIN antes de ir a producción.
     */
    @PostMapping("/jugadores/sincronizar")
    public ResponseEntity<Map<String, Object>> sincronizarJugadores() {
        log.info("[ADMIN] Sincronización de jugadores iniciada manualmente.");

        // Ejecutar en un hilo separado para no bloquear el request
        CompletableFuture.runAsync(() -> {
            JugadorCrawlerService.SincronizacionResultado resultado = jugadorCrawlerService.sincronizarJugadores();
            log.info("[ADMIN] Sincronización finalizada: {}", resultado);
        });

        return ResponseEntity.accepted().body(Map.of(
                "mensaje", "Sincronización iniciada en background.",
                "detalle", "Revisá los logs del servidor para ver el progreso."));
    }

    /**
     * POST /api/admin/scraper/fixture/sincronizar
     * Carga los partidos de una jornada específica.
     * Body: { "jornadaId": 1, "urlFixture": "https://..." }
     */
    @PostMapping("/fixture/sincronizar")
    public ResponseEntity<Map<String, Object>> sincronizarFixture(
            @RequestBody Map<String, Object> body) {

        Long jornadaId = Long.valueOf(body.get("jornadaId").toString());
        String urlFixture = body.get("urlFixture").toString();

        CompletableFuture.runAsync(() -> {
            FixtureCrawlerService.FixtureResultado resultado = fixtureCrawlerService.sincronizarFixture(jornadaId,
                    urlFixture);
            log.info("[ADMIN] Fixture cargado: {}", resultado);
        });

        return ResponseEntity.accepted().body(Map.of(
                "mensaje", "Sincronización de fixture iniciada.",
                "jornadaId", jornadaId));
    }

    /**
     * GET /api/admin/scraper/forzar-cron
     * Ejecuta manualmente la transición de estados y la recolección de
     * estadísticas.
     */
    @GetMapping("/forzar-cron")
    public ResponseEntity<Map<String, Object>> forzarCronScraper() {
        log.info("[ADMIN] Ejecución manual de CronJobs solicitada.");

        CompletableFuture.runAsync(() -> {
            try {
                // 1. Primero forzamos la evaluación de estados (esto pasa partidos a
                // FINALIZADO)
                log.info("[ADMIN] 1. Evaluando transiciones de Jornadas y Partidos...");
                jornadaTransicionCronJob.evaluarTransiciones();

                // 2. Luego forzamos el Scraper (busca los FINALIZADOS y les carga las
                // estadísticas)
                log.info("[ADMIN] 2. Recolectando estadísticas...");
                scraperCronJob.procesarPartidosDeJornadaActiva();

                log.info("[ADMIN] Secuencia manual finalizada.");
            } catch (Exception e) {
                log.error("[ADMIN] Error forzando los cron: {}", e.getMessage());
            }
        });

        return ResponseEntity.accepted().body(Map.of(
                "mensaje", "Secuencia de actualización iniciada.",
                "orden", "1. Actualizar Estados -> 2. Recolectar Estadísticas",
                "detalle", "Revisá los logs de Render para confirmar."));
    }
}