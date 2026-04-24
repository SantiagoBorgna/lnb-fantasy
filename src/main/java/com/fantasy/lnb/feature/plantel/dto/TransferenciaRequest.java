package com.fantasy.lnb.feature.plantel.dto;

import com.fantasy.lnb.feature.plantel.RolPlantel;
import lombok.Data;

// ── Request: reemplazar un jugador por otro ───────────────────────────────────
@Data
public class TransferenciaRequest {

    // Jugador que sale del plantel
    private Long jugadorSaleId;

    // Jugador que entra al plantel
    private Long jugadorEntraId;

    // Rol que ocupará el jugador entrante
    // (puede cambiar si se reorganiza el banco)
    private RolPlantel rolEntrante;
}