package com.fantasy.lnb.feature.jornada;

import com.fantasy.lnb.feature.jornada.dto.CrearJornadaRequest;
import com.fantasy.lnb.feature.jornada.dto.JornadaDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jornadas")
@RequiredArgsConstructor
public class JornadaController {

    private final JornadaService jornadaService;

    // GET /api/jornadas — lista todas (historial completo)
    @GetMapping
    public ResponseEntity<List<JornadaDto>> listarTodas() {
        return ResponseEntity.ok(jornadaService.listarTodas());
    }

    // GET /api/jornadas/activa — la que está EN_JUEGO ahora mismo
    @GetMapping("/activa")
    public ResponseEntity<JornadaDto> obtenerActiva() {
        return jornadaService.obtenerEnJuego()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    // GET /api/jornadas/proxima — la siguiente ABIERTA (para el countdown)
    @GetMapping("/proxima")
    public ResponseEntity<JornadaDto> obtenerProxima() {
        return jornadaService.obtenerProximaAbierta()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    // POST /api/jornadas — crear nueva jornada (admin)
    @PostMapping
    public ResponseEntity<JornadaDto> crearJornada(
            @RequestBody CrearJornadaRequest request) {
        return ResponseEntity.ok(jornadaService.crearJornada(request));
    }

    /**
     * PATCH /api/jornadas/{id}/iniciar
     * PATCH /api/jornadas/{id}/finalizar
     *
     * Endpoints de administración para forzar transiciones manualmente.
     * En el Módulo 6 se protegerán con rol ADMIN.
     * Por ahora requieren JWT válido (anyRequest().authenticated()).
     */
    @PatchMapping("/{id}/iniciar")
    public ResponseEntity<JornadaDto> iniciar(@PathVariable Long id) {
        jornadaService.iniciarJornada(id);
        return jornadaService.obtenerEnJuego()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/finalizar")
    public ResponseEntity<Void> finalizar(@PathVariable Long id) {
        jornadaService.finalizarJornada(id);
        return ResponseEntity.noContent().build();
    }
}