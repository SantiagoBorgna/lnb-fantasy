package com.fantasy.lnb.feature.auth.jwt;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Almacena tokens JWT que fueron explícitamente invalidados.
 * Se consulta en cada request para verificar que el token no fue revocado.
 *
 * La tabla crece con el tiempo — el job de limpieza (ver paso 5)
 * elimina tokens expirados para mantenerla liviana.
 */
@Entity
@Table(name = "token_revocado", indexes = @Index(name = "idx_token_hash", columnList = "tokenHash", unique = true))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenRevocado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Guardamos el hash SHA-256 del token, no el token en claro
    // Evita exponer tokens en la BD si hay una brecha de seguridad
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    // Email del usuario — para revocar todos los tokens de un usuario
    @Column(nullable = false)
    private String email;

    // Cuándo expira el JWT original — para poder limpiarlo después
    @Column(nullable = false)
    private LocalDateTime expiracion;

    @Column(nullable = false, updatable = false)
    private LocalDateTime revocadoEn;

    @PrePersist
    protected void onCreate() {
        this.revocadoEn = LocalDateTime.now();
    }
}