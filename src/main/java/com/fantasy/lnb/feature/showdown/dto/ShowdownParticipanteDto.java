package com.fantasy.lnb.feature.showdown.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ShowdownParticipanteDto {
    private Long id;
    private String nombre;
    private String apellido;
    private Double puntosTotales;
    private Boolean esMio; // Si es el usuario actual
}
