package com.fantasy.lnb;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.Random;

@Component
public class MockDataLoader implements CommandLineRunner {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        jdbcTemplate.update("UPDATE partido SET puntos_local = 85, puntos_visitante = 80 WHERE id = 28");
        
        String gesHash = jdbcTemplate.queryForObject("SELECT ges_hash FROM partido WHERE id = 28", String.class);
        Long jornadaId = jdbcTemplate.queryForObject("SELECT jornada_id FROM partido WHERE id = 28", Long.class);
        
        List<Long> jugadoresId = jdbcTemplate.queryForList("SELECT id FROM jugador_real WHERE equipo_lnb_id IN (1, 6)", Long.class);
        Random random = new Random();
        
        for (Long id : jugadoresId) {
            int exists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM estadistica_partido WHERE ges_partido_id = ? AND jugador_real_id = ?", Integer.class, gesHash, id);
            if (exists == 0) {
                int pts = random.nextInt(25) + 2;
                int rebDef = random.nextInt(7);
                int rebOf = random.nextInt(3);
                int ast = random.nextInt(8);
                int stl = random.nextInt(3);
                int blk = random.nextInt(2);
                int tov = random.nextInt(4);
                
                double valFantasy = pts + rebDef + rebOf + ast + stl + blk - tov;
                
                jdbcTemplate.update(
                    "INSERT INTO estadistica_partido " +
                    "(jugador_real_id, jornada_id, ges_partido_id, fecha_partido, fue_local, puntos, rebotes_defensivos, rebotes_ofensivos, asistencias, recuperaciones, perdidas, faltas_cometidas, faltas_recibidas, tiros_campo_fallados, tiros_libres_fallados, tapones_realizados, tapones_recibidos, fue_expulsado_por_faltas, tiene_falta_tecnica, fue_descalificado, fue_titular_en_partido_real, equipo_gano, puntaje_fantasy_calculado, creado_en) " +
                    "VALUES (?, ?, ?, NOW(), false, ?, ?, ?, ?, ?, ?, 0, 0, 0, 0, ?, 0, false, false, false, true, true, ?, NOW())",
                    id, jornadaId, gesHash, pts, rebDef, rebOf, ast, stl, tov, blk, valFantasy
                );
            }
        }
        System.out.println("Mock data for partido 28 loaded with random stats into estadistica_partido.");
    }
}
