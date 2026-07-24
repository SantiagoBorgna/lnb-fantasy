package com.fantasy.lnb.feature.draft.dto;

import com.fantasy.lnb.feature.torneo.EstadoDraft;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class DraftStateDto {
    private EstadoDraft estado;
    private List<DraftTurnoDto> turnos;
    private Long turnoActualId; // null si el draft no empezó o ya terminó
    private Integer cantidadParticipantes;
    private Integer maxParticipantes;
    private Long adminId;
}
