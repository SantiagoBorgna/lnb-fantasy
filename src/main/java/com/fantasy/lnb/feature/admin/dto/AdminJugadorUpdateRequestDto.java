package com.fantasy.lnb.feature.admin.dto;

import com.fantasy.lnb.feature.mercado.EstadoJugador;
import com.fantasy.lnb.feature.mercado.PosicionJugador;
import lombok.Data;

@Data
public class AdminJugadorUpdateRequestDto {
    private PosicionJugador posicion;
    private EstadoJugador estado;
    private Long equipoRealId;
    private Double valorMercadoActual;
}

