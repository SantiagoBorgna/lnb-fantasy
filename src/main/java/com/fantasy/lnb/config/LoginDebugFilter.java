package com.fantasy.lnb.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * TEMPORAL: traza el flujo de login (OAuth2 + /me) para diagnosticar fallos en PWA.
 * Todos los logs llevan el prefijo [LOGIN-DEBUG] para filtrarlos en Railway.
 * Nunca loguea tokens, codes ni valores de cookies/query: solo presencia y metadatos.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoginDebugFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/oauth2") || path.startsWith("/login") || path.equals("/api/auth/me"));
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        log.info("[LOGIN-DEBUG] -> {} {} | queryParams={} | ip={} | jsessionidCookie={} | sesionExistente={} | authHeader={} | origin={} | referer={} | secFetch={}/{} | ua={}",
                request.getMethod(),
                path,
                request.getQueryString() == null ? "ninguno" : nombresParams(request),
                primeraIp(request),
                tieneCookie(request, "JSESSIONID"),
                request.getSession(false) != null,
                request.getHeader("Authorization") != null,
                request.getHeader("Origin"),
                request.getHeader("Referer"),
                request.getHeader("Sec-Fetch-Site"),
                request.getHeader("Sec-Fetch-Dest"),
                request.getHeader("User-Agent"));

        try {
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            log.error("[LOGIN-DEBUG] !! EXCEPCION en {} {} : {} - {}",
                    request.getMethod(), path, e.getClass().getSimpleName(), e.getMessage());
            throw e;
        } finally {
            log.info("[LOGIN-DEBUG] <- {} {} | status={} | redirigeA={} | setCookie={}",
                    request.getMethod(),
                    path,
                    response.getStatus(),
                    destinoSinSecretos(response.getHeader("Location")),
                    response.getHeader("Set-Cookie") != null);
        }
    }

    private String nombresParams(HttpServletRequest request) {
        return String.join(",", request.getParameterMap().keySet());
    }

    private boolean tieneCookie(HttpServletRequest request, String nombre) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return false;
        for (Cookie c : cookies) {
            if (nombre.equals(c.getName())) return true;
        }
        return false;
    }

    private String primeraIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    /** Deja solo esquema+host+path: corta query (state, code) y fragment (#token=JWT). */
    private String destinoSinSecretos(String location) {
        if (location == null) return "-";
        int corte = location.length();
        int q = location.indexOf('?');
        int h = location.indexOf('#');
        if (q >= 0) corte = Math.min(corte, q);
        if (h >= 0) corte = Math.min(corte, h);
        String limpio = location.substring(0, corte);
        boolean teniaFragment = h >= 0;
        boolean teniaQuery = q >= 0;
        return limpio + (teniaQuery ? " [+query]" : "") + (teniaFragment ? " [+fragment]" : "");
    }
}
