package com.fantasy.lnb.feature.admin.mes;

import com.fantasy.lnb.feature.admin.mes.dto.MesDto;
import com.fantasy.lnb.feature.admin.mes.dto.MesRequestDto;
import com.fantasy.lnb.feature.admin.mes.dto.MesResumenDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/meses")
@RequiredArgsConstructor
public class AdminMesController {

    private final AdminMesService adminMesService;

    @GetMapping
    public ResponseEntity<List<MesDto>> listar() {
        return ResponseEntity.ok(adminMesService.listar());
    }

    @PostMapping
    public ResponseEntity<MesDto> crear(@RequestBody MesRequestDto request) {
        return ResponseEntity.ok(adminMesService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MesDto> actualizar(@PathVariable Long id, @RequestBody MesRequestDto request) {
        return ResponseEntity.ok(adminMesService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        adminMesService.eliminar(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/resumen")
    public ResponseEntity<MesResumenDto> resumen(@PathVariable Long id) {
        return ResponseEntity.ok(adminMesService.resumen(id));
    }
}
