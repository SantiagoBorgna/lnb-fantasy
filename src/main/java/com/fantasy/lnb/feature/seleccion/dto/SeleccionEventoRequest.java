package com.fantasy.lnb.feature.seleccion.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** fechaHora en hora argentina (ej. 2026-11-29T18:10) */
public record SeleccionEventoRequest(
        @NotNull Long localId,
        @NotNull Long visitanteId,
        @NotNull LocalDateTime fechaHora) {
}
