package com.fantasy.lnb.feature.usuario;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/usuarios")
@RequiredArgsConstructor
public class AdminUsuarioController {

    private final UsuarioRepository usuarioRepo;

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