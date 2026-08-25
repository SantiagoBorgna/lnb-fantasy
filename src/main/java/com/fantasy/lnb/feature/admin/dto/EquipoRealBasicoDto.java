package com.fantasy.lnb.feature.admin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EquipoRealBasicoDto {
    private Long id;
    private String nombre;
    private String sigla;
}

