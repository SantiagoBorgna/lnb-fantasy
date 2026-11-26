package com.fantasy.lnb.feature.seleccion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Planilla de un partido de la selección. El admin la manda con el resultado y las stats;
 * valoracion, fueMvp y puntajeFantasy los calcula el backend (se ignoran si vienen).
 */
public record PlanillaSeleccionDto(
        @NotNull @Min(0) Integer puntosLocal,
        @NotNull @Min(0) Integer puntosVisitante,
        @NotNull @Valid List<Fila> jugadores) {

    public record Fila(
            @NotNull Long jugadorId,
            String nombreCompleto,
            Integer numeroCamiseta,
            String equipoSigla,
            Boolean esLocal,
            Boolean titular,
            @Min(0) Integer puntos,
            @Min(0) Integer tirosCampoFallados,
            @Min(0) Integer tirosLibresFallados,
            @Min(0) Integer rebotesOfensivos,
            @Min(0) Integer rebotesDefensivos,
            @Min(0) Integer asistencias,
            @Min(0) Integer recuperaciones,
            @Min(0) Integer perdidas,
            @Min(0) Integer taponesRealizados,
            @Min(0) Integer taponesRecibidos,
            @Min(0) Integer faltasCometidas,
            @Min(0) Integer faltasRecibidas,
            Integer valoracion,
            Boolean fueMvp,
            Double puntajeFantasy) {
    }
}
