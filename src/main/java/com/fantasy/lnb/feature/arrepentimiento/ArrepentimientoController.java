package com.fantasy.lnb.feature.arrepentimiento;

import com.fantasy.lnb.feature.arrepentimiento.dto.SolicitudArrepentimientoRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ArrepentimientoController {

    private final ArrepentimientoService arrepentimientoService;

    /** POST /api/arrepentimiento — público: no hace falta iniciar sesión. */
    @PostMapping("/api/arrepentimiento")
    public ResponseEntity<Map<String, Object>> registrar(@RequestBody SolicitudArrepentimientoRequest request) {
        SolicitudArrepentimiento s = arrepentimientoService.registrar(request);
        return ResponseEntity.ok(Map.of("codigo", s.getCodigo(), "creadoEn", s.getCreadoEn()));
    }

    @GetMapping("/api/admin/arrepentimientos")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> listar() {
        return arrepentimientoService.listar().stream().map(ArrepentimientoController::aDto).toList();
    }

    @PatchMapping("/api/admin/arrepentimientos/{id}/resolver")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> resolver(@PathVariable Long id) {
        return aDto(arrepentimientoService.marcarResuelta(id));
    }

    private static Map<String, Object> aDto(SolicitudArrepentimiento s) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", s.getId());
        m.put("codigo", s.getCodigo());
        m.put("email", s.getEmail());
        m.put("fechaContratacion", s.getFechaContratacion());
        m.put("motivo", s.getMotivo());
        m.put("usuarioRegistrado", s.getUsuarioId() != null);
        m.put("creadoEn", s.getCreadoEn());
        m.put("resueltoEn", s.getResueltoEn());
        return m;
    }
}
