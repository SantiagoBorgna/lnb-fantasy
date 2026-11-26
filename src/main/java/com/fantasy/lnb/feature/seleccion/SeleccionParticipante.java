package com.fantasy.lnb.feature.seleccion;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "seleccion_participante")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeleccionParticipante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seleccion_evento_id", nullable = false)
    private SeleccionEvento evento;

    // UUID del dispositivo (localStorage), igual que en el Relámpago de la liga
    @Column(nullable = false)
    private String uuidDispositivo;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellido;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private Long capitanId;

    @Builder.Default
    @Column(nullable = false)
    private Double puntosTotales = 0.0;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "base_id", nullable = false)
    private SeleccionJugador base;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "escolta_id", nullable = false)
    private SeleccionJugador escolta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alero_id", nullable = false)
    private SeleccionJugador alero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alapivot_id", nullable = false)
    private SeleccionJugador alaPivot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pivot_id", nullable = false)
    private SeleccionJugador pivot;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    protected void onCreate() {
        this.creadoEn = LocalDateTime.now();
    }

    public List<SeleccionJugador> quinteto() {
        return List.of(base, escolta, alero, alaPivot, pivot);
    }
}
