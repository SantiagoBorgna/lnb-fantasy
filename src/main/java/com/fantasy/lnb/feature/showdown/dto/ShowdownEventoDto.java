package com.fantasy.lnb.feature.showdown.dto;

import com.fantasy.lnb.feature.showdown.EstadoShowdown;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ShowdownEventoDto {
    private Long id;
    private String codigoInscripcion;
    private EstadoShowdown estado;
    
    // Info del partido
    private String localSigla;
    private String localNombre;
    
    private String visitanteSigla;
    private String visitanteNombre;

    private String fecha;
}
