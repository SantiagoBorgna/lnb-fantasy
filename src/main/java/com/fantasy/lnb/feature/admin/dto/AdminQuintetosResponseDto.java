package com.fantasy.lnb.feature.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminQuintetosResponseDto {
    private String nombreUsuarioGanador;
    // Datos de la persona detrás del equipo ganador (null si no hay planteles en la jornada)
    private Long usuarioIdGanador;
    private String emailUsuarioGanador;
    private String nombreDisplayUsuarioGanador;
    private Double puntajeUsuarioGanador;
    private List<JugadorQuintetoDto> mejorQuintetoUsuario;
    
    private Double puntajeQuintetoIdeal;
    private List<JugadorQuintetoDto> quintetoIdealTeorico;
}
