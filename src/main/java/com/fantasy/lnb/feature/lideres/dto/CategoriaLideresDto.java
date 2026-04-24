package com.fantasy.lnb.feature.lideres.dto;

import lombok.Builder;
import lombok.Data;

// ── Una tarjeta de categoría en la pantalla de Líderes ───────────────────────
@Data
@Builder
public class CategoriaLideresDto {
    private String categoria; // "Puntos", "Rebotes", "Asistencias", etc.
    private String icono; // Nombre del icono para el frontend
    private LiderDto lider; // El jugador #1 de la categoría
}