package com.fantasy.lnb.feature.auth;

import com.fantasy.lnb.feature.auth.jwt.JwtService;
import com.fantasy.lnb.feature.auth.jwt.TokenRevocado;
import com.fantasy.lnb.feature.auth.jwt.TokenRevocadoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogoutService {

    private final TokenRevocadoRepository tokenRevocadoRepo;
    private final JwtService jwtService;

    /**
     * Revoca el token actual del usuario.
     * Se llama al hacer logout explícito O al hacer nuevo login
     * (para invalidar la sesión anterior).
     */
    @Transactional
    public void revocarToken(String token) {
        if (token == null || token.isBlank())
            return;

        String tokenHash = jwtService.hashToken(token);

        // Idempotente: si ya está revocado no hacer nada
        if (tokenRevocadoRepo.existsByTokenHash(tokenHash)) {
            log.debug("[LOGOUT] Token ya estaba revocado.");
            return;
        }

        String email = jwtService.extraerEmail(token);
        LocalDateTime expiracion = jwtService.extraerExpiracion(token);

        TokenRevocado revocado = TokenRevocado.builder()
                .tokenHash(tokenHash)
                .email(email)
                .expiracion(expiracion)
                .build();

        tokenRevocadoRepo.save(revocado);
        log.info("[LOGOUT] Token revocado para: {}", email);
    }

    /**
     * Revoca TODOS los tokens activos de un usuario.
     * Útil si el usuario reporta actividad sospechosa.
     * (endpoint admin — implementar en Módulo de administración futuro)
     */
    @Transactional
    public void revocarTodosLosTokens(String email) {
        tokenRevocadoRepo.deleteByEmail(email);
        log.info("[LOGOUT] Todos los tokens revocados para: {}", email);
    }

    /**
     * Job de limpieza: elimina tokens expirados de la blacklist.
     * Corre todos los días a las 3 AM.
     * Un token expirado ya no puede usarse aunque no esté en la blacklist,
     * así que no hay riesgo en eliminarlo.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void limpiarTokensExpirados() {
        tokenRevocadoRepo.eliminarExpirados(LocalDateTime.now());
        log.info("[LOGOUT] Limpieza de tokens expirados completada.");
    }
}