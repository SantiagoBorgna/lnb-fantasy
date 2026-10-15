package com.fantasy.lnb.feature.ranking.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Resumen de la jornada de un usuario para la tarjeta compartible:
 * cuánto sumó, en qué puesto quedó en la fecha y en el general.
 */
@Data
@Builder
public class MiResumenJornadaDto {
    private Integer jornadaNumero;
    private String nombreEquipo;
    private Double puntajeFecha;
    private Integer posicionFecha;
    private Integer totalParticipantesFecha;
    private Integer posicionGlobal;
    private Double puntajeGlobal;
    private String equipoFavoritoColor;
}
