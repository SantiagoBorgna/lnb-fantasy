package com.fantasy.lnb.feature.mercado;

import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.torneo.Torneo;
import com.fantasy.lnb.feature.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "waiver_claim")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaiverClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "torneo_id", nullable = false)
    private Torneo torneo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jornada_id", nullable = false)
    private Jornada jornada;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "jugador_elegido_id", nullable = true)
    private JugadorReal jugadorElegido;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "jugador_cortado_id", nullable = true)
    private JugadorReal jugadorCortado; // Puede ser null si el plantel tiene espacio (por ej. si hizo un drop antes)

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "dt_elegido_id", nullable = true)
    private com.fantasy.lnb.feature.dt.DirectorTecnico dtElegido;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "dt_cortado_id", nullable = true)
    private com.fantasy.lnb.feature.dt.DirectorTecnico dtCortado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EstadoClaim estado = EstadoClaim.PENDIENTE;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column
    private String motivoRechazo; // Si fue rechazado (ej. "Jugador ya fichado por equipo con mayor prioridad")

    @PrePersist
    protected void onCreate() {
        this.creadoEn = LocalDateTime.now();
    }
}
