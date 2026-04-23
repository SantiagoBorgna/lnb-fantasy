package com.fantasy.lnb.config;

import com.fantasy.lnb.feature.auth.jwt.JwtAuthFilter;
import com.fantasy.lnb.feature.auth.oauth2.OAuth2SuccessHandler;
import com.fantasy.lnb.feature.auth.oauth2.OAuth2UserService;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthFilter jwtAuthFilter;
        private final OAuth2SuccessHandler oAuth2SuccessHandler;
        private final OAuth2UserService oAuth2UserService;

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                // ── Sin CSRF: API stateless con JWT no lo necesita ──────────────
                                .csrf(AbstractHttpConfigurer::disable)

                                // ── CORS: delegamos a CorsConfig.java ───────────────────────────
                                .cors(cors -> {
                                })

                                // ── Sin sesión HTTP: cada request se autentica por JWT ───────────
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                // ── Rutas públicas vs protegidas ─────────────────────────────────
                                .authorizeHttpRequests(auth -> auth
                                                // Endpoints públicos
                                                .requestMatchers(
                                                                "/api/auth/**",
                                                                "/login/**",
                                                                "/oauth2/**",
                                                                "/api/mercado/jugadores/**", // El mercado es visible
                                                                                             // sin
                                                                                             // login
                                                                "/api/lideres/**" // Rankings son públicos
                                                ).permitAll()
                                                // Todo lo demás requiere JWT válido
                                                .anyRequest().authenticated())

                                // ── Configuración OAuth2 ─────────────────────────────────────────
                                .oauth2Login(oauth2 -> oauth2
                                                .userInfoEndpoint(endpoint -> endpoint.userService(oAuth2UserService))
                                                .successHandler(oAuth2SuccessHandler))

                                // ── JWT Filter antes del filtro estándar de username/password ────
                                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }
}