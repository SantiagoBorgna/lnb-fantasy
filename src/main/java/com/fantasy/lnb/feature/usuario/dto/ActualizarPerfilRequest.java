package com.fantasy.lnb.feature.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ActualizarPerfilRequest {
    @NotBlank(message = "El nombre del mánager no puede estar vacío")
    private String nombreDisplay;

    @NotBlank(message = "El nombre del equipo virtual no puede estar vacío")
    private String nombreEquipo;

    @NotNull(message = "Debes seleccionar un equipo favorito")
    private Long equipoFavoritoId;
}
