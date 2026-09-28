package com.fantasy.lnb.feature.admin.dto;

import lombok.Data;

@Data
public class FusionarJugadoresRequestDto {
    // Jugador que se conserva (el que ya tiene planteles/historial de usuarios enganchado)
    private Long idPrincipal;

    // Jugador redundante que se descarta (normalmente el que el crawler creó de más)
    private Long idDuplicado;
}
