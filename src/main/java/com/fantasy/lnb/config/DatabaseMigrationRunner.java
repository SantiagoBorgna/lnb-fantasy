package com.fantasy.lnb.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseMigrationRunner implements CommandLineRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        try {
            jdbcTemplate.execute("ALTER TABLE transaccion_draft MODIFY COLUMN tipo VARCHAR(255) NOT NULL");
            System.out.println("=========================================================");
            System.out.println("SUCCESSFULLY MIGRATED transaccion_draft.tipo TO VARCHAR");
            System.out.println("=========================================================");
        } catch (Exception e) {
            System.err.println("Failed to alter table: " + e.getMessage());
        }
    }
}
