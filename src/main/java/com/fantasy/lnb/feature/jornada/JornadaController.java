package com.fantasy.lnb.feature.jornada;

import com.fantasy.lnb.feature.jornada.dto.CrearJornadaRequest;
import com.fantasy.lnb.feature.jornada.dto.JornadaDto;
import com.fantasy.lnb.feature.plantel.PlantelClonadoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jornadas")
@RequiredArgsConstructor
public class JornadaController {

    private final JornadaService jornadaService;
    private final PlantelClonadoService plantelClonadoService;

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
            @Valid @RequestBody CrearJornadaRequest request) {
        return ResponseEntity.ok(jornadaService.crearJornada(request));
    }

    /**
     * POST /api/jornadas/clonar-planteles
     * Dispara el clonado masivo manualmente.
     * Idempotente — no duplica si ya fue clonado.
     * Proteger con rol ADMIN en el Módulo 6 extendido.
     */
    @PostMapping("/clonar-planteles")
    public ResponseEntity<?> clonarPlanteles() {
        int clonados = plantelClonadoService.clonarJornadaFinalizadaHaciaAbierta();

        if (clonados == -1) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "No se encontró el par de jornadas para clonar.",
                    "detalle", "Verificar que exista una jornada FINALIZADA " +
                            "y una ABIERTA_A_CAMBIOS."));
        }

        return ResponseEntity.ok(Map.of(
                "mensaje", "Clonado completado.",
                "clonados", clonados));
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