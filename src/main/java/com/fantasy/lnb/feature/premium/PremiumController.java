package com.fantasy.lnb.feature.premium;

import com.fantasy.lnb.feature.premium.dto.ConsejeroResponseDto;
import com.fantasy.lnb.feature.usuario.UsuarioResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/premium")
@RequiredArgsConstructor
public class PremiumController {

    private final PremiumService premiumService;
    private final UsuarioResolver usuarioResolver;

    @PostMapping("/simular-compra")
    public ResponseEntity<Void> simularCompra(@AuthenticationPrincipal UserDetails userDetails) {
        Long usuarioId = usuarioResolver.resolverIdDesdeEmail(userDetails.getUsername());
        premiumService.simularCompra(usuarioId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/consejero")
    public ResponseEntity<ConsejeroResponseDto> obtenerConsejos(@AuthenticationPrincipal UserDetails userDetails) {
        Long usuarioId = usuarioResolver.resolverIdDesdeEmail(userDetails.getUsername());
        return ResponseEntity.ok(premiumService.obtenerConsejos(usuarioId));
    }

    @PostMapping("/dismiss-vencimiento")
    public ResponseEntity<Void> dismissVencimiento(@AuthenticationPrincipal UserDetails userDetails) {
        Long usuarioId = usuarioResolver.resolverIdDesdeEmail(userDetails.getUsername());
        premiumService.marcarNotificacionVista(usuarioId);
        return ResponseEntity.ok().build();
    }
}
