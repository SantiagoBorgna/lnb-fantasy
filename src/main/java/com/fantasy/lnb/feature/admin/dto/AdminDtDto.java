package com.fantasy.lnb.feature.admin.dto;

import com.fantasy.lnb.feature.mercado.EstadoJugador;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDtDto {
    private Long id;
    private String nombreCompleto;
    private Long equipoId;
    private String equipoNombre;
    private String equipoSigla;
    private String colorPrincipal;
    private String colorSecundario;
    private EstadoJugador estado;
    private Double promedioFantasy;
    private Integer cantidadPlanteles;
}
