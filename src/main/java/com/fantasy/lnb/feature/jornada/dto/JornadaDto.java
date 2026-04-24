package com.fantasy.lnb.feature.jornada.dto;

import com.fantasy.lnb.feature.jornada.EstadoJornada;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

// ── Respuesta pública (frontend) ─────────────────────────────────────────────
@Data
@Builder
public class JornadaDto {
    private Long id;
    private Integer numero;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private EstadoJornada estado;
    private Long segundosHastaInicio; // Para el countdown del Dashboard
}