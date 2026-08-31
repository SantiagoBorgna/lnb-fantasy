package com.fantasy.lnb.feature.premium;

import com.fantasy.lnb.feature.usuario.UsuarioResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/premium")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;
    private final UsuarioResolver usuarioResolver;

    @PostMapping("/checkout")
    public ResponseEntity<?> createCheckout(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        }
        
        Long usuarioId = usuarioResolver.resolverIdDesdeEmail(userDetails.getUsername());
        String initPoint = checkoutService.createSubscriptionPreference(usuarioId);
        
        return ResponseEntity.ok(Map.of("init_point", initPoint));
    }
}
