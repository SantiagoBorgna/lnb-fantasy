package com.fantasy.lnb.feature.admin.dto;

import com.fantasy.lnb.feature.mercado.EstadoJugador;
import com.fantasy.lnb.feature.mercado.PosicionJugador;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminJugadorDto {
    private Long id;
    private String nombreCompleto;
    private PosicionJugador posicion;
    private EstadoJugador estado;
    private Double valorMercadoActual;
    private Integer numeroCamiseta;
    private Long equipoRealId;
    private String equipoSigla;
    private String gesPerfilUrl;
    private String fotoUrl;
    private Integer cantidadPlanteles;
    private Integer cantidadCapitan;
}

