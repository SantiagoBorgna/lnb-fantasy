package com.fantasy.lnb.feature.mercado;

/**
 * Posiciones válidas según el PRD.
 * El orden importa para validar formaciones en el Módulo 5:
 * BASE / ESCOLTA → ALERO / ALA_PIVOT → PIVOT
 */
public enum PosicionJugador {
    BASE,
    ESCOLTA,
    ALERO,
    ALA_PIVOT,
    PIVOT,
    DESCONOCIDO // ← Asignado por el scraper cuando la posición no está disponible
}