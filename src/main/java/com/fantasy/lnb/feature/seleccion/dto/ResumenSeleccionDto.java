package com.fantasy.lnb.feature.seleccion.dto;

import java.util.List;

/**
 * Datos de las historias del Relámpago Selección para el admin: los quintetos de los que
 * terminaron primeros (varios si hay empate: el admin elige al que ganó el sorteo) y el
 * quinteto ideal del partido (el mejor puntaje fantasy de cada posición).
 */
public record ResumenSeleccionDto(
        String localNombre,
        String visitanteNombre,
        Integer puntosLocal,
        Integer puntosVisitante,
        List<Ganador> ganadores,
        List<Jugador> quintetoIdeal) {

    public record Ganador(Long participanteId, String nombre, String apellido, Double puntosTotales, List<Jugador> jugadores) {
    }

    /** Forma que usan las placas de la canchita (quintetoCanvas). puntosFantasy ya incluye el x1.5 del capitán. */
    public record Jugador(String nombre, Integer numeroCamiseta, String posicion, String equipoSigla,
                          String colorPrincipal, String colorSecundario, Integer modeloCamiseta,
                          Double puntosFantasy, boolean esCapitan) {
    }
}
