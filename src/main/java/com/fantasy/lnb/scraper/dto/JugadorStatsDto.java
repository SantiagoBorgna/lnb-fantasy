package com.fantasy.lnb.scraper.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class JugadorStatsDto {

    // --- Identificadores ---
    @JsonProperty("IdJugador")
    private Long idJugador;

    @JsonProperty("IdClub")
    private Long idClub;

    @JsonProperty("Nombre")
    private String nombre;

    // --- Estadísticas ofensivas ---
    @JsonProperty("Puntos")
    private Integer puntos;

    @JsonProperty("TirosDos")
    private TirosEstatDto tirosDos;

    @JsonProperty("TirosTres")
    private TirosEstatDto tirosTres;

    @JsonProperty("TirosLibres")
    private TirosEstatDto tirosLibres;

    @JsonProperty("Asistencias")
    private Integer asistencias;

    // --- Estadísticas de rebote ---
    @JsonProperty("ReboteDefensivo")
    private Integer reboteDefensivo;

    @JsonProperty("ReboteOfensivo")
    private Integer reboteOfensivo;

    // --- Estadísticas defensivas / negativas ---
    @JsonProperty("Recuperaciones")
    private Integer recuperaciones;

    @JsonProperty("Perdidas")
    private Integer perdidas;

    @JsonProperty("FaltaCometida")
    private Integer faltaCometida;

    @JsonProperty("FaltaRecibida")
    private Integer faltaRecibida;

    // --- Métricas adicionales ---
    @JsonProperty("Valoracion")
    private Integer valoracion; // Índice GES (no se usa en puntuación, útil para debug)

    @JsonProperty("TiempoJuego")
    private String tiempoJuego; // "33:34" — útil para detectar DNP (Did Not Play)

    @JsonProperty("CincoInicial")
    private Boolean cincoInicial; // true = fue titular en el partido real

    @JsonProperty("TaponCometido")
    private Integer taponesRealizados;

    @JsonProperty("TaponRecibido")
    private Integer taponesRecibidos;
}