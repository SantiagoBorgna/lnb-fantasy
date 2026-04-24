package com.fantasy.lnb.feature.plantel.dto;

import lombok.Builder;
import lombok.Data;

// ── Response: resultado de la transferencia ───────────────────────────────────
@Data
@Builder
public class TransferenciaResultadoDto {

    private String jugadorSaleNombre;
    private String jugadorEntraNombre;
    private Double diferenciaPresupuesto; // Positivo = ganó créditos, negativo = gastó
    private Double presupuestoRestante;
    private Integer transferenciasUsadas;
    private Integer transferenciasRestantes;
}