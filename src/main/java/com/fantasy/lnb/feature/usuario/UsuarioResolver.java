package com.fantasy.lnb.feature.usuario;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UsuarioResolver {

    private final UsuarioRepository usuarioRepo;

    /**
     * Resuelve el ID de la BD a partir del email que viene en el JWT.
     * El JwtAuthFilter ya validó que el token es correcto antes de llegar aquí.
     */
    public Long resolverIdDesdeEmail(String email) {
        return usuarioRepo.findByEmail(email)
                .map(Usuario::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "Usuario autenticado no encontrado en BD: " + email));
    }

    public Usuario resolverDesdeEmail(String email) {
        return usuarioRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException(
                        "Usuario autenticado no encontrado en BD: " + email));
    }
}