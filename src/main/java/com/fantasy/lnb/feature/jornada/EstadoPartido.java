package com.fantasy.lnb.feature.jornada;

public enum EstadoPartido {
    PROGRAMADO, // Cargado en el fixture, aún no jugado
    FINALIZADO, // Terminó, pendiente de scraping
    PROCESADO // Estadísticas ya scrapeadas y persistidas
}