package com.fantasy.lnb.feature.admin.mes.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MesDto {
    private Long id;
    private String nombre;
    /** Ordenadas por número de jornada; solo las que siguen existiendo */
    private List<Long> jornadaIds;
    private List<Integer> jornadaNumeros;
}
