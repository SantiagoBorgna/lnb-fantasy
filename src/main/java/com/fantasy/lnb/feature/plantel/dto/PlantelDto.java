package com.fantasy.lnb.feature.plantel.dto;

import com.fantasy.lnb.feature.mercado.EstadoJugador;
import com.fantasy.lnb.feature.mercado.PosicionJugador;
import com.fantasy.lnb.feature.plantel.RolPlantel;
import lombok.Builder;
import lombok.Data;
import java.util.List;

// ── Response: estado actual del plantel del usuario ───────────────────────────
@Data
@Builder
public class PlantelDto {

    private Long plantelId;
    private Integer jornadaNumero;
    private String formacion;
    private Double puntajeObtenidoFecha;
    private Integer transferenciasUsadas;
    private Integer transferenciasRestantes;
    private String nombreEquipo;
    private Double puntajeDt;

    // DT
    private DtDto dt;

    // Los 10 jugadores con sus roles y multiplicadores
    private List<JugadorPlantelDto> jugadores;

    // Presupuesto restante del usuario (calculado en tiempo real)
    private Double presupuestoRestante;

    @Data
    @Builder
    public static class JugadorPlantelDto {
        private Long jugadorPlantelId;
        private Long jugadorRealId;
        private String nombreCompleto;
        private Integer numeroCamiseta;
        private String equipoSigla;
        private String colorPrincipal;
        private String colorSecundario;
        private Integer modeloCamiseta;
        private PosicionJugador posicion;
        private EstadoJugador estado;
        private RolPlantel rol;
        private Double multiplicador;
        private Double precioDeCompra;
        private Double valorMercadoActual;
        private Double promedioPuntosUltimas3;
    }

    @Data
    @Builder
    public static class DtDto {
        private Long dtId;
        private String nombreCompleto;
        private String nacionalidad;
        private String equipoNombre;
        private String equipoSigla;
        private String colorPrincipal;
        private String colorSecundario;
        private EstadoJugador estado;
        private Double promedioFantasy;
    }
}