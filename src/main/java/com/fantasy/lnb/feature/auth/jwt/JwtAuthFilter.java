package com.fantasy.lnb.feature.auth.jwt;

import com.fantasy.lnb.feature.usuario.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final TokenRevocadoRepository tokenRevocadoRepo;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // ── DEBUG TEMPORAL ───────────────────────────────────────────────────
        log.info("[JWT-DEBUG] Header recibido: '{}'", authHeader);
        // ────────────────────────────────────────────────────────────────────

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);

        // ── DEBUG TEMPORAL ───────────────────────────────────────────────────
        log.info("[JWT-DEBUG] Token extraído (primeros 30 chars): '{}'",
                token.length() > 30 ? token.substring(0, 30) + "..." : token);
        log.info("[JWT-DEBUG] Resultado de esValido(): {}", jwtService.esValido(token));
        // ────────────────────────────────────────────────────────────────────

        if (!jwtService.esValido(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        // ── Verificar blacklist ──────────────────────────────────────────────
        String tokenHash = jwtService.hashToken(token);
        if (tokenRevocadoRepo.existsByTokenHash(tokenHash)) {
            log.warn("[JWT] Token revocado intentó acceder. Hash: {}",
                    tokenHash.substring(0, 8) + "...");
            filterChain.doFilter(request, response);
            return;
        }

        // Solo autenticamos si no hay ya una autenticación en el contexto
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String email = jwtService.extraerEmail(token);

            // Verificamos que el usuario todavía exista en nuestra BD
            usuarioRepository.findByEmail(email).ifPresent(usuario -> {

                // Construir authorities con el rol del token
                String rolStr = jwtService.extraerRol(token).name(); // "USER" o "ADMIN"
                var authority = new SimpleGrantedAuthority("ROLE_" + rolStr);

                var userDetails = User.withUsername(usuario.getEmail())
                        .password("")
                        .authorities(authority) // ← rol real en lugar de lista vacía
                        .build();

                var authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            });
        }

        filterChain.doFilter(request, response);
    }
}