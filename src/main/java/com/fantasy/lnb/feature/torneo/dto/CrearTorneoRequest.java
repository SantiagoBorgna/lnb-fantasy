package com.fantasy.lnb.feature.torneo.dto;

import com.fantasy.lnb.feature.torneo.TipoTorneo;
import jakarta.validation.constraints.*;
import lombok.Data;

// ── Request: crear un torneo ──────────────────────────────────────────────────
@Data
public class CrearTorneoRequest {

    @NotBlank(message = "El nombre del torneo es obligatorio.")
    @Size(min = 3, max = 60, message = "El nombre debe tener entre 3 y 60 caracteres.")
    @Pattern(regexp = "^[a-zA-Z0-9 ñÑáéíóúÁÉÍÓÚüÜ!¡¿?.,-]+$", message = "El nombre contiene caracteres no permitidos")
    private String nombre;

    @Size(max = 500, message = "La descripción no puede superar 500 caracteres.")
    @Pattern(regexp = "^[a-zA-Z0-9 ñÑáéíóúÁÉÍÓÚüÜ!¡¿?.,-]+$", message = "La descripción contiene caracteres no permitidos")
    private String descripcion;

    @NotNull(message = "El tipo de torneo es obligatorio.")
    private TipoTorneo tipo;
}