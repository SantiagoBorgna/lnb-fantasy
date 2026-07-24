package com.fantasy.lnb.feature.torneo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnfrentamientoDto {
    private Long id;
    
    private Long jornadaId;
    private Integer jornadaNumero;
    
    private Long equipoLocalId;
    private String equipoLocalNombre;
    
    // Puede ser null si el equipo local tiene fecha libre
    private Long equipoVisitanteId;
    private String equipoVisitanteNombre;
    
    private Double puntajeLocal;
    private Double puntajeVisitante;
    
    private Boolean procesado;
}
