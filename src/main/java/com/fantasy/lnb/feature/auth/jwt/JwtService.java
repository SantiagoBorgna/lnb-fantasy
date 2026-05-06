package com.fantasy.lnb.feature.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fantasy.lnb.feature.usuario.RolUsuario;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMs;

    // Spring inyecta los valores del application.properties
    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {

        // Keys.hmacShaKeyFor exige mínimo 256 bits — validado en arranque
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    // ── Generación ──────────────────────────────────────────────────────────

    /**
     * Genera un JWT firmado con HS256.
     * El subject es el email del usuario (único entre proveedores).
     * Incluimos el id y el nombre como claims extras para que el
     * frontend no necesite hacer un request adicional al cargar.
     */
    public String generarToken(Long usuarioId, String email,
            String nombreDisplay, RolUsuario rol) {
        Date ahora = new Date();
        Date expiracion = new Date(ahora.getTime() + expirationMs);

        return Jwts.builder()
                .subject(email)
                .claim("id", usuarioId)
                .claim("nombre", nombreDisplay)
                .claim("rol", rol.name())
                .issuedAt(ahora)
                .expiration(expiracion)
                .signWith(secretKey)
                .compact();
    }

    // Agregar método de extracción:
    public RolUsuario extraerRol(String token) {
        String rolStr = parsearClaims(token).get("rol", String.class);
        try {
            return RolUsuario.valueOf(rolStr);
        } catch (Exception e) {
            return RolUsuario.USER; // Fallback seguro
        }
    }

    // ── Validación y extracción ─────────────────────────────────────────────

    /**
     * Valida firma + expiración en un solo paso.
     * Devuelve false para cualquier excepción (token manipulado, expirado,
     * malformado).
     */
    public boolean esValido(String token) {
        try {
            parsearClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("[JWT] Token inválido: {}", e.getMessage());
            return false;
        }
    }

    public String extraerEmail(String token) {
        return parsearClaims(token).getSubject();
    }

    public Long extraerUsuarioId(String token) {
        return parsearClaims(token).get("id", Long.class);
    }

    // ── Privado ─────────────────────────────────────────────────────────────

    private Claims parsearClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Genera un hash SHA-256 del token para guardarlo en la blacklist.
     * Nunca almacenamos el token en claro.
     */
    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error al hashear token", e);
        }
    }

    /**
     * Extrae la fecha de expiración del token para guardarla en TokenRevocado.
     * Necesaria para que el job de limpieza pueda eliminar tokens vencidos.
     */
    public LocalDateTime extraerExpiracion(String token) {
        return parsearClaims(token)
                .getExpiration()
                .toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}