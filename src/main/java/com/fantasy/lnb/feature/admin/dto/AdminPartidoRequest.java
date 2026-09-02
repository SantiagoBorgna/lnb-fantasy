package com.fantasy.lnb.feature.admin.dto;

import com.fantasy.lnb.feature.jornada.EstadoPartido;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminPartidoRequest {
    private Long jornadaId;
    private LocalDateTime fechaHora;
    private EstadoPartido estado;
    private Integer puntosLocal;
    private Integer puntosVisitante;
}
