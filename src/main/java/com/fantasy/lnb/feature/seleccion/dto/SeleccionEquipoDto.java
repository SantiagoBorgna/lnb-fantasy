package com.fantasy.lnb.feature.seleccion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SeleccionEquipoDto(
        Long id,
        @NotBlank String nombre,
        @NotBlank @Size(max = 5) String sigla,
        @NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}") String colorPrincipal,
        @NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}") String colorSecundario,
        Integer modeloCamiseta) {
}
