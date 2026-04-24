package com.fantasy.lnb.feature.plantel.dto;

import com.fantasy.lnb.feature.plantel.RolPlantel;
import lombok.Data;
import java.util.List;

// ── Request: armar o guardar el plantel completo ──────────────────────────────
@Data
public class GuardarPlantelRequest {

    private Long dtId; // ID del JugadorReal elegido como DT
    private String formacion; // "1-2-2", "1-3-1", etc.
    private List<SlotJugadorRequest> jugadores; // Exactamente 10 jugadores

    @Data
    public static class SlotJugadorRequest {
        private Long jugadorRealId;
        private RolPlantel rol; // TITULAR, CAPITAN, SEXTO_HOMBRE, SUPLENTE
    }
}