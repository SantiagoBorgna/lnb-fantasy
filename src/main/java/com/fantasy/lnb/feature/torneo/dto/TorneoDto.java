package com.fantasy.lnb.feature.torneo.dto;

import com.fantasy.lnb.feature.torneo.TipoTorneo;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

// ── Response: resumen del torneo ──────────────────────────────────────────────
@Data
@Builder
public class TorneoDto {
    private Long id;
    private String nombre;
    private String descripcion;
    private TipoTorneo tipo;
    private String codigoInvitacion; // Solo visible al creador
    private String urlInvitacion; // Link completo para compartir
    private String creadorNombre;
    private Integer cantidadParticipantes;
    private LocalDateTime creadoEn;
}