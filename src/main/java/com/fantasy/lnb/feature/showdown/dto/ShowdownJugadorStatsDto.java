package com.fantasy.lnb.feature.showdown.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ShowdownJugadorStatsDto {
    private Long id;
    private String nombreCompleto;
    private String equipoSigla;
    private String equipoColorPrincipal;
    private String equipoColorSecundario;
    private Integer equipoModeloCamiseta;
    private Integer numeroCamiseta;
    private String posicion;
    private Boolean esCapitan;
    
    private Integer pts;
    private Integer reb;
    private Integer ast;
    private Integer stl;
    private Integer blk;
    private Integer tov;
    
    private Double valFantasy;
    private Double puntosAportados;
}
