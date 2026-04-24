package com.fantasy.lnb.feature.auth;

import com.fantasy.lnb.feature.equipo.EquipoRealRepository;
import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

        private final UsuarioRepository usuarioRepository;
        private final EquipoRealRepository equipoRealRepo;

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

        /**
         * PATCH /api/auth/equipo-favorito/{equipoId}
         * Permite al usuario setear su equipo favorito de la LNB.
         * Se muestra como camiseta en el ranking y en el dashboard.
         */
        @PatchMapping("/equipo-favorito/{equipoId}")
        public ResponseEntity<?> setEquipoFavorito(
                        @AuthenticationPrincipal UserDetails userDetails,
                        @PathVariable Long equipoId) {

                String email = userDetails.getUsername();

                return usuarioRepository.findByEmail(email)
                                .map(usuario -> equipoRealRepo.findById(equipoId)
                                                .map(equipo -> {
                                                        usuario.setEquipoFavorito(equipo);
                                                        usuarioRepository.save(usuario);
                                                        return ResponseEntity.ok(Map.of(
                                                                        "mensaje", "Equipo favorito actualizado.",
                                                                        "equipo", equipo.getNombre()));
                                                })
                                                .orElse(ResponseEntity.notFound().build()))
                                .orElse(ResponseEntity.status(401).build());
        }
}