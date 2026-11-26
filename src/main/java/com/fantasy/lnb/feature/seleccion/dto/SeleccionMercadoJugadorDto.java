package com.fantasy.lnb.feature.seleccion.dto;

/**
 * Jugador del mercado de un Relámpago Selección. Tiene la misma forma que JugadorReal
 * (valorMercadoActual, equipoReal...) para reutilizar el draft del Relámpago de la liga.
 */
public record SeleccionMercadoJugadorDto(
        Long id,
        String nombreCompleto,
        Integer numeroCamiseta,
        String posicion,
        String estado,
        Double valorMercadoActual,
        Equipo equipoReal) {

    public record Equipo(String nombre, String sigla, String colorPrincipal, String colorSecundario, Integer modeloCamiseta) {
    }
}
