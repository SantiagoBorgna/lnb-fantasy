package com.fantasy.lnb.feature.dt;

import com.fantasy.lnb.feature.dt.dto.DirectorTecnicoDto;
import com.fantasy.lnb.feature.mercado.EstadoJugador;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/dt")
@RequiredArgsConstructor
public class DirectorTecnicoController {

    private final DirectorTecnicoRepository dtRepo;

    // GET /api/dt — todos los DTs disponibles
    @GetMapping
    public ResponseEntity<List<DirectorTecnicoDto>> listarDisponibles() {
        return ResponseEntity.ok(
                dtRepo.findByEstado(EstadoJugador.DISPONIBLE)
                        .stream()
                        .map(this::toDto)
                        .toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DirectorTecnicoDto> obtener(@PathVariable Long id) {
        return dtRepo.findById(id)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    private DirectorTecnicoDto toDto(DirectorTecnico dt) {
        return DirectorTecnicoDto.builder()
                .id(dt.getId())
                .nombreCompleto(dt.getNombreCompleto())
                .nacionalidad(dt.getNacionalidad())
                .equipoId(dt.getEquipoReal().getId())
                .equipoNombre(dt.getEquipoReal().getNombre())
                .equipoSigla(dt.getEquipoReal().getSigla())
                .colorPrincipal(dt.getEquipoReal().getColorPrincipal())
                .colorSecundario(dt.getEquipoReal().getColorSecundario())
                .estado(dt.getEstado())
                .build();
    }
}