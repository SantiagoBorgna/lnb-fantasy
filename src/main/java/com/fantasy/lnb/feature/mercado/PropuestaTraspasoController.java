package com.fantasy.lnb.feature.mercado;

import com.fantasy.lnb.feature.mercado.dto.PropuestaTraspasoDto;
import com.fantasy.lnb.feature.mercado.dto.PropuestaTraspasoRequest;
import com.fantasy.lnb.feature.usuario.UsuarioResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/traspasos")
@RequiredArgsConstructor
public class PropuestaTraspasoController {

    private final TraspasoUsuarioService traspasoService;
    private final UsuarioResolver usuarioResolver;

    private Long getUsuarioId(UserDetails userDetails) {
        return usuarioResolver.resolverIdDesdeEmail(userDetails.getUsername());
    }

    @GetMapping("/torneo/{torneoId}")
    public ResponseEntity<org.springframework.data.domain.Page<PropuestaTraspasoDto>> obtenerMisPropuestas(
            @PathVariable Long torneoId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return ResponseEntity.ok(traspasoService.obtenerMisPropuestas(torneoId, getUsuarioId(userDetails), pageable));
    }

    @PostMapping("/proponer")
    public ResponseEntity<PropuestaTraspasoDto> proponerTraspaso(
            @RequestBody PropuestaTraspasoRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(traspasoService.proponerTraspaso(getUsuarioId(userDetails), request));
    }

    @PostMapping("/{id}/aceptar")
    public ResponseEntity<Void> aceptarTraspaso(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        traspasoService.aceptarTraspaso(getUsuarioId(userDetails), id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/rechazar")
    public ResponseEntity<Void> rechazarTraspaso(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        traspasoService.rechazarTraspaso(getUsuarioId(userDetails), id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<Void> cancelarTraspaso(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        traspasoService.cancelarTraspaso(getUsuarioId(userDetails), id);
        return ResponseEntity.ok().build();
    }
}
