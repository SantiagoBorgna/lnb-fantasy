package com.fantasy.lnb.feature.arrepentimiento.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class SolicitudArrepentimientoRequest {
    private String email;
    private LocalDate fechaContratacion;
    private String motivo;
}
