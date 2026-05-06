package com.fantasy.lnb.feature.auth.oauth2;

import com.fantasy.lnb.feature.auth.LogoutService;
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
        private final LogoutService logoutService;

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

                // Revocar token anterior si existe (invalidar sesión previa)
                // El token anterior viene en el header si el usuario ya estaba logueado
                String headerAnterior = request.getHeader("Authorization");
                if (headerAnterior != null && headerAnterior.startsWith("Bearer ")) {
                        String tokenAnterior = headerAnterior.substring(7);
                        try {
                                logoutService.revocarToken(tokenAnterior);
                        } catch (Exception e) {
                                // Si el token anterior era inválido, ignorar — el nuevo se genera igual
                                log.debug("[OAUTH2] Token anterior inválido al revocar: {}", e.getMessage());
                        }
                }

                String jwt = jwtService.generarToken(
                                usuario.getId(),
                                usuario.getEmail(),
                                usuario.getNombreDisplay(),
                                usuario.getRol());

                log.info("[OAUTH2] JWT generado para: {}", email);

                // Redirige al frontend con el token en el fragment
                // Ejemplo: http://localhost:5173/auth/callback#token=eyJhbGci...
                String redirectUrl = frontendUrl + "/auth/callback#token=" + jwt;
                response.sendRedirect(redirectUrl);
        }
}