package com.fantasy.lnb.feature.auth;

import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UsuarioRepository usuarioRepository;

    /**
     * Endpoint para que el frontend verifique si su JWT sigue siendo válido
     * y obtenga los datos básicos del usuario logueado.
     * El JwtAuthFilter ya validó el token antes de llegar aquí.
     *
     * GET /api/auth/me
     * Header: Authorization: Bearer <token>
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "No autenticado"));
        }

        return usuarioRepository.findByEmail(userDetails.getUsername())
                .map(usuario -> ResponseEntity.ok(Map.of(
                        "id", usuario.getId(),
                        "email", usuario.getEmail(),
                        "nombreDisplay", usuario.getNombreDisplay(),
                        "avatarUrl", usuario.getAvatarUrl() != null
                                ? usuario.getAvatarUrl()
                                : "")))
                .orElse(ResponseEntity.status(404)
                        .body(Map.of("error", "Usuario no encontrado")));
    }
}