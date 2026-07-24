package com.fantasy.lnb.feature.mercado;

import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.torneo.Torneo;
import com.fantasy.lnb.feature.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaccion_draft")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransaccionDraft {

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
    @JoinColumn(name = "jugador_entra_id", nullable = true)
    private JugadorReal jugadorEntra;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "jugador_sale_id", nullable = true)
    private JugadorReal jugadorSale;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "dt_entra_id", nullable = true)
    private com.fantasy.lnb.feature.dt.DirectorTecnico dtEntra;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "dt_sale_id", nullable = true)
    private com.fantasy.lnb.feature.dt.DirectorTecnico dtSale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(255)")
    private TipoTransaccionDraft tipo;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;

    @PrePersist
    protected void onCreate() {
        this.fecha = LocalDateTime.now();
    }
}
