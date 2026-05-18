package com.fantasy.lnb.feature.mercado;

import com.fantasy.lnb.feature.equipo.EquipoReal;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "jugador_real")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JugadorReal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ID que usa GES Deportiva — clave para cruzar con el scraper
    @Column(nullable = false, unique = true)
    private Long gesId;

    @Column(nullable = false)
    private String nombreCompleto;

    @Column(nullable = false)
    private Integer numeroCamiseta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipo_lnb_id", nullable = false)
    private EquipoReal equipoReal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PosicionJugador posicion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoJugador estado;

    // Precio actual en el mercado Fantasy (arranca con valor base al cargar el
    // plantel)
    @Column(nullable = false, columnDefinition = "DECIMAL(6,2) DEFAULT 5.00")
    private Double valorMercadoActual;

    // Valor base contra el que se compara el promedio móvil para ajustar precio.
    // Se setea una sola vez al dar de alta al jugador y nunca cambia.
    @Column(nullable = false, columnDefinition = "DECIMAL(6,2) DEFAULT 5.00")
    private Double valorBase;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(nullable = false)
    private LocalDateTime actualizadoEn;

    // URL del perfil en GES (para re-scraping futuro)
    @Column(length = 500)
    private String gesPerfilUrl;

    // Foto de perfil de GES (guardamos la URL, no la imagen)
    @Column(length = 500)
    private String fotoUrl;

    @Column
    private java.time.LocalDate fechaNacimiento;

    @Column(name = "promedio_fantasy")
    private Double promedioFantasy = 0.0;

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