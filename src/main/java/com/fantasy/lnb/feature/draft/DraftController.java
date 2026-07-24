package com.fantasy.lnb.feature.draft;

import com.fantasy.lnb.feature.draft.dto.DraftStateDto;
import com.fantasy.lnb.feature.usuario.UsuarioResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/torneos/{torneoId}/draft")
@RequiredArgsConstructor
public class DraftController {

    private final DraftService draftService;
    private final UsuarioResolver usuarioResolver;

    @PostMapping("/iniciar")
    public ResponseEntity<?> iniciarDraft(
            @PathVariable Long torneoId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long adminId = usuarioResolver.resolverIdDesdeEmail(userDetails.getUsername());
        draftService.iniciarDraft(torneoId, adminId);
        return ResponseEntity.ok(Map.of("mensaje", "Draft iniciado exitosamente."));
    }

    @GetMapping
    public ResponseEntity<DraftStateDto> obtenerEstadoDraft(
            @PathVariable Long torneoId) {
        return ResponseEntity.ok(draftService.obtenerEstadoDraft(torneoId));
    }

    @PostMapping("/pick/{jugadorRealId}")
    public ResponseEntity<?> elegirJugador(
            @PathVariable Long torneoId,
            @PathVariable Long jugadorRealId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long usuarioId = usuarioResolver.resolverIdDesdeEmail(userDetails.getUsername());
        draftService.elegirJugador(usuarioId, torneoId, jugadorRealId);
        return ResponseEntity.ok(Map.of("mensaje", "Jugador elegido exitosamente."));
    }

    @PostMapping("/pick-dt/{dtId}")
    public ResponseEntity<?> elegirDt(
            @PathVariable Long torneoId,
            @PathVariable Long dtId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long usuarioId = usuarioResolver.resolverIdDesdeEmail(userDetails.getUsername());
        draftService.elegirDt(usuarioId, torneoId, dtId);
        return ResponseEntity.ok(Map.of("mensaje", "Director Técnico elegido exitosamente."));
    }
}
