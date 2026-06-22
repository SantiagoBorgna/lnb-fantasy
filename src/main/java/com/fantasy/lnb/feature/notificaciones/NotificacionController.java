package com.fantasy.lnb.feature.notificaciones;

import com.fantasy.lnb.feature.notificaciones.dto.PushRequestDto;
import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/notificaciones")
@RequiredArgsConstructor
@Slf4j // <-- Agregado para logging
public class NotificacionController {

    private final PushNotificationService pushService;
    private final UsuarioRepository usuarioRepository;

    @PostMapping("/suscribir")
    public ResponseEntity<Void> suscribirDispositivo(
            @RequestBody PushRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("[DEBUG] Endpoint /api/notificaciones/suscribir alcanzado.");
        log.info("[DEBUG] Usuario solicitante: {}", userDetails != null ? userDetails.getUsername() : "null");
        
        if (request == null) {
            log.error("[DEBUG] El request body (PushRequestDto) es NULL!");
            return ResponseEntity.badRequest().build();
        }
        
        log.info("[DEBUG] Payload recibido: endpoint={}, p256dh={}, auth={}", 
            request.getEndpoint() != null ? "PRESENTE" : "MISSING",
            (request.getKeys() != null && request.getKeys().getP256dh() != null) ? "PRESENTE" : "MISSING",
            (request.getKeys() != null && request.getKeys().getAuth() != null) ? "PRESENTE" : "MISSING"
        );

        Usuario usuario = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        pushService.guardarSuscripcion(
                usuario,
                request.getEndpoint(),
                request.getKeys().getP256dh(),
                request.getKeys().getAuth());

        log.info("[DEBUG] Suscripción procesada exitosamente y retornando 200 OK.");
        return ResponseEntity.ok().build();
    }
}