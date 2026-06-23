package com.fantasy.lnb.feature.torneo.dto;

import com.fantasy.lnb.feature.torneo.TipoTorneo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class EditarTorneoRequest {

    @NotBlank(message = "El nombre es obligatorio.")
    @Pattern(regexp = "^[a-zA-Z0-9 ñÑáéíóúÁÉÍÓÚüÜ!¡¿?.,-]+$", message = "El nombre contiene caracteres no permitidos")
    private String nombre;

    @Pattern(regexp = "^[a-zA-Z0-9 ñÑáéíóúÁÉÍÓÚüÜ!¡¿?.,-]+$", message = "La descripción contiene caracteres no permitidos")
    private String descripcion;

    @NotNull(message = "El tipo de torneo es obligatorio.")
    private TipoTorneo tipo;
}