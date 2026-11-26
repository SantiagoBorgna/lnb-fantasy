package com.fantasy.lnb.feature.seleccion.dto;

import com.fantasy.lnb.feature.showdown.EstadoShowdown;

public record AdminSeleccionEventoDto(
        Long id,
        String codigoInscripcion,
        EstadoShowdown estado,
        Long localId,
        String localSigla,
        String localNombre,
        Long visitanteId,
        String visitanteSigla,
        String visitanteNombre,
        String fecha,
        Integer puntosLocal,
        Integer puntosVisitante,
        int participantesCount,
        boolean mostrarBanner) {
}
