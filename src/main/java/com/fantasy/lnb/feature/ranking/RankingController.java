package com.fantasy.lnb.feature.ranking;

import com.fantasy.lnb.feature.ranking.dto.PosicionGlobalDto;
import com.fantasy.lnb.feature.torneo.TorneoService;
import com.fantasy.lnb.feature.torneo.dto.PosicionTorneoDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ranking")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;
    private final TorneoService torneoService;

    /**
     * GET /api/ranking/global
     * GET /api/ranking/global?limite=50
     * Ranking global de todos los equipos de la plataforma.
     * Público — no requiere autenticación.
     */
    @GetMapping("/global")
    public ResponseEntity<List<PosicionGlobalDto>> rankingGlobal(
            @RequestParam(defaultValue = "100") int limite) {
        return ResponseEntity.ok(rankingService.obtenerRankingGlobal(limite));
    }

    /**
     * GET /api/ranking/torneo/{torneoId}
     * Tabla de posiciones de un torneo específico.
     * Reutiliza el método del TorneoService — el controller es el único
     * punto de entrada, la lógica no se duplica.
     */
    @GetMapping("/torneo/{torneoId}")
    public ResponseEntity<List<PosicionTorneoDto>> rankingTorneo(
            @PathVariable Long torneoId) {
        return ResponseEntity.ok(
                torneoService.obtenerTablaPosiciones(torneoId));
    }
}