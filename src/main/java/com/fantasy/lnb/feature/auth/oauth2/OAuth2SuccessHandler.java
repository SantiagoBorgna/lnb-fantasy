package com.fantasy.lnb.feature.auth.oauth2;

import com.fantasy.lnb.feature.auth.jwt.JwtService;
import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.usuario.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * Se ejecuta una sola vez después de que OAuth2UserService procesó al usuario.
     * Genera el JWT y redirige al frontend con el token en el fragment de la URL.
     *
     * Usamos fragment (#) en lugar de query param (?) para que el token
     * nunca llegue al servidor en requests subsiguientes ni quede en logs.
     * El frontend lee window.location.hash al cargar y lo guarda en
     * memoria/localStorage.
     */
    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException(
                        "Usuario no encontrado post-OAuth2: " + email));

        String jwt = jwtService.generarToken(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombreDisplay());

        log.info("[OAUTH2] JWT generado para: {}", email);

        // Redirige al frontend con el token en el fragment
        // Ejemplo: http://localhost:5173/auth/callback#token=eyJhbGci...
        String redirectUrl = frontendUrl + "/auth/callback#token=" + jwt;
        response.sendRedirect(redirectUrl);
    }
}