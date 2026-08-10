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
    
    private Boolean fueTitular;
    private Boolean gano;
    private Integer puntos;
    private Integer asistencias;
    private Integer rebotesDefensivos;
    private Integer rebotesOfensivos;
    private Integer recuperaciones;
    private Integer taponesRealizados;
    private Integer faltasRecibidas;
    private Integer perdidas;
    private Integer taponesRecibidos;
    private Integer faltasCometidas;
    private Integer tirosDeCampoFallados;
    private Integer tirosLibresFallados;
    
    private Double valFantasy;
    private Double puntosAportados;
}
