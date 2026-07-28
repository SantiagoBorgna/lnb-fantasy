package com.fantasy.lnb.feature.auth.oauth2;

import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2UserService extends DefaultOAuth2UserService {

    private final UsuarioRepository usuarioRepository;

    /**
     * Spring Security llama a este método después de que Google/Microsoft
     * autenticó al usuario y nos devolvió sus datos.
     * Aquí hacemos el "upsert": si el usuario no existe lo creamos,
     * si ya existe actualizamos su último login.
     */
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest)
            throws OAuth2AuthenticationException {

        // Delegamos la carga del OAuth2User al comportamiento por defecto
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String provider = userRequest.getClientRegistration()
                .getRegistrationId(); // "google" o "azure"

        procesarUsuario(oAuth2User, provider);

        return oAuth2User;
    }

    public void procesarUsuario(OAuth2User oAuth2User, String provider) {

        // Los atributos difieren levemente entre Google y Microsoft
        String email, nombre, providerId, avatarUrl;

        if ("google".equals(provider)) {
            email = oAuth2User.getAttribute("email");
            nombre = oAuth2User.getAttribute("name");
            providerId = oAuth2User.getAttribute("sub");
            avatarUrl = oAuth2User.getAttribute("picture");
        } else {
            // Azure AD / Microsoft
            email = oAuth2User.getAttribute("email");
            nombre = oAuth2User.getAttribute("name");
            providerId = oAuth2User.getAttribute("oid"); // Object ID de Azure
            avatarUrl = null; // Microsoft Graph no devuelve foto en este scope
        }

        if (email == null) {
            log.error("[OAUTH2] El proveedor {} no devolvió email. Abortando.", provider);
            throw new OAuth2AuthenticationException("email_not_found");
        }

        usuarioRepository.findByEmail(email).ifPresentOrElse(
                // ── Usuario existente: actualizamos datos que pueden cambiar ──
                usuario -> {
                    usuario.setNombreDisplay(nombre);
                    usuario.setAvatarUrl(avatarUrl);
                    usuario.setUltimoLogin(LocalDateTime.now());
                    usuarioRepository.save(usuario);
                    log.info("[OAUTH2] Login existente: {} via {}", email, provider);
                },
                // ── Usuario nuevo: lo registramos ────────────────────────────
                () -> {
                    Usuario nuevo = Usuario.builder()
                            .email(email)
                            .nombreDisplay(nombre)
                            .provider(provider)
                            .providerId(providerId)
                            .avatarUrl(avatarUrl)
                            .build();
                    usuarioRepository.save(nuevo);
                    log.info("[OAUTH2] Nuevo usuario registrado: {} via {}", email, provider);
                });
    }
}