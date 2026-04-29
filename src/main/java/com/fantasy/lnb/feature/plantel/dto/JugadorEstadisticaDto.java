package com.fantasy.lnb.feature.plantel.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JugadorEstadisticaDto {
    private Long jugadorRealId;
    private Double puntajeFantasy; // null si no jugó todavía
    private Integer puntos;
    private Integer rebotesDefensivos;
    private Integer rebotesOfensivos;
    private Integer asistencias;
    private Integer recuperaciones;
    private Integer perdidas;
    private Integer faltasCometidas;
    private Integer faltasRecibidas;
    private Boolean fueTitular;
    private Boolean gano;
    private Integer taponesRealizados;
    private Integer taponesRecibidos;
    private Integer tirosDeCampoFallados;
    private Integer tirosLibresFallados;

    private Boolean jugó; // false → mostrar "--"
}