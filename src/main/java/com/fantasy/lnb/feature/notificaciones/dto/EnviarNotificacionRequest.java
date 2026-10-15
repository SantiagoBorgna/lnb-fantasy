package com.fantasy.lnb.feature.notificaciones.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Notificación que manda el admin desde el panel.
 * soloPrueba = true → solo a los dispositivos del admin que la envía.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnviarNotificacionRequest {
    private String titulo;
    private String mensaje;
    private String url;
    private boolean soloPrueba;
}
