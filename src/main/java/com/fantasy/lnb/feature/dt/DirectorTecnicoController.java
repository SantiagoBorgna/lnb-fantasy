package com.fantasy.lnb.feature.dt;

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
    public ResponseEntity<List<DirectorTecnico>> listarDisponibles() {
        return ResponseEntity.ok(
                dtRepo.findByEstado(EstadoJugador.DISPONIBLE));
    }

    // GET /api/dt/{id} — detalle de un DT
    @GetMapping("/{id}")
    public ResponseEntity<DirectorTecnico> obtener(@PathVariable Long id) {
        return dtRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}