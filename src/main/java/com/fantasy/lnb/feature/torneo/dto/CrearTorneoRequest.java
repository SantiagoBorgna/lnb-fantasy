package com.fantasy.lnb.feature.torneo.dto;

import com.fantasy.lnb.feature.torneo.TipoTorneo;
import jakarta.validation.constraints.*;
import lombok.Data;

// ── Request: crear un torneo ──────────────────────────────────────────────────
@Data
public class CrearTorneoRequest {

    @NotBlank(message = "El nombre del torneo es obligatorio.")
    @Size(min = 3, max = 60, message = "El nombre debe tener entre 3 y 60 caracteres.")
    private String nombre;

    @Size(max = 500, message = "La descripción no puede superar 500 caracteres.")
    private String descripcion;

    @NotNull(message = "El tipo de torneo es obligatorio.")
    private TipoTorneo tipo;
}