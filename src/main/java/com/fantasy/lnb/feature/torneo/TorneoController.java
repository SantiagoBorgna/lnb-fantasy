package com.fantasy.lnb.feature.torneo;

import com.fantasy.lnb.feature.torneo.dto.CrearTorneoRequest;
import com.fantasy.lnb.feature.torneo.dto.PosicionTorneoDto;
import com.fantasy.lnb.feature.torneo.dto.TorneoDto;
import com.fantasy.lnb.feature.usuario.UsuarioResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/torneos")
@RequiredArgsConstructor
public class TorneoController {

    private final TorneoService torneoService;
    private final UsuarioResolver usuarioResolver;

    // GET /api/torneos?nombre=xxx — buscador de torneos públicos
    @GetMapping
    public ResponseEntity<List<TorneoDto>> listarPublicos(
            @RequestParam(required = false) String nombre) {
        return ResponseEntity.ok(torneoService.listarPublicos(nombre));
    }

    // GET /api/torneos/mis-torneos — torneos del usuario autenticado
    @GetMapping("/mis-torneos")
    public ResponseEntity<List<TorneoDto>> misTorneos(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long usuarioId = usuarioResolver.resolverIdDesdeEmail(
                userDetails.getUsername());
        return ResponseEntity.ok(torneoService.listarMisTorneos(usuarioId));
    }

    // GET /api/torneos/{id}/tabla — tabla de posiciones de un torneo
    @GetMapping("/{id}/tabla")
    public ResponseEntity<List<PosicionTorneoDto>> tabla(
            @PathVariable Long id) {
        return ResponseEntity.ok(torneoService.obtenerTablaPosiciones(id));
    }

    // POST /api/torneos — crear un torneo nuevo
    @PostMapping
    public ResponseEntity<TorneoDto> crear(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody CrearTorneoRequest request) {
        Long usuarioId = usuarioResolver.resolverIdDesdeEmail(
                userDetails.getUsername());
        return ResponseEntity.ok(torneoService.crearTorneo(usuarioId, request));
    }

    // POST /api/torneos/unirse/{codigo} — unirse por UUID
    @PostMapping("/unirse/{codigo}")
    public ResponseEntity<TorneoDto> unirse(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String codigo) {
        Long usuarioId = usuarioResolver.resolverIdDesdeEmail(
                userDetails.getUsername());
        return ResponseEntity.ok(
                torneoService.unirseATorneo(usuarioId, codigo));
    }
}