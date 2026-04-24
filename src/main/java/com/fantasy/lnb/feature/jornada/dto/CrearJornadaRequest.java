package com.fantasy.lnb.feature.jornada.dto;

import lombok.Data;
import java.time.LocalDateTime;

// ── Request de creación (solo admin) ─────────────────────────────────────────
@Data
public class CrearJornadaRequest {
    private Integer numero;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
}