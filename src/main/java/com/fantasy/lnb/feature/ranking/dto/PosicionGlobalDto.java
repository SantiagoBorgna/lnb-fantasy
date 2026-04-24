package com.fantasy.lnb.feature.ranking.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PosicionGlobalDto {
    private Integer posicion;
    private String nombreEquipo;
    private String nombreUsuario;
    private String equipoFavoritoSigla; // Para mostrar la camiseta en el ranking
    private String equipoFavoritoColor; // Color principal del equipo favorito
    private Double puntajeGlobal;
    private Long equipoVirtualId;
}