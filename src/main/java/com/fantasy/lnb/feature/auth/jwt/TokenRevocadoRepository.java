package com.fantasy.lnb.feature.auth.jwt;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;

@Repository
public interface TokenRevocadoRepository
        extends JpaRepository<TokenRevocado, Long> {

    // Verificación en cada request — debe ser ultra rápida (índice en tokenHash)
    boolean existsByTokenHash(String tokenHash);

    // Revocar todos los tokens activos de un usuario (ej: cambio de cuenta)
    void deleteByEmail(String email);

    // Limpieza periódica — elimina tokens ya expirados (no hace falta guardarlos)
    @Modifying
    @Query("DELETE FROM TokenRevocado t WHERE t.expiracion < :ahora")
    void eliminarExpirados(LocalDateTime ahora);
}