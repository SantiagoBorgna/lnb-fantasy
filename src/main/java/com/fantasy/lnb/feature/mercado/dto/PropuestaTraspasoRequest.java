package com.fantasy.lnb.feature.mercado.dto;

import lombok.Data;
import java.util.List;

@Data
public class PropuestaTraspasoRequest {
    private Long torneoId;
    private Long equipoReceptorId; // o receptorUsuarioId
    
    private List<Long> jugadoresOfrecidosIds;
    private List<Long> jugadoresSolicitadosIds;
    
    private Long dtOfrecidoId;
    private Long dtSolicitadoId;
}
