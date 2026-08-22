package com.fantasy.lnb.feature.admin.dto;

import com.fantasy.lnb.feature.mercado.PosicionJugador;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JugadorQuintetoDto {
    private Long id;
    private String nombre;
    private String apellido;
    private String clubReal;
    private PosicionJugador posicion;
    private Double puntosFantasy;
    private Boolean esCapitan;
}
