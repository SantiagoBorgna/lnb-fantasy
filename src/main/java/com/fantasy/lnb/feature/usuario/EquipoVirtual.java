package com.fantasy.lnb.feature.usuario;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "equipo_virtual")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipoVirtual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, columnDefinition = "DECIMAL(8,2) DEFAULT 100.00")
    private Double presupuestoActual;

    @Column(nullable = false, columnDefinition = "DECIMAL(10,2) DEFAULT 0.00")
    private Double puntajeGlobal;
}