package com.fantasy.lnb.feature.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ActualizarPerfilRequest {
    @NotBlank(message = "El nombre del mánager no puede estar vacío")
    @Pattern(regexp = "^[a-zA-Z0-9 ñÑáéíóúÁÉÍÓÚüÜ-]+$", message = "El nombre contiene caracteres no permitidos")
    private String nombreDisplay;

    @NotBlank(message = "El nombre del equipo virtual no puede estar vacío")
    @Pattern(regexp = "^[a-zA-Z0-9 ñÑáéíóúÁÉÍÓÚüÜ-]+$", message = "El nombre del equipo contiene caracteres no permitidos")
    private String nombreEquipo;

    @NotNull(message = "Debes seleccionar un equipo favorito")
    private Long equipoFavoritoId;
}
