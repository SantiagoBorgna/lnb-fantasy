package com.fantasy.lnb.feature.premium;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/premium")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    @PostMapping("/checkout")
    public ResponseEntity<?> createCheckout(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No autorizado"));
        }
        
        Long usuarioId = Long.parseLong(principal.getName());
        String initPoint = checkoutService.createSubscriptionPreference(usuarioId);
        
        return ResponseEntity.ok(Map.of("init_point", initPoint));
    }
}
