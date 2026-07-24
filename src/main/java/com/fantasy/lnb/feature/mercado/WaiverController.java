package com.fantasy.lnb.feature.mercado;

import com.fantasy.lnb.feature.mercado.dto.WaiverClaimDto;
import com.fantasy.lnb.feature.torneo.dto.PosicionTorneoDto;
import com.fantasy.lnb.feature.usuario.UsuarioResolver;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/waivers")
@RequiredArgsConstructor
public class WaiverController {

    private final WaiverService waiverService;
    private final UsuarioResolver usuarioResolver;

    /**
     * GET /api/waivers/{torneoId}/prioridad
     * Devuelve la lista de equipos en orden de prioridad de Waivers.
     */
    @GetMapping("/{torneoId}/prioridad")
    public ResponseEntity<List<PosicionTorneoDto>> obtenerOrdenPrioridad(
            @PathVariable Long torneoId) {
        // Aprovechamos PosicionTorneoDto para mandar el equipo, usuario y la prioridadWaiver
        // Podemos mapear los equipos del torneo ordenados por prioridadWaiver ASC.
        // Wait, PosicionTorneoDto no tiene prioridadWaiver! I will create a simple response DTO inline or inside WaiverService.
        // For now, let's just let the service handle it and return a list of maps or a new DTO.
        return ResponseEntity.ok(waiverService.obtenerOrdenPrioridad(torneoId));
    }

    /**
     * GET /api/waivers/{torneoId}/mis-reclamos
     * Devuelve los reclamos pendientes del usuario en este torneo.
     */
    @GetMapping("/{torneoId}/mis-reclamos")
    public ResponseEntity<List<WaiverClaimDto>> obtenerMisReclamos(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long torneoId) {
        
        Long usuarioId = usuarioResolver.resolverIdDesdeEmail(userDetails.getUsername());
        return ResponseEntity.ok(waiverService.obtenerMisReclamosPendientes(usuarioId, torneoId));
    }

    @PostMapping("/reclamo")
    public ResponseEntity<Void> registrarReclamo(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody com.fantasy.lnb.feature.mercado.dto.WaiverClaimRequest request) {
        
        Long usuarioId = usuarioResolver.resolverIdDesdeEmail(userDetails.getUsername());
        waiverService.registrarReclamo(usuarioId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{torneoId}/transacciones")
    public ResponseEntity<org.springframework.data.domain.Page<com.fantasy.lnb.feature.mercado.dto.TransaccionDraftDto>> obtenerTransacciones(
            @PathVariable Long torneoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return ResponseEntity.ok(waiverService.obtenerTransaccionesTorneo(torneoId, pageable));
    }

    @GetMapping("/fase")
    public ResponseEntity<Boolean> esFaseRestringida() {
        return ResponseEntity.ok(waiverService.esFaseRestringida());
    }

    /**
     * ENDPOINT TEMPORAL PARA PRUEBAS:
     * Fuerza el procesamiento de los reclamos de waiver ignorando la regla de las 4 horas.
     */
    @PostMapping("/test-procesar")
    public ResponseEntity<Void> testProcesarWaivers() {
        waiverService.procesarWaiversForzado();
        return ResponseEntity.ok().build();
    }

}
