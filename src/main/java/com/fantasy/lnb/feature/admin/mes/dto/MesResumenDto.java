package com.fantasy.lnb.feature.admin.mes.dto;

import com.fantasy.lnb.feature.admin.dto.JugadorQuintetoDto;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MesResumenDto {
    private Long mesId;
    private String nombre;
    /** Jornadas del mes (todas, ordenadas por número) */
    private List<JornadaMesDto> jornadas;
    /** Top 10 sumando solo las jornadas del mes en juego o finalizadas */
    private List<PosicionMesDto> tabla;
    /** Plantel del líder del mes en la última jornada jugada del mes (null si no hay) */
    private PunteroMesDto puntero;
    /** Mejor jugador por posición según su promedio en el mes (con mínimo de partidos) */
    private List<JugadorPromedioMesDto> quintetoIdeal;
    /** Top 5 por promedio en el mes (con mínimo de partidos) */
    private List<JugadorPromedioMesDto> mvp;
    /** Partidos mínimos para entrar al quinteto ideal / MVP */
    private int minimoPartidos;
    private List<GanadorJornadaDto> ganadoresJornada;

    @Data
    @Builder
    public static class JornadaMesDto {
        private Long id;
        private Integer numero;
        private String estado;
    }

    @Data
    @Builder
    public static class PosicionMesDto {
        private int posicion;
        private Long usuarioId;
        private String nombreEquipo;
        private String nombreUsuario;
        private double puntos;
        private int jornadasJugadas;
        private double mejorFecha;
    }

    @Data
    @Builder
    public static class PunteroMesDto {
        private String nombreEquipo;
        private String nombreUsuario;
        private double puntajeMes;
        private Integer jornadaNumero;
        private double puntajeJornada;
        private List<JugadorQuintetoDto> jugadores;
    }

    @Data
    @Builder
    public static class JugadorPromedioMesDto {
        private JugadorQuintetoDto jugador;
        private int partidos;
        private double promedio;
        private double total;
    }

    @Data
    @Builder
    public static class GanadorJornadaDto {
        private Integer jornadaNumero;
        private String nombreEquipo;
        private String nombreUsuario;
        private double puntaje;
    }
}
