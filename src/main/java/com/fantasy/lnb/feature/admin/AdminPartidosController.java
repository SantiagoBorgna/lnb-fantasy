package com.fantasy.lnb.feature.admin;

import com.fantasy.lnb.feature.admin.dto.AdminPartidoRequest;
import com.fantasy.lnb.feature.jornada.Partido;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/partidos")
@RequiredArgsConstructor
public class AdminPartidosController {

    private final AdminPartidosService adminPartidosService;

    @GetMapping
    public ResponseEntity<List<Partido>> listarPartidos(
            @RequestParam(required = false) Long jornadaId) {
        return ResponseEntity.ok(adminPartidosService.listarPartidos(jornadaId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Partido> actualizarPartido(
            @PathVariable Long id,
            @RequestBody AdminPartidoRequest request) {
        return ResponseEntity.ok(adminPartidosService.actualizarPartido(id, request));
    }

    @PostMapping("/{id}/scrape")
    public ResponseEntity<Partido> recalcularEstadisticas(@PathVariable Long id) {
        return ResponseEntity.ok(adminPartidosService.recalcularEstadisticas(id));
    }
}
