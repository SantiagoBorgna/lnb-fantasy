package com.fantasy.lnb.feature.torneo.dto;

import com.fantasy.lnb.feature.torneo.TipoTorneo;
import lombok.Data;

@Data
public class EditarTorneoRequest {
    private String nombre;
    private String descripcion;
    private TipoTorneo tipo;
}