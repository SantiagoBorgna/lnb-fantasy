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
     * GET /api/mercado/jugadores?posicion=BASE&orden=precio_asc
     * GET /api/mercado/jugadores?nombre=brocal&orden=promedio_desc
     *
     * Endpoint principal del Mercado — público según SecurityConfig.
     */
    @GetMapping("/jugadores")
    public ResponseEntity<List<JugadorMercadoDto>> listarJugadores(
            @RequestParam(required = false) PosicionJugador posicion,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String orden) { // <-- ¡Agregamos orden!

        // Si el usuario escribió algo en la barra de búsqueda, combinamos texto y
        // posición
        if (nombre != null && !nombre.isBlank()) {
            return ResponseEntity.ok(mercadoService.buscarPorNombre(nombre, posicion, orden));
        }

        // Si la barra está vacía pero tocó una pastillita de posición
        if (posicion != null) {
            return ResponseEntity.ok(mercadoService.listarPorPosicion(posicion, orden));
        }

        // Si no hay texto ni pastillita seleccionada, traemos a todos
        return ResponseEntity.ok(mercadoService.listarTodos(orden));
    }

    /**
     * GET /api/mercado/libres/{torneoId}
     * Devuelve los jugadores que NO pertenecen a ningún equipo de este Torneo Draft.
     */
    @GetMapping("/libres/{torneoId}")
    public ResponseEntity<List<JugadorMercadoDto>> listarLibres(
            @PathVariable Long torneoId,
            @RequestParam(required = false) PosicionJugador posicion,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String orden) {

        return ResponseEntity.ok(mercadoService.listarLibresTorneo(torneoId, posicion, nombre, orden));
    }

    @GetMapping("/jugadores/{id}")
    public ResponseEntity<JugadorMercadoDto> obtenerJugador(@PathVariable Long id) {
        return mercadoService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/jugadores/{id}/stats")
    public ResponseEntity<JugadorStatsResumenDto> obtenerStats(
            @PathVariable Long id) {
        return ResponseEntity.ok(mercadoService.obtenerStatsResumen(id));
    }

    /**
     * ENDPOINT TEMPORAL PARA SINCRONIZAR PROMEDIOS
     * Borrar o comentar después de usar.
     */
    @GetMapping("/forzar-actualizacion")
    public ResponseEntity<String> forzarActualizacion() {
        mercadoService.actualizarPreciosTodos();
        return ResponseEntity.ok("¡Todos los promedios y precios fueron recalculados y guardados en la base de datos!");
    }
}