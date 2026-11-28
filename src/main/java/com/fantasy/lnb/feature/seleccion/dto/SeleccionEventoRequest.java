package com.fantasy.lnb.feature.seleccion.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** fechaHora en hora argentina (ej. 2026-11-29T18:10). premio es opcional (vacío = sin premio). */
public record SeleccionEventoRequest(
        @NotNull Long localId,
        @NotNull Long visitanteId,
        @NotNull LocalDateTime fechaHora,
        @Size(max = 120) String premio) {
}
