package com.fantasy.lnb.feature.admin;

import com.fantasy.lnb.feature.admin.dto.AdminJornadaRequest;
import com.fantasy.lnb.feature.jornada.Jornada;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/jornadas")
@RequiredArgsConstructor
public class AdminJornadasController {

    private final AdminJornadasService adminJornadasService;

    @GetMapping
    public ResponseEntity<List<Jornada>> listarJornadas() {
        return ResponseEntity.ok(adminJornadasService.listarJornadas());
    }

    @PostMapping
    public ResponseEntity<Jornada> crearJornada(@RequestBody AdminJornadaRequest request) {
        return ResponseEntity.ok(adminJornadasService.crearJornada(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Jornada> actualizarJornada(@PathVariable Long id, @RequestBody AdminJornadaRequest request) {
        return ResponseEntity.ok(adminJornadasService.actualizarJornada(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarJornada(@PathVariable Long id) {
        adminJornadasService.eliminarJornada(id);
        return ResponseEntity.ok().build();
    }
}
