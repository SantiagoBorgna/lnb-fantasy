package com.fantasy.lnb.feature.admin.dto;

import com.fantasy.lnb.feature.jornada.EstadoJornada;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AdminJornadaRequest {
    private Integer numero;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private EstadoJornada estado;
}
