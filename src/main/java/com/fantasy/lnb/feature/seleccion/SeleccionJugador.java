package com.fantasy.lnb.feature.seleccion;

import com.fantasy.lnb.feature.mercado.PosicionJugador;
import jakarta.persistence.*;
import lombok.*;

/**
 * Jugador de una selección, cargado a mano desde el admin.
 * Es independiente de jugador_real: no aparece en el mercado del fantasy
 * ni le mueve el promedio ni el precio en la liga aunque juegue en la LNB.
 */
@Entity
@Table(name = "seleccion_jugador")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeleccionJugador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seleccion_equipo_id", nullable = false)
    private SeleccionEquipo equipo;

    @Column(nullable = false)
    private String nombreCompleto;

    @Column(nullable = false)
    private Integer numeroCamiseta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(50)")
    private PosicionJugador posicion;

    // Precio en créditos para el armado del quinteto (tope de 50cr)
    @Column(nullable = false, columnDefinition = "DECIMAL(6,2)")
    private Double valor;
}
