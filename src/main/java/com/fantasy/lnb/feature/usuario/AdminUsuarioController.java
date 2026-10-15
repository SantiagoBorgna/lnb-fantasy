package com.fantasy.lnb.feature.usuario;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.fantasy.lnb.feature.notificaciones.SuscripcionPushRepository;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/usuarios")
@RequiredArgsConstructor
public class AdminUsuarioController {

    private final UsuarioRepository usuarioRepo;
    private final SuscripcionPushRepository suscripcionRepo;

    /**
     * GET /api/admin/usuarios/resumen
     * Números para el panel de admin: registrados, cuántos terminaron el onboarding
     * (ACTIVO) y cuántos tienen las notificaciones activadas.
     */
    @GetMapping("/resumen")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> resumen() {
        long registrados = usuarioRepo.count();
        long activos = usuarioRepo.countByEstadoOnboarding(EstadoOnboarding.ACTIVO);
        return ResponseEntity.ok(Map.of(
                "registrados", registrados,
                "activos", activos,
                "sinTerminarOnboarding", registrados - activos,
                "usuariosConPush", suscripcionRepo.contarUsuariosConSuscripcion(),
                "dispositivosPush", suscripcionRepo.count()));
    }

    /**
     * PATCH /api/admin/usuarios/{id}/rol
     * Cambia el rol de un usuario. Solo accesible por ADMIN.
     * Body: { "rol": "ADMIN" } o { "rol": "USER" }
     */
    @PatchMapping("/{id}/rol")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> cambiarRol(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        RolUsuario nuevoRol;
        try {
            nuevoRol = RolUsuario.valueOf(body.get("rol").toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Rol inválido. Valores válidos: USER, ADMIN"));
        }

        return usuarioRepo.findById(id)
                .map(usuario -> {
                    usuario.setRol(nuevoRol);
                    usuarioRepo.save(usuario);
                    return ResponseEntity.ok(Map.of(
                            "mensaje", "Rol actualizado.",
                            "usuario", usuario.getEmail(),
                            "rol", nuevoRol.name()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/admin/usuarios
     * Lista todos los usuarios con su rol.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> listarUsuarios() {
        return ResponseEntity.ok(
                usuarioRepo.findAll().stream()
                        .map(u -> Map.of(
                                "id", u.getId(),
                                "email", u.getEmail(),
                                "nombre", u.getNombreDisplay(),
                                "rol", u.getRol().name(),
                                "estado", u.getEstadoOnboarding().name()))
                        .toList());
    }
}