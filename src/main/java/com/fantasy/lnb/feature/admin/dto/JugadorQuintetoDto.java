package com.fantasy.lnb.feature.admin.dto;

import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.mercado.JugadorReal;
import com.fantasy.lnb.feature.mercado.PosicionJugador;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JugadorQuintetoDto {
    private Long id;
    private String nombre;
    private String apellido;
    private String clubReal;
    private PosicionJugador posicion;
    private Double puntosFantasy;
    private Boolean esCapitan;
    
    // Para la CamisetaSVG
    private Integer numeroCamiseta;
    private Integer modeloCamiseta;
    private String colorPrincipal;
    private String colorSecundario;

    public static JugadorQuintetoDto desde(JugadorReal jr, Double puntos, boolean esCapitan) {
        EquipoReal equipo = jr.getEquipoReal();
        return JugadorQuintetoDto.builder()
                .id(jr.getId())
                .nombre(jr.getNombreCompleto())
                .apellido("") // nombreCompleto tiene todo
                .clubReal(equipo != null ? equipo.getSigla() : "N/A")
                .posicion(jr.getPosicion())
                .puntosFantasy(puntos)
                .esCapitan(esCapitan)
                .numeroCamiseta(jr.getNumeroCamiseta())
                .modeloCamiseta(equipo != null ? equipo.getModeloCamiseta() : 1)
                .colorPrincipal(equipo != null ? equipo.getColorPrincipal() : "#FFFFFF")
                .colorSecundario(equipo != null ? equipo.getColorSecundario() : "#000000")
                .build();
    }
}
