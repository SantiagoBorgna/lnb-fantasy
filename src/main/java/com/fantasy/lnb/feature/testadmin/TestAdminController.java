package com.fantasy.lnb.feature.testadmin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import com.fantasy.lnb.feature.torneo.TorneoH2HService;

@RestController
@RequestMapping("/api/admin/test")
@RequiredArgsConstructor
public class TestAdminController {

    private final TestAdminService testAdminService;
    private final TorneoH2HService torneoH2HService;

    @PostMapping("/reset-db")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> resetDb() {
        testAdminService.resetDb();
        return ResponseEntity.ok(Map.of("message", "Base de datos transaccional limpia"));
    }

    @PostMapping("/seed-jornadas")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> seedJornadas() {
        testAdminService.seedJornadas();
        return ResponseEntity.ok(Map.of("message", "3 Jornadas ficticias generadas con partidos"));
    }

    @PostMapping("/simular-jornada/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> simularJornada(@PathVariable Long id) {
        testAdminService.simularJornada(id);
        return ResponseEntity.ok(Map.of("message", "Jornada " + id + " simulada correctamente"));
    }

    @PostMapping("/recalcular-h2h")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> recalcularH2H() {
        torneoH2HService.recalcularTodoH2H();
        return ResponseEntity.ok(Map.of("message", "Torneos H2H recalculados correctamente con las jornadas finalizadas."));
    }
}
