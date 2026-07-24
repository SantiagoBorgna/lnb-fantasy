package com.fantasy.lnb.feature.torneo;

import com.fantasy.lnb.feature.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "draft_turno")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DraftTurno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "torneo_id", nullable = false)
    private Torneo torneo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private Integer ronda;

    @Column(nullable = false)
    private Integer numeroTurnoGlobal; // Ej: del 1 al 80 (si son 8 equipos y 10 rondas)

    @Column
    private LocalDateTime inicioTurno; // Cuándo le tocó elegir

    @Column
    private LocalDateTime limiteTiempo; // Cuándo se le vence el tiempo (Auto-Pick)

    @Column(nullable = false)
    @Builder.Default
    private Boolean completado = false;

    @Column
    private Long jugadorRealIdElegido; // Se setea cuando elige a alguien

    @Column
    private Long dtIdElegido; // Se setea en la última ronda

    @Column(nullable = false)
    @Builder.Default
    private Boolean fueAutoPick = false; // True si se venció el tiempo y eligió el sistema
}
