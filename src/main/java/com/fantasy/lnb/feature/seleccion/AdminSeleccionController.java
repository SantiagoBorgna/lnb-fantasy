package com.fantasy.lnb.feature.seleccion;

import com.fantasy.lnb.feature.seleccion.dto.AdminSeleccionEventoDto;
import com.fantasy.lnb.feature.seleccion.dto.PlanillaSeleccionDto;
import com.fantasy.lnb.feature.seleccion.dto.SeleccionEquipoDto;
import com.fantasy.lnb.feature.seleccion.dto.SeleccionEventoRequest;
import com.fantasy.lnb.feature.seleccion.dto.SeleccionJugadorDto;
import com.fantasy.lnb.feature.showdown.dto.AdminShowdownParticipanteDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/seleccion")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSeleccionController {

    private final SeleccionAdminService adminService;

    // ── Selecciones ──
    @GetMapping("/equipos")
    public List<SeleccionEquipoDto> getEquipos() {
        return adminService.getEquipos();
    }

    @PostMapping("/equipos")
    public SeleccionEquipoDto crearEquipo(@Valid @RequestBody SeleccionEquipoDto dto) {
        return adminService.guardarEquipo(null, dto);
    }

    @PutMapping("/equipos/{id}")
    public SeleccionEquipoDto editarEquipo(@PathVariable Long id, @Valid @RequestBody SeleccionEquipoDto dto) {
        return adminService.guardarEquipo(id, dto);
    }

    @DeleteMapping("/equipos/{id}")
    public ResponseEntity<Void> eliminarEquipo(@PathVariable Long id) {
        adminService.eliminarEquipo(id);
        return ResponseEntity.noContent().build();
    }

    // ── Jugadores ──
    @GetMapping("/jugadores")
    public List<SeleccionJugadorDto> getJugadores() {
        return adminService.getJugadores();
    }

    @PostMapping("/jugadores")
    public SeleccionJugadorDto crearJugador(@Valid @RequestBody SeleccionJugadorDto dto) {
        return adminService.guardarJugador(null, dto);
    }

    @PutMapping("/jugadores/{id}")
    public SeleccionJugadorDto editarJugador(@PathVariable Long id, @Valid @RequestBody SeleccionJugadorDto dto) {
        return adminService.guardarJugador(id, dto);
    }

    @DeleteMapping("/jugadores/{id}")
    public ResponseEntity<Void> eliminarJugador(@PathVariable Long id) {
        adminService.eliminarJugador(id);
        return ResponseEntity.noContent().build();
    }

    // ── Partidos (cada uno es un Relámpago) ──
    @GetMapping("/eventos")
    public List<AdminSeleccionEventoDto> getEventos() {
        return adminService.getEventos();
    }

    @PostMapping("/eventos")
    public AdminSeleccionEventoDto crearEvento(@Valid @RequestBody SeleccionEventoRequest request) {
        return adminService.crearEvento(request);
    }

    @PutMapping("/eventos/{id}")
    public AdminSeleccionEventoDto editarEvento(@PathVariable Long id, @Valid @RequestBody SeleccionEventoRequest request) {
        return adminService.editarEvento(id, request);
    }

    @PutMapping("/eventos/{id}/banner")
    public AdminSeleccionEventoDto cambiarBanner(@PathVariable Long id, @RequestParam boolean mostrar) {
        return adminService.cambiarBanner(id, mostrar);
    }

    @DeleteMapping("/eventos/{id}")
    public ResponseEntity<Void> eliminarEvento(@PathVariable Long id) {
        adminService.eliminarEvento(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/eventos/{id}/participantes")
    public List<AdminShowdownParticipanteDto> getParticipantes(@PathVariable Long id) {
        return adminService.getParticipantes(id);
    }

    @GetMapping("/eventos/{id}/planilla")
    public PlanillaSeleccionDto getPlanilla(@PathVariable Long id) {
        return adminService.getPlanilla(id);
    }

    @PutMapping("/eventos/{id}/planilla")
    public PlanillaSeleccionDto guardarPlanilla(@PathVariable Long id, @Valid @RequestBody PlanillaSeleccionDto planilla) {
        return adminService.guardarPlanilla(id, planilla);
    }
}
