package com.fantasy.lnb.feature.auth;

import com.fantasy.lnb.feature.usuario.RolUsuario;
import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.usuario.UsuarioRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Login compartido entre ligas: el login con Google se hace siempre en el backend de la Liga Nacional
 * y los demás backends (ej. Liga Argentina) aceptan ese mismo token (comparten JWT_SECRET).
 * La primera vez que alguien entra a otra liga no existe en su base: con
 * app.auth.autocrear-usuarios=true lo damos de alta con los datos del token.
 */
@Slf4j
@Service
public class AltaDesdeTokenService {

    static final String PROVIDER_TOKEN_COMPARTIDO = "token-compartido";

    private final UsuarioRepository usuarioRepository;
    private final boolean autocrear;

    public AltaDesdeTokenService(
            UsuarioRepository usuarioRepository,
            @Value("${app.auth.autocrear-usuarios:false}") boolean autocrear) {
        this.usuarioRepository = usuarioRepository;
        this.autocrear = autocrear;
    }

    /** El usuario del token; si no existe y está habilitado, lo crea. */
    public Optional<Usuario> buscarOCrear(String email, String nombre, RolUsuario rol) {
        Optional<Usuario> existente = usuarioRepository.findByEmail(email);
        if (existente.isPresent() || !autocrear) {
            return existente;
        }

        try {
            Usuario nuevo = Usuario.builder()
                    .email(email)
                    .nombreDisplay(nombre != null && !nombre.isBlank() ? nombre : email.split("@")[0])
                    .provider(PROVIDER_TOKEN_COMPARTIDO)
                    .providerId(email)
                    .build();
            nuevo = usuarioRepository.save(nuevo); // @PrePersist lo deja en NUEVO y USER

            if (rol == RolUsuario.ADMIN) {
                nuevo.setRol(RolUsuario.ADMIN);
                nuevo = usuarioRepository.save(nuevo);
            }
            log.info("[AUTH] Usuario dado de alta desde el token compartido: {}", email);
            return Optional.of(nuevo);
        } catch (DataIntegrityViolationException e) {
            // Dos requests a la vez del mismo usuario nuevo: el otro ya lo creó
            return usuarioRepository.findByEmail(email);
        }
    }
}
