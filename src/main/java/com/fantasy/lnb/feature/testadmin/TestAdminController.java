package com.fantasy.lnb.feature.testadmin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/test")
@RequiredArgsConstructor
public class TestAdminController {

    private final TestAdminService testAdminService;

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
}
