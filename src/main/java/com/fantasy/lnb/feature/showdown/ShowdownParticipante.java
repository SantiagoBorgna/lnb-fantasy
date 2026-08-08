package com.fantasy.lnb.feature.showdown;

import com.fantasy.lnb.feature.mercado.JugadorReal;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "showdown_participante")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShowdownParticipante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "showdown_evento_id", nullable = false)
    private ShowdownEvento evento;

    // UUID para sesión por localStorage
    @Column(nullable = false)
    private String uuidDispositivo;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellido;

    @Builder.Default
    @Column(nullable = false)
    private Double puntosTotales = 0.0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_id", nullable = false)
    private JugadorReal base;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escolta_id", nullable = false)
    private JugadorReal escolta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alero_id", nullable = false)
    private JugadorReal alero;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alapivot_id", nullable = false)
    private JugadorReal alaPivot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pivot_id", nullable = false)
    private JugadorReal pivot;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    protected void onCreate() {
        this.creadoEn = LocalDateTime.now();
    }
}
