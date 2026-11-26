package com.fantasy.lnb.feature.seleccion;

import com.fantasy.lnb.feature.showdown.EstadoShowdown;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Un partido de la selección con su Relámpago: el partido y el evento son lo mismo
 * (no hay jornadas ni scraper detrás). Las estadísticas se cargan a mano al terminar.
 */
@Entity
@Table(name = "seleccion_evento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeleccionEvento {

    // El admin carga el horario en hora argentina; el servidor puede correr en otra zona
    static final ZoneId ZONA_PARTIDOS = ZoneId.of("America/Argentina/Buenos_Aires");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String codigoInscripcion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipo_local_id", nullable = false)
    private SeleccionEquipo local;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipo_visitante_id", nullable = false)
    private SeleccionEquipo visitante;

    // Inicio del partido en hora argentina: ahí se cierra la inscripción
    @Column(nullable = false)
    private LocalDateTime fechaHora;

    // ABIERTO hasta que se cargan las estadísticas (FINALIZADO). EN_CURSO no se guarda:
    // sale de comparar fechaHora con la hora actual (ver estadoActual)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoShowdown estado;

    private Integer puntosLocal;
    private Integer puntosVisitante;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    protected void onCreate() {
        this.creadoEn = LocalDateTime.now();
        if (this.estado == null) {
            this.estado = EstadoShowdown.ABIERTO;
        }
    }

    public boolean partidoEmpezo() {
        return !fechaHora.isAfter(LocalDateTime.now(ZONA_PARTIDOS));
    }

    public EstadoShowdown estadoActual() {
        if (estado == EstadoShowdown.FINALIZADO) {
            return EstadoShowdown.FINALIZADO;
        }
        return partidoEmpezo() ? EstadoShowdown.EN_CURSO : EstadoShowdown.ABIERTO;
    }
}
