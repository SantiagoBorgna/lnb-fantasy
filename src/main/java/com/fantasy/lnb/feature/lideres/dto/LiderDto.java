package com.fantasy.lnb.feature.lideres.dto;

import lombok.Builder;
import lombok.Data;

// ── Un jugador en el ranking de una categoría ─────────────────────────────────
@Data
@Builder
public class LiderDto {
    private Long jugadorRealId;
    private String nombreCompleto;
    private String equipoSigla;
    private String colorPrincipal;
    private String colorSecundario;
    private Integer modeloCamiseta;
    private Integer numeroCamiseta;
    private Double promedio; // Promedio de la categoría por partido
    private Integer partidosJugados;
}