package com.fantasy.lnb.feature.torneo.dto;

import com.fantasy.lnb.feature.torneo.TipoTorneo;
import lombok.Data;

// ── Request: crear un torneo ──────────────────────────────────────────────────
@Data
public class CrearTorneoRequest {
    private String nombre;
    private String descripcion;
    private TipoTorneo tipo;
}