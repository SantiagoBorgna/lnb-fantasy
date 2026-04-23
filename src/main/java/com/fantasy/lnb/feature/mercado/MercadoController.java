package com.fantasy.lnb.feature.mercado;

import com.fantasy.lnb.feature.mercado.dto.JugadorMercadoDto;
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
     *
     * Endpoint principal del Mercado — público según SecurityConfig.
     * Los parámetros son opcionales y mutuamente excluyentes:
     * si vienen los dos a la vez, la posición tiene prioridad.
     */
    @GetMapping("/jugadores")
    public ResponseEntity<List<JugadorMercadoDto>> listarJugadores(
            @RequestParam(required = false) PosicionJugador posicion,
            @RequestParam(required = false) String nombre) {

        if (posicion != null) {
            return ResponseEntity.ok(mercadoService.listarPorPosicion(posicion));
        }

        if (nombre != null && !nombre.isBlank()) {
            return ResponseEntity.ok(mercadoService.buscarPorNombre(nombre));
        }

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
}