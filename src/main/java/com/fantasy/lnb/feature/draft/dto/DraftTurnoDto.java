package com.fantasy.lnb.feature.draft.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class DraftTurnoDto {
    private Long id;
    private Long usuarioId;
    private String nombreUsuario;
    private String nombreEquipo;
    private int ronda;
    private int numeroTurnoGlobal;
    private boolean completado;
    private Long jugadorRealIdElegido;
    private String nombreJugadorElegido;
    private Long dtIdElegido;
    private String nombreDtElegido;
    private LocalDateTime inicioTurno;
    private LocalDateTime limiteTiempo;
    private boolean fueAutoPick;
}
