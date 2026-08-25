package com.fantasy.lnb.feature.admin;

import com.fantasy.lnb.feature.admin.dto.AdminJugadorDto;
import com.fantasy.lnb.feature.admin.dto.AdminJugadorUpdateRequestDto;
import com.fantasy.lnb.feature.admin.dto.EquipoRealBasicoDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/jugadores")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminJugadoresController {

    private final AdminJugadoresService adminJugadoresService;

    @GetMapping
    public ResponseEntity<List<AdminJugadorDto>> getAllJugadores() {
        return ResponseEntity.ok(adminJugadoresService.getAllJugadores());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateJugador(@PathVariable Long id, @RequestBody AdminJugadorUpdateRequestDto request) {
        adminJugadoresService.updateJugador(id, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/equipos")
    public ResponseEntity<List<EquipoRealBasicoDto>> getAllEquipos() {
        return ResponseEntity.ok(adminJugadoresService.getAllEquipos());
    }
}

