package com.fantasy.lnb.feature.admin;

import com.fantasy.lnb.feature.admin.dto.AdminDtDto;
import com.fantasy.lnb.feature.admin.dto.AdminDtUpdateRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/dts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDtsController {

    private final AdminDtsService adminDtsService;

    @GetMapping
    public ResponseEntity<List<AdminDtDto>> getAllDts() {
        return ResponseEntity.ok(adminDtsService.getAllDts());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateDt(@PathVariable Long id, @RequestBody AdminDtUpdateRequestDto request) {
        adminDtsService.updateDt(id, request);
        return ResponseEntity.ok().build();
    }
}
