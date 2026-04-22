package com.fantasy.lnb.feature.estadisticas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Mapea la respuesta JSON del endpoint de acta digital de GES Deportiva.
 * 
 * @JsonIgnoreProperties(ignoreUnknown = true) es CRÍTICO: si GES agrega
 *                                     campos nuevos a su API, no rompe nuestro
 *                                     parser.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GesPartidoDto {

    @JsonProperty("id")
    private String gesPartidoId;

    @JsonProperty("fecha")
    private String fechaHora; // Parsear a LocalDateTime en el Service

    @JsonProperty("equipoLocal")
    private GesEquipoDto equipoLocal;

    @JsonProperty("equipoVisitante")
    private GesEquipoDto equipoVisitante;

    // --- Equipo anidado ---
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GesEquipoDto {

        @JsonProperty("id")
        private String gesEquipoId;

        @JsonProperty("nombre")
        private String nombre;

        @JsonProperty("puntosTotal")
        private Integer puntosTotal; // Para calcular quién ganó y por cuánto

        @JsonProperty("jugadores")
        private List<GesJugadorDto> jugadores;
    }

    // --- Jugador anidado dentro del equipo ---
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GesJugadorDto {

        @JsonProperty("id")
        private String gesJugadorId; // Clave para buscar en nuestra BD

        @JsonProperty("nombre")
        private String nombre;

        @JsonProperty("numero")
        private Integer numeroCamiseta;

        @JsonProperty("titular")
        private Boolean titular;

        // Estadísticas — nombres basados en estructura típica GES
        @JsonProperty("puntos")
        private Integer puntos;

        @JsonProperty("rebotesDefensivos")
        private Integer rebotesDefensivos;

        @JsonProperty("rebotesOfensivos")
        private Integer rebotesOfensivos;

        @JsonProperty("asistencias")
        private Integer asistencias;

        @JsonProperty("recuperos") // ← "robos" en GES se llama "recuperos"
        private Integer robos;

        @JsonProperty("taponesHechos")
        private Integer taponesRealizados;

        @JsonProperty("taponesRecibidos")
        private Integer taponesRecibidos;

        @JsonProperty("perdidas")
        private Integer perdidas;

        @JsonProperty("tirosDeCampoFallados")
        private Integer tirosCampoFallados;

        @JsonProperty("tirosLibresFallados")
        private Integer tirosLibresFallados;

        @JsonProperty("faltasRecibidas")
        private Integer faltasRecibidas;

        @JsonProperty("faltasCometidas")
        private Integer faltasCometidas;

        @JsonProperty("faltas") // Total de faltas — para detectar 5ta falta
        private Integer totalFaltas;

        @JsonProperty("faltaTecnica")
        private Boolean faltaTecnica;

        @JsonProperty("descalificado")
        private Boolean descalificado;
    }
}