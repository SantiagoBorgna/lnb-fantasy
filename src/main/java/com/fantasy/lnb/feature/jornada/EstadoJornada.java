package com.fantasy.lnb.feature.jornada;

public enum EstadoJornada {

    ABIERTA_A_CAMBIOS, // Los mánagers pueden editar su plantel
    EN_JUEGO, // Ventana activa — no se permiten cambios
    FINALIZADA // Estadísticas procesadas, precios actualizados
}