package com.fantasy.lnb.feature.jornada.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;

// ── Request de creación (solo admin) ─────────────────────────────────────────
@Data
public class CrearJornadaRequest {

    @NotNull(message = "El número de jornada es obligatorio.")
    @Min(value = 1, message = "El número de jornada debe ser mayor a 0.")
    private Integer numero;

    @NotNull(message = "La fecha de inicio es obligatoria.")
    private LocalDateTime fechaInicio;

    @NotNull(message = "La fecha de fin es obligatoria.")
    private LocalDateTime fechaFin;
}