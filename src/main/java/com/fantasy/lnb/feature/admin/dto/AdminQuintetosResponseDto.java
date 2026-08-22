package com.fantasy.lnb.feature.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminQuintetosResponseDto {
    private String nombreUsuarioGanador;
    private Double puntajeUsuarioGanador;
    private List<JugadorQuintetoDto> mejorQuintetoUsuario;
    
    private Double puntajeQuintetoIdeal;
    private List<JugadorQuintetoDto> quintetoIdealTeorico;
}
