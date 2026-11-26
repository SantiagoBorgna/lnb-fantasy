package com.fantasy.lnb.feature.seleccion;

import jakarta.persistence.*;
import lombok.*;

/**
 * Planilla de un jugador en un partido de la selección, cargada a mano por el admin.
 * Separada de estadistica_partido para no tocar los promedios ni los precios de la liga.
 */
@Entity
@Table(name = "seleccion_estadistica",
        uniqueConstraints = @UniqueConstraint(columnNames = {"seleccion_evento_id", "seleccion_jugador_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeleccionEstadistica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seleccion_evento_id", nullable = false)
    private SeleccionEvento evento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seleccion_jugador_id", nullable = false)
    private SeleccionJugador jugador;

    @Builder.Default @Column(nullable = false) private Boolean titular = false;
    @Builder.Default @Column(nullable = false) private Integer puntos = 0;
    @Builder.Default @Column(nullable = false) private Integer tirosCampoFallados = 0;
    @Builder.Default @Column(nullable = false) private Integer tirosLibresFallados = 0;
    @Builder.Default @Column(nullable = false) private Integer rebotesOfensivos = 0;
    @Builder.Default @Column(nullable = false) private Integer rebotesDefensivos = 0;
    @Builder.Default @Column(nullable = false) private Integer asistencias = 0;
    @Builder.Default @Column(nullable = false) private Integer recuperaciones = 0;
    @Builder.Default @Column(nullable = false) private Integer perdidas = 0;
    @Builder.Default @Column(nullable = false) private Integer taponesRealizados = 0;
    @Builder.Default @Column(nullable = false) private Integer taponesRecibidos = 0;
    @Builder.Default @Column(nullable = false) private Integer faltasCometidas = 0;
    @Builder.Default @Column(nullable = false) private Integer faltasRecibidas = 0;

    // Valoración oficial cargada por el admin (null = se calcula con la fórmula)
    private Integer valoracionManual;

    // Calculados al guardar la planilla
    @Builder.Default @Column(nullable = false) private Integer valoracion = 0;
    @Builder.Default @Column(nullable = false) private Boolean equipoGano = false;
    @Builder.Default @Column(nullable = false) private Boolean fueMvp = false;
    @Builder.Default @Column(nullable = false) private Double puntajeFantasy = 0.0;
}
