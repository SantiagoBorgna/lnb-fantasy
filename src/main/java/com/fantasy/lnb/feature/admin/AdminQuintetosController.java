package com.fantasy.lnb.feature.admin;

import com.fantasy.lnb.feature.admin.dto.AdminQuintetosResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/quintetos")
@RequiredArgsConstructor
public class AdminQuintetosController {

    private final AdminQuintetosService adminQuintetosService;

    @GetMapping
    public ResponseEntity<AdminQuintetosResponseDto> getQuintetos(@RequestParam Long jornadaId) {
        return ResponseEntity.ok(adminQuintetosService.getQuintetosPorJornada(jornadaId));
    }
}
