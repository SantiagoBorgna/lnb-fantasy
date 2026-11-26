package com.fantasy.lnb.feature.seleccion.dto;

import com.fantasy.lnb.feature.mercado.PosicionJugador;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Alta/edición de un jugador de selección desde el admin (equipoSigla es solo de lectura). */
public record SeleccionJugadorDto(
        Long id,
        @NotNull Long equipoId,
        String equipoSigla,
        @NotBlank String nombreCompleto,
        @NotNull @Min(0) Integer numeroCamiseta,
        @NotNull PosicionJugador posicion,
        @NotNull @DecimalMin("0.5") Double valor) {
}
