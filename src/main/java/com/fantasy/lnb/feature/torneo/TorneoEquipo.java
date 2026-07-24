package com.fantasy.lnb.feature.torneo;

import com.fantasy.lnb.feature.usuario.EquipoVirtual;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "torneo_equipo", uniqueConstraints = @UniqueConstraint(name = "uk_torneo_equipo", columnNames = {
        "torneo_id", "equipo_virtual_id" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TorneoEquipo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "torneo_id", nullable = false)
    private Torneo torneo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipo_virtual_id", nullable = false)
    private EquipoVirtual equipoVirtual;

    // Fecha de ingreso al torneo
    @Column(nullable = false, updatable = false)
    private LocalDateTime unidoEn;

    // Prioridad en la lista de waivers (1 = más alta). Se sortea al inicio y rota de forma redonda.
    @Column
    private Integer prioridadWaiver;

    @Column(nullable = false)
    @Builder.Default
    private Double puntajeGlobal = 0.0; // Puntos totales obtenidos en el contexto de ESTE torneo (útil para Draft)

    // --- Estadísticas H2H ---
    @Column(nullable = false)
    @Builder.Default
    private Integer partidosGanados = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer partidosEmpatados = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer partidosPerdidos = 0;

    @Column(nullable = false)
    @Builder.Default
    private Double puntosFavor = 0.0; // Desempate: Total de puntos fantasy obtenidos en los duelos H2H

    @PrePersist
    protected void onCreate() {
        this.unidoEn = LocalDateTime.now();
    }
}