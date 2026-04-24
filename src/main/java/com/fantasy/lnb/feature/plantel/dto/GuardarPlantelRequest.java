package com.fantasy.lnb.feature.plantel.dto;

import com.fantasy.lnb.feature.plantel.RolPlantel;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;

// ── Request: armar o guardar el plantel completo ──────────────────────────────
@Data
public class GuardarPlantelRequest {

    @NotNull(message = "El DT es obligatorio.")
    private Long dtId;

    @NotBlank(message = "La formación es obligatoria.")
    @Pattern(regexp = "^[1-3]-[1-3]-[1-3]$", message = "Formato de formación inválido.")
    private String formacion;

    @NotNull
    @Size(min = 10, max = 10, message = "El plantel debe tener exactamente 10 jugadores.")
    private List<SlotJugadorRequest> jugadores;

    @Data
    public static class SlotJugadorRequest {

        @NotNull(message = "El ID del jugador es obligatorio.")
        private Long jugadorRealId;

        @NotNull(message = "El rol del jugador es obligatorio.")
        private RolPlantel rol;
    }
}