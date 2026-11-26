package com.fantasy.lnb.feature.seleccion;

import com.fantasy.lnb.feature.seleccion.dto.SeleccionMercadoJugadorDto;
import com.fantasy.lnb.feature.showdown.dto.ParticiparShowdownRequest;
import com.fantasy.lnb.feature.showdown.dto.ShowdownEventoDto;
import com.fantasy.lnb.feature.showdown.dto.ShowdownMiEquipoDto;
import com.fantasy.lnb.feature.showdown.dto.ShowdownParticipanteDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Relámpago Selección (público, sin login): mismas rutas que /api/showdown. */
@RestController
@RequestMapping("/api/seleccion")
@RequiredArgsConstructor
public class SeleccionController {

    private final SeleccionService seleccionService;

    /** Evento con la inscripción abierta para el banner del juego (204 si no hay). */
    @GetMapping("/activo")
    public ResponseEntity<ShowdownEventoDto> getActivo() {
        return seleccionService.getEventoActivo()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/{codigo}")
    public ResponseEntity<ShowdownEventoDto> getEvento(@PathVariable String codigo) {
        return ResponseEntity.ok(seleccionService.getEvento(codigo));
    }

    @GetMapping("/{codigo}/mercado")
    public ResponseEntity<List<SeleccionMercadoJugadorDto>> getMercado(@PathVariable String codigo) {
        return ResponseEntity.ok(seleccionService.getMercado(codigo));
    }

    @PostMapping("/{codigo}/participar")
    public ResponseEntity<Long> participar(@PathVariable String codigo,
                                           @Valid @RequestBody ParticiparShowdownRequest request) {
        return ResponseEntity.ok(seleccionService.participar(codigo, request));
    }

    /** Paso 1 del formulario: 409 si la inscripción cerró o el email ya participa. */
    @GetMapping("/{codigo}/verificar-inscripcion")
    public ResponseEntity<Void> verificarInscripcion(@PathVariable String codigo,
                                                     @RequestParam String email,
                                                     @RequestParam String uuidDispositivo) {
        seleccionService.verificarInscripcion(codigo, email, uuidDispositivo);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{codigo}/ranking")
    public ResponseEntity<List<ShowdownParticipanteDto>> getRanking(@PathVariable String codigo,
                                                                    @RequestParam String uuidDispositivo) {
        return ResponseEntity.ok(seleccionService.getRanking(codigo, uuidDispositivo));
    }

    @GetMapping("/{codigo}/mi-equipo")
    public ResponseEntity<ShowdownMiEquipoDto> getMiEquipo(@PathVariable String codigo,
                                                           @RequestParam String uuidDispositivo) {
        return ResponseEntity.ok(seleccionService.getMiEquipo(codigo, uuidDispositivo));
    }
}
