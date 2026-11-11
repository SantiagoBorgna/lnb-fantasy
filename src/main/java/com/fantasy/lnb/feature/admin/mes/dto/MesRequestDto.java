package com.fantasy.lnb.feature.admin.mes.dto;

import lombok.Data;

import java.util.List;

@Data
public class MesRequestDto {
    private String nombre;
    private List<Long> jornadaIds;
}
