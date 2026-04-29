package com.fantasy.lnb.feature.mercado.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JugadorStatsResumenDto {
    private Long jugadorRealId;
    private Double promedioPuntos;
    private Double promedioRebotesDefensivos;
    private Double promedioRebotesOfensivos;
    private Double promedioAsistencias;
    private Double promedioRobos;
    private Double promedioPerdidas;
    private Double promedioTaponesRealizados;
    private Double promedioTaponesRecibidos;
    private Double promedioFaltasCometidas;
    private Double promedioFaltasRecibidas;
    private Double promedioTirosCampoFallados;
    private Double promedioTirosLibresFallados;

    private Double promedioFantasy;
    private Integer partidosJugados;

    // Constructor para JPQL (en orden exacto de la query):
    public JugadorStatsResumenDto(Long jugadorRealId, Double promedioPuntos,
            Double promedioRebotesDefensivos, Double promedioRebotesOfensivos,
            Double promedioAsistencias, Double promedioRobos, Double promedioPerdidas,
            Double promedioTaponesRealizados, Double promedioTaponesRecibidos,
            Double promedioFaltasCometidas, Double promedioFaltasRecibidas,
            Double promedioTirosCampoFallados, Double promedioTirosLibresFallados,
            Double promedioFantasy, Integer partidosJugados) {

        this.jugadorRealId = jugadorRealId;
        this.promedioPuntos = promedioPuntos;
        this.promedioRebotesDefensivos = promedioRebotesDefensivos;
        this.promedioRebotesOfensivos = promedioRebotesOfensivos;
        this.promedioAsistencias = promedioAsistencias;
        this.promedioRobos = promedioRobos;
        this.promedioPerdidas = promedioPerdidas;
        this.promedioTaponesRealizados = promedioTaponesRealizados;
        this.promedioTaponesRecibidos = promedioTaponesRecibidos;
        this.promedioFaltasCometidas = promedioFaltasCometidas;
        this.promedioFaltasRecibidas = promedioFaltasRecibidas;
        this.promedioTirosCampoFallados = promedioTirosCampoFallados;
        this.promedioTirosLibresFallados = promedioTirosLibresFallados;
        this.promedioFantasy = promedioFantasy;
        this.partidosJugados = partidosJugados;
    }
}