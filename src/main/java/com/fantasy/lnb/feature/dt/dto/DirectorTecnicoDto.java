package com.fantasy.lnb.feature.dt.dto;

import com.fantasy.lnb.feature.mercado.EstadoJugador;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DirectorTecnicoDto {
    private Long id;
    private String nombreCompleto;
    private String nacionalidad;
    private Long equipoId;
    private String equipoNombre;
    private String equipoSigla;
    private String colorPrincipal;
    private String colorSecundario;
    private EstadoJugador estado;
}