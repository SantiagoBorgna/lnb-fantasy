package com.fantasy.lnb.feature.lideres;

import com.fantasy.lnb.feature.lideres.dto.CategoriaLideresDto;
import com.fantasy.lnb.feature.lideres.dto.LiderDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lideres")
@RequiredArgsConstructor
public class LideresController {

    private final LideresService lideresService;

    /**
     * GET /api/lideres
     * Resumen de todas las categorías con el líder de cada una.
     * Renderiza las tarjetas de la pantalla principal de Líderes.
     * Público — ya estaba en el permitAll() del SecurityConfig.
     */
    @GetMapping
    public ResponseEntity<List<CategoriaLideresDto>> resumen() {
        return ResponseEntity.ok(lideresService.obtenerResumenLideres());
    }

    /**
     * GET /api/lideres/{categoria}
     * Top 5 de una categoría específica.
     * Se llama cuando el usuario toca una tarjeta para expandirla.
     * Categorías válidas: puntos, rebotes, asistencias, robos, tapones, fantasy
     */
    @GetMapping("/{categoria}")
    public ResponseEntity<List<LiderDto>> topPorCategoria(
            @PathVariable String categoria) {
        return ResponseEntity.ok(
                lideresService.obtenerTopPorCategoria(categoria));
    }
}