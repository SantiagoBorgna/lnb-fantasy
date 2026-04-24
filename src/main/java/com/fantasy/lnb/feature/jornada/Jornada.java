package com.fantasy.lnb.feature.jornada;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "jornada")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Jornada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Número secuencial visible para el usuario (Jornada 1, Jornada 2...)
    @Column(nullable = false, unique = true)
    private Integer numero;

    // Ventana de 3-4 días que agrupa los partidos de esta jornada Fantasy
    @Column(nullable = false)
    private LocalDateTime fechaInicio;

    @Column(nullable = false)
    private LocalDateTime fechaFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoJornada estado;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(nullable = false)
    private LocalDateTime actualizadoEn;

    @PrePersist
    protected void onCreate() {
        this.creadoEn = LocalDateTime.now();
        this.actualizadoEn = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.actualizadoEn = LocalDateTime.now();
    }

    // ── Helpers de dominio ──────────────────────────────────────────────────

    /** Verdadero si el timestamp dado cae dentro de la ventana de esta jornada. */
    public boolean contieneTimestamp(LocalDateTime timestamp) {
        return !timestamp.isBefore(fechaInicio) && !timestamp.isAfter(fechaFin);
    }

    public boolean estaAbierta() {
        return estado == EstadoJornada.ABIERTA_A_CAMBIOS;
    }

    public boolean estaEnJuego() {
        return estado == EstadoJornada.EN_JUEGO;
    }

    public boolean estaFinalizada() {
        return estado == EstadoJornada.FINALIZADA;
    }
}