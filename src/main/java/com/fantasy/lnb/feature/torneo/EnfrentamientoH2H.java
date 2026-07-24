package com.fantasy.lnb.feature.torneo;

import com.fantasy.lnb.feature.jornada.Jornada;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "enfrentamiento_h2h")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnfrentamientoH2H {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "torneo_id", nullable = false)
    private Torneo torneo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jornada_id", nullable = false)
    private Jornada jornada;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipo_local_id", nullable = false)
    private TorneoEquipo equipoLocal;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "equipo_visitante_id", nullable = true)
    private TorneoEquipo equipoVisitante; // Null si hay un número impar y le tocó "Bye" (libre)

    @Column
    private Double puntajeLocal; // Puntos Fantasy logrados en esa jornada

    @Column
    private Double puntajeVisitante;

    @Column(nullable = false)
    @Builder.Default
    private Boolean procesado = false; // Se pone en true cuando termina la jornada y se suman los 3 puntos
}
