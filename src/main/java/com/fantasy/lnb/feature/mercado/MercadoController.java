package com.fantasy.lnb.feature.mercado;

import com.fantasy.lnb.feature.mercado.dto.JugadorMercadoDto;
import com.fantasy.lnb.feature.mercado.dto.JugadorStatsResumenDto;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mercado")
@RequiredArgsConstructor
public class MercadoController {

    private final MercadoService mercadoService;

    /**
     * GET /api/mercado/jugadores
     * GET /api/mercado/jugadores?posicion=BASE
     * GET /api/mercado/jugadores?nombre=brocal
     * GET /api/mercado/jugadores?nombre=boc&posicion=BASE (Nueva búsqueda
     * combinada)
     *
     * Endpoint principal del Mercado — público según SecurityConfig.
     */
    @GetMapping("/jugadores")
    public ResponseEntity<List<JugadorMercadoDto>> listarJugadores(
            @RequestParam(required = false) PosicionJugador posicion,
            @RequestParam(required = false) String nombre) {

        // Si el usuario escribió algo en la barra de búsqueda, combinamos texto y
        // posición
        if (nombre != null && !nombre.isBlank()) {
            return ResponseEntity.ok(mercadoService.buscarPorNombre(nombre, posicion));
        }

        // Si la barra está vacía pero tocó una pastillita de posición
        if (posicion != null) {
            return ResponseEntity.ok(mercadoService.listarPorPosicion(posicion));
        }

        // Si no hay texto ni pastillita seleccionada, traemos a todos
        return ResponseEntity.ok(mercadoService.listarTodos());
    }

    /**
     * GET /api/mercado/jugadores/{id}
     *
     * Detalle de un jugador individual.
     * El frontend lo usa al abrir el Modal Flotante desde la Canchita.
     */
    @GetMapping("/jugadores/{id}")
    public ResponseEntity<JugadorMercadoDto> obtenerJugador(@PathVariable Long id) {
        return mercadoService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/mercado/jugadores/{id}/stats
     * Promedios históricos del jugador para mostrar en el modal del Mercado.
     */
    @GetMapping("/jugadores/{id}/stats")
    public ResponseEntity<JugadorStatsResumenDto> obtenerStats(
            @PathVariable Long id) {
        return ResponseEntity.ok(mercadoService.obtenerStatsResumen(id));
    }
}