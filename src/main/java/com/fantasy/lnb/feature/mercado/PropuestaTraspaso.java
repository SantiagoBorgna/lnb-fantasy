package com.fantasy.lnb.feature.mercado;

import com.fantasy.lnb.feature.dt.DirectorTecnico;
import com.fantasy.lnb.feature.usuario.EquipoVirtual;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.torneo.Torneo;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "propuesta_traspaso")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropuestaTraspaso {

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
    @JoinColumn(name = "equipo_proponente_id", nullable = false)
    private EquipoVirtual equipoProponente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipo_receptor_id", nullable = false)
    private EquipoVirtual equipoReceptor;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "prop_jugadores_ofrecidos",
            joinColumns = @JoinColumn(name = "propuesta_id", foreignKey = @ForeignKey(name = "fk_prop_jug_ofr_prop")),
            inverseJoinColumns = @JoinColumn(name = "jugador_id", foreignKey = @ForeignKey(name = "fk_prop_jug_ofr_jug"))
    )
    @Builder.Default
    private List<JugadorReal> jugadoresOfrecidos = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "prop_jugadores_solicitados",
            joinColumns = @JoinColumn(name = "propuesta_id", foreignKey = @ForeignKey(name = "fk_prop_jug_sol_prop")),
            inverseJoinColumns = @JoinColumn(name = "jugador_id", foreignKey = @ForeignKey(name = "fk_prop_jug_sol_jug"))
    )
    @Builder.Default
    private List<JugadorReal> jugadoresSolicitados = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "dt_ofrecido_id")
    private DirectorTecnico dtOfrecido;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "dt_solicitado_id")
    private DirectorTecnico dtSolicitado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPropuestaTraspaso estado;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    private LocalDateTime fechaResolucion;

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        if (this.estado == null) {
            this.estado = EstadoPropuestaTraspaso.PENDIENTE;
        }
    }
}
