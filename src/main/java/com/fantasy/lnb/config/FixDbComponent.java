package com.fantasy.lnb.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FixDbComponent {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void fixDbConstraints() {
        try {
            log.info("[FIX-DB] Alterando tabla transaccion_draft para permitir NULL en jugadores/DTs...");
            jdbcTemplate.execute("ALTER TABLE transaccion_draft MODIFY jugador_entra_id BIGINT NULL");
            jdbcTemplate.execute("ALTER TABLE transaccion_draft MODIFY jugador_sale_id BIGINT NULL");
            jdbcTemplate.execute("ALTER TABLE transaccion_draft MODIFY dt_entra_id BIGINT NULL");
            jdbcTemplate.execute("ALTER TABLE transaccion_draft MODIFY dt_sale_id BIGINT NULL");
            log.info("[FIX-DB] Tablas alteradas con éxito.");
        } catch (Exception e) {
            log.warn("[FIX-DB] No se pudo alterar la tabla, quizás ya estaba bien o no existe: {}", e.getMessage());
        }
    }
}
