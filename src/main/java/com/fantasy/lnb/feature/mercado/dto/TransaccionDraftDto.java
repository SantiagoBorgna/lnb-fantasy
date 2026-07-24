package com.fantasy.lnb.feature.mercado.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class TransaccionDraftDto {
    private Long id;
    private Long equipoUsuarioId;
    private String equipoUsuarioNombre;
    private Long jugadorEntranteId;
    private String jugadorEntranteNombre;
    private Long jugadorSalienteId;
    private String jugadorSalienteNombre;
    private Long dtEntranteId;
    private String dtEntranteNombre;
    private Long dtSalienteId;
    private String dtSalienteNombre;
    private String tipo;
    private LocalDateTime fecha;
}
