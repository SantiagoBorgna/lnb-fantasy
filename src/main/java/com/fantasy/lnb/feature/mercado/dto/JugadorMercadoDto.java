package com.fantasy.lnb.feature.mercado.dto;

import com.fantasy.lnb.feature.mercado.EstadoJugador;
import com.fantasy.lnb.feature.mercado.PosicionJugador;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JugadorMercadoDto {

    private Long id;
    private Long gesId;
    private String nombreCompleto;
    private Integer numeroCamiseta;

    // Datos del equipo real (aplanados — el frontend no necesita el objeto
    // completo)
    private Long equipoId;
    private String equipoNombre;
    private String equipoSigla;
    private Integer modeloCamiseta;
    private String colorPrincipal;
    private String colorSecundario;

    private PosicionJugador posicion;
    private EstadoJugador estado;

    private Double valorMercadoActual;

    // Promedio de puntos Fantasy de las últimas 3 jornadas
    // El frontend lo muestra en la fila del Mercado según el PRD
    private Double promedioPuntosUltimas3;
}