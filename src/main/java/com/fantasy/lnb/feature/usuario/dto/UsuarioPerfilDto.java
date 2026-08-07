package com.fantasy.lnb.feature.usuario.dto;

import com.fantasy.lnb.feature.usuario.EstadoOnboarding;
import lombok.Builder;
import lombok.Data;

// ── Response: estado completo del usuario para que el frontend sepa dónde redirigir
@Data
@Builder
public class UsuarioPerfilDto {
    private Long id;
    private String email;
    private String nombreDisplay;
    private String avatarUrl;
    private EstadoOnboarding estadoOnboarding;
    private java.util.Set<String> ayudasVistas;

    // Datos del equipo favorito
    private Long equipoFavoritoId;
    private String equipoFavoritoNombre;
    private String equipoFavoritoSigla;
    private String colorPrincipal;
    private String colorSecundario;
    private Integer modeloCamiseta;

    // Datos del equipo virtual
    private Long equipoVirtualId;
    private String nombreEquipoVirtual;
    private Double presupuestoActual;
    private Double puntajeGlobal;
}