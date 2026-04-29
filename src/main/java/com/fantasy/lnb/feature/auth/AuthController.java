package com.fantasy.lnb.feature.auth;

import com.fantasy.lnb.feature.auth.jwt.JwtService;
import com.fantasy.lnb.feature.equipo.EquipoReal;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

        private final UsuarioRepository usuarioRepository;
        private final EquipoRealRepository equipoRealRepo;
        private final LogoutService logoutService;
        private final JwtService jwtService;

        @GetMapping("/me")
        public ResponseEntity<?> me(@AuthenticationPrincipal UserDetails userDetails) {

                // 1. Verificamos si hay usuario logueado
                if (userDetails == null) {
                        return ResponseEntity.status(401).body(Map.of("error", "No autenticado"));
                }

                // 2. Buscamos en BD (Retorno temprano si no existe)
                Optional<Usuario> userOpt = usuarioRepository.findByEmail(userDetails.getUsername());
                if (userOpt.isEmpty()) {
                        return ResponseEntity.status(404).body(Map.of("error", "Usuario no encontrado"));
                }

                // 3. Si llegamos acá, el usuario existe. Armamos la respuesta.
                Usuario usuario = userOpt.get();
                var respuesta = new java.util.HashMap<String, Object>();

                respuesta.put("id", usuario.getId());
                respuesta.put("email", usuario.getEmail());
                respuesta.put("nombreDisplay", usuario.getNombreDisplay());
                respuesta.put("avatarUrl", usuario.getAvatarUrl() != null ? usuario.getAvatarUrl() : "");

                // ← Campo nuevo: el frontend decide a dónde redirigir
                respuesta.put("estadoOnboarding", usuario.getEstadoOnboarding());

                // Agregar equipo favorito si existe
                if (usuario.getEquipoFavorito() != null) {
                        respuesta.put("equipoFavorito", Map.of(
                                        "id", usuario.getEquipoFavorito().getId(),
                                        "nombre", usuario.getEquipoFavorito().getNombre(),
                                        "sigla", usuario.getEquipoFavorito().getSigla(),
                                        "colorPrincipal", usuario.getEquipoFavorito().getColorPrincipal(),
                                        "colorSecundario", usuario.getEquipoFavorito().getColorSecundario()));
                } else {
                        respuesta.put("equipoFavorito", null);
                }

                return ResponseEntity.ok(respuesta);
        }

        @PatchMapping("/equipo-favorito/{equipoId}")
        public ResponseEntity<?> setEquipoFavorito(
                        @AuthenticationPrincipal UserDetails userDetails,
                        @PathVariable Long equipoId) {

                if (userDetails == null) {
                        return ResponseEntity.status(401).build();
                }

                // 1. Buscar usuario
                Optional<Usuario> userOpt = usuarioRepository.findByEmail(userDetails.getUsername());
                if (userOpt.isEmpty()) {
                        return ResponseEntity.status(401).build();
                }

                // 2. Buscar equipo
                Optional<EquipoReal> equipoOpt = equipoRealRepo.findById(equipoId);
                if (equipoOpt.isEmpty()) {
                        return ResponseEntity.notFound().build();
                }

                // 3. Modificar y guardar
                Usuario usuario = userOpt.get();
                EquipoReal equipo = equipoOpt.get();

                usuario.setEquipoFavorito(equipo);
                usuarioRepository.save(usuario);

                return ResponseEntity.ok(Map.of(
                                "mensaje", "Equipo favorito actualizado.",
                                "equipo", equipo.getNombre()));
        }

        /**
         * POST /api/auth/logout
         * Revoca el token actual — el frontend debe eliminarlo del localStorage.
         */
        @PostMapping("/logout")
        public ResponseEntity<?> logout(
                        @RequestHeader(value = "Authorization", required = false) String authHeader) {

                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring(7);
                        logoutService.revocarToken(token);
                }

                return ResponseEntity.ok(Map.of("mensaje", "Sesión cerrada correctamente."));
        }
}