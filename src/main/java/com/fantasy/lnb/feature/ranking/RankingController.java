package com.fantasy.lnb.feature.ranking;

import com.fantasy.lnb.feature.ranking.dto.PosicionGlobalDto;
import com.fantasy.lnb.feature.torneo.TorneoService;
import com.fantasy.lnb.feature.torneo.dto.PosicionTorneoDto;
import com.fantasy.lnb.feature.usuario.UsuarioRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ranking")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;
    private final TorneoService torneoService;
    private final UsuarioRepository usuarioRepository;

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

    /**
     * GET /api/ranking/torneo/{torneoId}/jornada/{jornadaId}
     * Ranking de una jornada especfica para un torneo.
     */
    @GetMapping("/torneo/{torneoId}/jornada/{jornadaId}")
    public ResponseEntity<List<PosicionGlobalDto>> rankingJornadaTorneo(
            @PathVariable Long torneoId,
            @PathVariable Long jornadaId,
            @RequestParam(defaultValue = "100") int limite) {
        return ResponseEntity.ok(
                rankingService.obtenerRankingJornadaTorneo(torneoId, jornadaId, limite));
    }

    /**
     * GET /api/ranking/jornada/{jornadaId}
     * Ranking de una jornada específica — ordenado por puntajeObtenidoFecha.
     */
    @GetMapping("/jornada/{jornadaId}")
    public ResponseEntity<List<PosicionGlobalDto>> rankingJornada(
            @PathVariable Long jornadaId,
            @RequestParam(defaultValue = "100") int limite) {
        return ResponseEntity.ok(
                rankingService.obtenerRankingJornada(jornadaId, limite));
    }

    /**
     * GET /api/ranking/mi-posicion
     * Devuelve la posición del usuario autenticado en el ranking global.
     */
    /**
     * GET /api/ranking/mi-posicion
     * Devuelve la posición del usuario autenticado en el ranking global.
     */
    @GetMapping("/mi-posicion")
    public ResponseEntity<PosicionGlobalDto> miPosicion(
            @AuthenticationPrincipal UserDetails userDetails) {

        // 1. Verificamos que haya alguien logueado
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        // 2. Buscamos al usuario en la BD usando su email
        var userOpt = usuarioRepository.findByEmail(userDetails.getUsername());
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // 3. Extraemos el ID y llamamos al servicio
        Long usuarioId = userOpt.get().getId();

        return rankingService.obtenerMiPosicion(usuarioId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }
}