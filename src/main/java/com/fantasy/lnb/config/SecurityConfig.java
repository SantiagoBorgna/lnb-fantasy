package com.fantasy.lnb.config;

import com.fantasy.lnb.feature.auth.jwt.JwtAuthFilter;
import com.fantasy.lnb.feature.auth.oauth2.OAuth2SuccessHandler;
import com.fantasy.lnb.feature.auth.oauth2.OAuth2UserService;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@EnableMethodSecurity
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

                                                // ── Rutas completamente públicas ─────────────────────────────
                                                .requestMatchers(
                                                                "/api/auth/login",
                                                                "/login/**",
                                                                "/oauth2/**",
                                                                "/api/mercado/jugadores",
                                                                "/api/mercado/jugadores/**",
                                                                "/api/lideres/**",
                                                                "/api/jornadas",
                                                                "/api/jornadas/**",
                                                                "/api/ranking/**",
                                                                "/api/torneos",
                                                                "/api/torneos/*/tabla",
                                                                "/api/dt",
                                                                "/api/dt/**",
                                                                "/api/onboarding/equipos")
                                                .permitAll()

                                                // ── Rutas exclusivas de ADMIN ─────────────────────────────────
                                                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                                                // ── Todo lo demás requiere JWT válido (cualquier rol) ─────────
                                                .anyRequest().authenticated())

                                // ── Manejo de excepciones: Devolver 401 en vez de HTML ───────────
                                .exceptionHandling(exc -> exc
                                                .authenticationEntryPoint((request, response, authException) -> {
                                                        response.setStatus(401);
                                                        response.setContentType("application/json");
                                                        response.getWriter().write(
                                                                        "{\"error\": \"Token invalido o revocado\"}");
                                                }))
                                // ── Configuración OAuth2 ─────────────────────────────────────────
                                .oauth2Login(oauth2 -> oauth2
                                                .userInfoEndpoint(endpoint -> endpoint.userService(oAuth2UserService))
                                                .successHandler(oAuth2SuccessHandler))

                                // ── JWT Filter antes del filtro estándar de username/password ────
                                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }
}