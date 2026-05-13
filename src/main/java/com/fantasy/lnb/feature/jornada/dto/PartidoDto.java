package com.fantasy.lnb.feature.jornada.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class PartidoDto {
    private Long id;
    private String equipoLocal;
    private String siglaLocal;
    private String equipoVisitante;
    private String siglaVisitante;
    private LocalDateTime fechaHora;
    private Integer puntosLocal;
    private Integer puntosVisitante;
    private String estado;
}