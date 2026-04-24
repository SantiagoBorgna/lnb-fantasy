package com.fantasy.lnb.feature.dt;

import com.fantasy.lnb.feature.equipo.EquipoReal;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "director_tecnico")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DirectorTecnico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombreCompleto;

    @Column(nullable = false)
    private String nacionalidad;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipo_lnb_id", nullable = false)
    private EquipoReal equipoReal;

    // Estado para mostrar en el mercado de DTs
    // Reutilizamos el mismo Enum que los jugadores — aplica igual
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private com.fantasy.lnb.feature.mercado.EstadoJugador estado;

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
}