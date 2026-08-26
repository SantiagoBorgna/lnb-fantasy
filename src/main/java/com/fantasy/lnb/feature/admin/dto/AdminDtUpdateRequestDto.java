package com.fantasy.lnb.feature.admin.dto;

import com.fantasy.lnb.feature.mercado.EstadoJugador;
import lombok.Data;

@Data
public class AdminDtUpdateRequestDto {
    private String nombreCompleto;
    private Long equipoId;
    private EstadoJugador estado;
    private Double promedioFantasy;
}
