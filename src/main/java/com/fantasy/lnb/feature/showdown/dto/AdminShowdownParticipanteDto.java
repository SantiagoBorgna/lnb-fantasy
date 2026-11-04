package com.fantasy.lnb.feature.showdown.dto;

import lombok.Builder;
import lombok.Data;

// Solo para el panel de admin: el ranking público (ShowdownParticipanteDto) no expone el email
@Data
@Builder
public class AdminShowdownParticipanteDto {
    private Long id;
    private String nombre;
    private String apellido;
    private String email;
    private Double puntosTotales;
}
