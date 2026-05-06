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
    private final FixtureCrawlerService fixtureCrawlerService; // Punto 6

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
}