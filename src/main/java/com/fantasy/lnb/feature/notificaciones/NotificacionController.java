package com.fantasy.lnb.feature.notificaciones;

import com.fantasy.lnb.feature.notificaciones.dto.PushRequestDto;
import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final PushNotificationService pushService;
    private final UsuarioRepository usuarioRepository;

    @PostMapping("/suscribir")
    public ResponseEntity<Void> suscribirDispositivo(
            @RequestBody PushRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails) {

        // Buscamos el usuario logueado usando el JWT provisto en la request
        Usuario usuario = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        pushService.guardarSuscripcion(
                usuario,
                request.getEndpoint(),
                request.getKeys().getP256dh(),
                request.getKeys().getAuth());

        return ResponseEntity.ok().build();
    }
}