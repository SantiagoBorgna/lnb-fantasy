package com.fantasy.lnb.feature.mercado.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PropuestaTraspasoDto {
    private Long id;
    private Long torneoId;
    
    private Long equipoProponenteId;
    private String equipoProponenteNombre;
    private String equipoProponenteUsuarioNombre;
    
    private Long equipoReceptorId;
    private String equipoReceptorNombre;
    private String equipoReceptorUsuarioNombre;
    
    private List<JugadorMercadoDto> jugadoresOfrecidos;
    private List<JugadorMercadoDto> jugadoresSolicitados;
    
    private Long dtOfrecidoId;
    private String dtOfrecidoNombre;
    
    private Long dtSolicitadoId;
    private String dtSolicitadoNombre;
    
    private String estado;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaResolucion;
}
