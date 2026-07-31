package com.fantasy.lnb.feature.jornada;

import com.fantasy.lnb.feature.jornada.dto.CrearJornadaRequest;
import com.fantasy.lnb.feature.jornada.dto.JornadaDto;
import com.fantasy.lnb.feature.jornada.dto.PartidoDto;
import com.fantasy.lnb.feature.plantel.PlantelClonadoService;

import com.fantasy.lnb.feature.testadmin.TestAdminService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fantasy.lnb.feature.torneo.TorneoH2HService;

@RestController
@RequestMapping("/api/jornadas")
@RequiredArgsConstructor
public class JornadaController {

    private final JornadaService jornadaService;
    private final PlantelClonadoService plantelClonadoService;
    private final JornadaRepository jornadaRepo;
    private final TestAdminService testAdminService;
    private final TorneoH2HService torneoH2HService;

    // GET /api/jornadas/fix-h2h -> Endpoint temporal para arreglar stats
    @GetMapping("/fix-h2h")
    public ResponseEntity<?> fixH2HStats() {
        torneoH2HService.recalcularTodoH2H();
        return ResponseEntity.ok(Map.of("message", "Torneos H2H recalculados exitosamente. Ya puedes ver los puntos reales."));
    }

    // GET /api/jornadas/seed-public -> Permite inicializar fixture sin login
    @GetMapping("/seed-public")
    public ResponseEntity<?> seedPublic() {
        if (jornadaRepo.count() == 0) {
            testAdminService.seedJornadas();
            return ResponseEntity.ok(Map.of("message", "Fixture generado correctamente"));
        }
        return ResponseEntity.ok(Map.of("message", "Ya existen jornadas generadas"));
    }

    // GET /api/jornadas/seed-partidos-public -> Agrega partidos a jornadas existentes
    @GetMapping("/seed-partidos-public")
    public ResponseEntity<?> seedPartidosPublic() {
        testAdminService.seedPartidosParaJornadasExistentes();
        return ResponseEntity.ok(Map.of("message", "Partidos agregados a las jornadas existentes"));
    }

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

    @GetMapping("/{id}/partidos")
    public ResponseEntity<List<PartidoDto>> obtenerPartidosDeJornada(@PathVariable Long id) {
        return ResponseEntity.ok(jornadaService.obtenerPartidosDeJornada(id));
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

        // 1. Buscamos la última jornada que haya finalizado
        Optional<Jornada> ultimaFinalizada = jornadaRepo.findFirstByEstadoOrderByNumeroDesc(EstadoJornada.FINALIZADA);

        if (ultimaFinalizada.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "No se encontró una jornada finalizada.",
                    "detalle",
                    "Debe existir al menos una jornada en estado FINALIZADA para poder clonar sus equipos hacia la próxima."));
        }

        // 2. Ejecutamos el clonado usando la nueva lógica segura
        int clonados = plantelClonadoService.clonarDesdeJornadaFinalizada(ultimaFinalizada.get());

        if (clonados == -1) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "No se encontró el par de jornadas para clonar.",
                    "detalle", "Asegurate de tener creada una jornada en estado ABIERTA_A_CAMBIOS posterior a la J"
                            + ultimaFinalizada.get().getNumero()));
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