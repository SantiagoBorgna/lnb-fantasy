package com.fantasy.lnb.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value; // <-- ¡Faltaba este!
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// <-- ¡Faltaban estos 3 de Bucket4j! -->
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // Cache de buckets por IP — uno por cada IP cliente
    // ConcurrentHashMap es thread-safe para accesos concurrentes
    private final Map<String, Bucket> bucketsPorIp = new ConcurrentHashMap<>();

    // Límites configurados según el tipo de endpoint
    @Value("${rate-limit.publico.requests-por-minuto:60}")
    private int limitePublico;

    @Value("${rate-limit.auth.requests-por-minuto:15}")
    private int limiteAuth;

    @Value("${rate-limit.privado.requests-por-minuto:200}")
    private int limitePrivado;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String ip = extraerIp(request);
        String path = request.getRequestURI();

        // Todos los endpoints de la API pasan por el rate limiter
        if (!path.startsWith("/api") && !path.startsWith("/login") && !path.startsWith("/oauth2")) {
            filterChain.doFilter(request, response);
            return;
        }

        int limite;
        String tipoClave;
        if (esRutaAuth(path)) {
            limite = limiteAuth;
            tipoClave = "auth";
        } else if (esRutaPublica(path)) {
            limite = limitePublico;
            tipoClave = "pub";
        } else {
            limite = limitePrivado;
            tipoClave = "priv";
        }

        String clave = ip + ":" + tipoClave;

        Bucket bucket = bucketsPorIp.computeIfAbsent(clave, k -> crearBucket(limite));

        if (bucket.tryConsume(1)) {
            // Request dentro del límite — continuar
            filterChain.doFilter(request, response);
        } else {
            // Límite excedido
            log.warn("[RATE-LIMIT] IP {} bloqueada en ruta {}. Límite: {}/min",
                    ip, path, limite);

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                    {
                      "status": 429,
                      "error": "Demasiadas solicitudes",
                      "mensaje": "Superaste el límite de requests. Intentá de nuevo en un minuto."
                    }
                    """);
        }
    }

    /**
     * Limpia el cache de buckets cada hora.
     * Elimina buckets completamente llenos (IPs que no han hecho requests
     * recientes y ya se recargaron por completo).
     */
    @Scheduled(fixedDelay = 3_600_000)
    public void limpiarBucketsInactivos() {
        // Simplemente limpiar todo el cache cada hora.
        // Los buckets se recrean en el próximo request de cada IP.
        int cantidad = bucketsPorIp.size();
        bucketsPorIp.clear();
        log.debug("[RATE-LIMIT] Cache de buckets limpiado. {} entradas eliminadas.",
                cantidad);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Bucket crearBucket(int requestsPorMinuto) {
        Bandwidth limite = Bandwidth.classic(
                requestsPorMinuto,
                Refill.greedy(requestsPorMinuto, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limite).build();
    }

    /**
     * Extrae la IP real del cliente.
     * Considera proxies y load balancers (X-Forwarded-For).
     */
    private String extraerIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // Tomar solo la primera IP de la cadena (la del cliente original)
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Define si una ruta es considerada "pública" (lectura masiva)
     */
    private boolean esRutaPublica(String path) {
        return path.startsWith("/api/mercado/jugadores")
                || path.startsWith("/api/lideres")
                || path.startsWith("/api/jornadas")
                || path.startsWith("/api/ranking")
                || path.startsWith("/api/torneos");
    }

    private boolean esRutaAuth(String path) {
        return path.startsWith("/login")
                || path.startsWith("/oauth2")
                || path.startsWith("/api/auth");
    }
}