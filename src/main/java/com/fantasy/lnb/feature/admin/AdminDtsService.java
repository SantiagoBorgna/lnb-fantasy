package com.fantasy.lnb.feature.admin;

import com.fantasy.lnb.feature.admin.dto.AdminDtDto;
import com.fantasy.lnb.feature.admin.dto.AdminDtUpdateRequestDto;
import com.fantasy.lnb.feature.dt.DirectorTecnico;
import com.fantasy.lnb.feature.dt.DirectorTecnicoRepository;
import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.equipo.EquipoRealRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDtsService {

    private final DirectorTecnicoRepository dtRepo;
    private final EquipoRealRepository equipoRepo;

    @Transactional(readOnly = true)
    public List<AdminDtDto> getAllDts() {
        return dtRepo.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void updateDt(Long id, AdminDtUpdateRequestDto request) {
        DirectorTecnico dt = dtRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("DT no encontrado con id " + id));

        // Actualizar nombre
        if (request.getNombreCompleto() != null && !request.getNombreCompleto().isBlank()) {
            dt.setNombreCompleto(request.getNombreCompleto().trim());
        }

        // Actualizar equipo
        if (request.getEquipoId() != null && !request.getEquipoId().equals(dt.getEquipoReal().getId())) {
            EquipoReal nuevoEquipo = equipoRepo.findById(request.getEquipoId())
                    .orElseThrow(() -> new IllegalArgumentException("Equipo no encontrado"));
            dt.setEquipoReal(nuevoEquipo);
        }

        // Actualizar estado y puntaje
        if (request.getEstado() != null) {
            dt.setEstado(request.getEstado());
        }

        if (request.getPromedioFantasy() != null) {
            dt.setPromedioFantasy(request.getPromedioFantasy());
        }

        dtRepo.save(dt);
        log.info("[ADMIN] DT {} (ID: {}) actualizado exitosamente", dt.getNombreCompleto(), dt.getId());
    }

    private AdminDtDto toDto(DirectorTecnico dt) {
        return AdminDtDto.builder()
                .id(dt.getId())
                .nombreCompleto(dt.getNombreCompleto())
                .equipoId(dt.getEquipoReal().getId())
                .equipoNombre(dt.getEquipoReal().getNombre())
                .equipoSigla(dt.getEquipoReal().getSigla())
                .colorPrincipal(dt.getEquipoReal().getColorPrincipal())
                .colorSecundario(dt.getEquipoReal().getColorSecundario())
                .estado(dt.getEstado())
                .promedioFantasy(dt.getPromedioFantasy() != null ? dt.getPromedioFantasy() : 0.0)
                .build();
    }
}
