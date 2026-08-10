package com.fantasy.lnb.feature.showdown;

import com.fantasy.lnb.feature.mercado.JugadorReal;
import com.fantasy.lnb.feature.showdown.dto.ParticiparShowdownRequest;
import com.fantasy.lnb.feature.showdown.dto.ShowdownEventoDto;
import com.fantasy.lnb.feature.showdown.dto.ShowdownParticipanteDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/showdown")
@RequiredArgsConstructor
public class ShowdownController {

    private final ShowdownService showdownService;
    private final ShowdownEventoRepository eventoRepo;
    private final com.fantasy.lnb.feature.jornada.PartidoRepository partidoRepo;

    @PostMapping("/crear-test")
    public ResponseEntity<String> crearEventoTest() {
        var partido = partidoRepo.findFirstByEquipoLocal_NombreContainingIgnoreCaseAndEstadoOrderByFechaHoraAsc("Independiente", com.fantasy.lnb.feature.jornada.EstadoPartido.PROGRAMADO)
                .orElseThrow(() -> new RuntimeException("No hay partidos programados de Independiente de local en la BD"));
        
        String codigo = "test-" + System.currentTimeMillis();
        var evento = ShowdownEvento.builder()
                .partido(partido)
                .codigoInscripcion(codigo)
                .estado(EstadoShowdown.ABIERTO)
                .build();
        eventoRepo.save(evento);
        return ResponseEntity.ok(codigo);
    }

    @GetMapping("/{codigo}")
    public ResponseEntity<ShowdownEventoDto> getEvento(@PathVariable String codigo) {
        return ResponseEntity.ok(showdownService.getEvento(codigo));
    }

    @GetMapping("/{codigo}/mercado")
    public ResponseEntity<List<JugadorReal>> getMercado(@PathVariable String codigo) {
        return ResponseEntity.ok(showdownService.getMercado(codigo));
    }

    @PostMapping("/{codigo}/participar")
    public ResponseEntity<Long> participar(
            @PathVariable String codigo, 
            @Valid @RequestBody ParticiparShowdownRequest request) {
        return ResponseEntity.ok(showdownService.participar(codigo, request));
    }

    @GetMapping("/{codigo}/ranking")
    public ResponseEntity<List<ShowdownParticipanteDto>> getRanking(
            @PathVariable String codigo,
            @RequestParam String uuidDispositivo) {
        return ResponseEntity.ok(showdownService.getRanking(codigo, uuidDispositivo));
    }

    @GetMapping("/{codigo}/mi-equipo")
    public ResponseEntity<com.fantasy.lnb.feature.showdown.dto.ShowdownMiEquipoDto> getMiEquipo(
            @PathVariable String codigo,
            @RequestParam String uuidDispositivo) {
        return ResponseEntity.ok(showdownService.getMiEquipo(codigo, uuidDispositivo));
    }
}
