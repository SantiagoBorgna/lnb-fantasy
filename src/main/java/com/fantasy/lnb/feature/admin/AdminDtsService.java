package com.fantasy.lnb.feature.admin;

import com.fantasy.lnb.feature.admin.dto.AdminDtDto;
import com.fantasy.lnb.feature.admin.dto.AdminDtUpdateRequestDto;
import com.fantasy.lnb.feature.dt.DirectorTecnico;
import com.fantasy.lnb.feature.dt.DirectorTecnicoRepository;
import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.equipo.EquipoRealRepository;
import com.fantasy.lnb.feature.plantel.PlantelJornadaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDtsService {

    private final DirectorTecnicoRepository dtRepo;
    private final EquipoRealRepository equipoRepo;
    private final PlantelJornadaRepository plantelRepo;

    @Transactional(readOnly = true)
    public List<AdminDtDto> getAllDts() {
        Map<Long, Integer> dtCounts = plantelRepo.countDtsEnPlantelesActuales().stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Number) row[1]).intValue()
                ));

        return dtRepo.findAll().stream()
                .map(dt -> {
                    AdminDtDto dto = toDto(dt);
                    dto.setCantidadPlanteles(dtCounts.getOrDefault(dt.getId(), 0));
                    return dto;
                })
                .collect(Collectors.toList());
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
