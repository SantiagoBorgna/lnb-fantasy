package com.fantasy.lnb.feature.equipo;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "equipo_real")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipoReal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String nombre;

    @Column(nullable = false, length = 5)
    private String sigla; // Ej: "BAT", "SAN", "CDU"

    // Colores en formato hex para renderizar la camiseta SVG en el frontend
    @Column(nullable = false, length = 7)
    private String colorPrincipal; // Ej: "#1A3A6B"

    @Column(nullable = false, length = 7)
    private String colorSecundario; // Ej: "#FFFFFF"

    @Column(name = "modelo_camiseta")
    private Integer modeloCamiseta = 1;
}