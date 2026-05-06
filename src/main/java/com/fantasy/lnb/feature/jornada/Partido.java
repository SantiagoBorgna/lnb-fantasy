package com.fantasy.lnb.feature.jornada;

import com.fantasy.lnb.feature.equipo.EquipoReal;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "partido")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Partido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jornada_id", nullable = false)
    private Jornada jornada;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipo_local_id", nullable = false)
    private EquipoReal equipoLocal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipo_visitante_id", nullable = false)
    private EquipoReal equipoVisitante;

    // Hash único de GES — la URL del partido
    @Column(nullable = false, unique = true)
    private String gesHash;

    // URL completa del partido en GES
    @Column(nullable = false, length = 500)
    private String gesUrl;

    @Column(nullable = false)
    private LocalDateTime fechaHora;

    // Estado del partido
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPartido estado;

    // Resultado (se llena cuando el partido termina)
    private Integer puntosLocal;
    private Integer puntosVisitante;

    // true una vez que el scraper procesó las estadísticas
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean estadisticasProcesadas;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    protected void onCreate() {
        this.creadoEn = LocalDateTime.now();
        this.estadisticasProcesadas = false;
    }

    public boolean localGano() {
        if (puntosLocal == null || puntosVisitante == null)
            return false;
        return puntosLocal > puntosVisitante;
    }
}