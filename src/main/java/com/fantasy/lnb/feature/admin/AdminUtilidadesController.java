package com.fantasy.lnb.feature.admin;

import com.fantasy.lnb.feature.auth.LogoutService;
import com.fantasy.lnb.feature.draft.DraftService;
import com.fantasy.lnb.feature.mercado.WaiverService;
import com.fantasy.lnb.feature.premium.PremiumService;
import com.fantasy.lnb.feature.showdown.ShowdownPuntuacionService;
import com.fantasy.lnb.scraper.JornadaTransicionCronJob;
import com.fantasy.lnb.scraper.PreciosCronJob;
import com.fantasy.lnb.scraper.ScraperCronJob;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Permite disparar manualmente, desde el panel de admin, cada uno de los
 * CronJobs del juego sin tener que esperar a su horario programado.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/utilidades")
@RequiredArgsConstructor
public class AdminUtilidadesController {

    private final ScraperCronJob scraperCronJob;
    private final JornadaTransicionCronJob jornadaTransicionCronJob;
    private final PreciosCronJob preciosCronJob;
    private final DraftService draftService;
    private final ShowdownPuntuacionService showdownPuntuacionService;
    private final WaiverService waiverService;
    private final PremiumService premiumService;
    private final LogoutService logoutService;

    @PostMapping("/crons/scraper-partidos")
    public ResponseEntity<Map<String, Object>> correrScraperPartidos() {
        return ejecutar("Scraper de partidos/estadísticas", scraperCronJob::procesarPartidosDeJornadaActiva);
    }

    @PostMapping("/crons/transicion-jornadas")
    public ResponseEntity<Map<String, Object>> correrTransicionJornadas() {
        return ejecutar("Transición de jornadas y partidos", jornadaTransicionCronJob::evaluarTransiciones);
    }

    @PostMapping("/crons/precios")
    public ResponseEntity<Map<String, Object>> correrActualizacionPrecios() {
        return ejecutar("Actualización de precios de mercado", preciosCronJob::actualizarPrecios);
    }

    @PostMapping("/crons/draft-autopicks")
    public ResponseEntity<Map<String, Object>> correrAutoPicksDraft() {
        return ejecutar("Auto-picks de draft vencidos", draftService::procesarAutoPicksVencidos);
    }

    @PostMapping("/crons/showdown-puntajes")
    public ResponseEntity<Map<String, Object>> correrPuntajesShowdown() {
        return ejecutar("Puntajes de eventos Relámpago en curso", showdownPuntuacionService::procesarPuntajesEventosEnCurso);
    }

    @PostMapping("/crons/waivers")
    public ResponseEntity<Map<String, Object>> correrWaivers() {
        return ejecutar("Procesamiento de reclamos de waivers", waiverService::procesarWaiversForzado);
    }

    @PostMapping("/crons/premium-vencidos")
    public ResponseEntity<Map<String, Object>> correrPremiumVencidos() {
        return ejecutar("Revocación de suscripciones Premium vencidas", premiumService::revocarSuscripcionesVencidas);
    }

    @PostMapping("/crons/tokens-expirados")
    public ResponseEntity<Map<String, Object>> correrLimpiezaTokens() {
        return ejecutar("Limpieza de tokens expirados", logoutService::limpiarTokensExpirados);
    }

    private ResponseEntity<Map<String, Object>> ejecutar(String nombre, Runnable job) {
        log.info("[ADMIN][UTILIDADES] Ejecución manual solicitada: {}", nombre);

        CompletableFuture.runAsync(() -> {
            try {
                job.run();
                log.info("[ADMIN][UTILIDADES] Finalizado: {}", nombre);
            } catch (Exception e) {
                log.error("[ADMIN][UTILIDADES] Error ejecutando '{}': {}", nombre, e.getMessage(), e);
            }
        });

        return ResponseEntity.accepted().body(Map.of(
                "mensaje", "'" + nombre + "' iniciado en background.",
                "detalle", "Revisá los logs del servidor para confirmar el resultado."));
    }
}
