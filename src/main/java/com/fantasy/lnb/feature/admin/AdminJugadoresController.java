package com.fantasy.lnb.feature.admin;

import com.fantasy.lnb.feature.admin.dto.AdminJugadorDto;
import com.fantasy.lnb.feature.admin.dto.AdminJugadorUpdateRequestDto;
import com.fantasy.lnb.feature.admin.dto.EquipoRealBasicoDto;
import com.fantasy.lnb.feature.admin.dto.FusionarJugadoresRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    /**
     * POST /api/admin/jugadores/fusionar
     * Fusión de emergencia para cuando el crawler no logra matchear un jugador
     * cargado a mano y termina creando uno duplicado. Conserva idPrincipal (con
     * su historial de planteles) y le copia los datos de GES de idDuplicado,
     * que se borra.
     */
    @PostMapping("/fusionar")
    public ResponseEntity<Map<String, Object>> fusionarJugadores(@RequestBody FusionarJugadoresRequestDto request) {
        return ResponseEntity.ok(
                adminJugadoresService.fusionarJugadores(request.getIdPrincipal(), request.getIdDuplicado()));
    }
}

