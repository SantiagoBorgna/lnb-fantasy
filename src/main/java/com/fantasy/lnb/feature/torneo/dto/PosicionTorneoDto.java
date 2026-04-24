package com.fantasy.lnb.feature.torneo.dto;

import lombok.Builder;
import lombok.Data;

// ── Response: una fila de la tabla de posiciones ──────────────────────────────
@Data
@Builder
public class PosicionTorneoDto {
    private Integer posicion; // 1°, 2°, 3°...
    private String nombreEquipo;
    private String nombreUsuario;
    private Double puntajeGlobal;
    private Long equipoVirtualId;
}