package com.fantasy.lnb.feature.showdown;

public enum EstadoShowdown {
    ABIERTO,     // El partido aún no empezó, la gente puede armar su equipo
    EN_CURSO,    // El partido empezó, o terminó pero aún no se procesaron las estadísticas
    FINALIZADO   // Las estadísticas fueron procesadas y el ranking es definitivo
}
