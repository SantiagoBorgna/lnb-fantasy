package com.fantasy.lnb.feature.seleccion;

import jakarta.persistence.*;
import lombok.*;

/**
 * Selección nacional (Argentina, Bahamas, Canadá...) del Modo Relámpago Selección.
 * Tabla propia: no se mezcla con los equipos de la liga.
 */
@Entity
@Table(name = "seleccion_equipo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeleccionEquipo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    @Column(nullable = false, length = 5)
    private String sigla;

    @Column(nullable = false, length = 7)
    private String colorPrincipal;

    @Column(nullable = false, length = 7)
    private String colorSecundario;

    @Builder.Default
    @Column(nullable = false)
    private Integer modeloCamiseta = 1;
}
