package com.fantasy.lnb.feature.showdown;

import com.fantasy.lnb.feature.jornada.dto.PartidoDto;
import com.fantasy.lnb.feature.showdown.dto.AdminShowdownDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/showdown")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminShowdownController {

    private final ShowdownService showdownService;

    @GetMapping
    public ResponseEntity<List<AdminShowdownDto>> getTodosLosShowdowns() {
        return ResponseEntity.ok(showdownService.getTodosLosShowdowns());
    }

    @GetMapping("/partidos-disponibles")
    public ResponseEntity<List<PartidoDto>> getPartidosDisponibles() {
        return ResponseEntity.ok(showdownService.getPartidosDisponiblesParaShowdown());
    }

    @PostMapping
    public ResponseEntity<AdminShowdownDto> crearShowdown(@RequestParam Long partidoId) {
        return ResponseEntity.ok(showdownService.crearShowdownManual(partidoId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarShowdown(@PathVariable Long id) {
        showdownService.eliminarShowdown(id);
        return ResponseEntity.ok().build();
    }
}
