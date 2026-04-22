package com.fantasy.lnb.feature.estadisticas;

import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.mercado.JugadorReal;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "estadistica_partido", uniqueConstraints = @UniqueConstraint(name = "uk_jugador_jornada", columnNames = {
        "jugador_real_id", "jornada_id" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstadisticaPartido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- Relaciones (Stubs: solo necesitan existir como @Entity con @Id) ---
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jugador_real_id", nullable = false)
    private JugadorReal jugadorReal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jornada_id", nullable = false)
    private Jornada jornada;

    // --- Metadato del partido ---
    @Column(nullable = false)
    private String gesPartidoId;

    @Column(nullable = false)
    private LocalDateTime fechaPartido;

    private Boolean fueLocal;

    // --- Estadísticas brutas (lo que scrapeamos) ---
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer puntos;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer rebotesDefensivos;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer rebotesOfensivos;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer asistencias;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer recuperaciones;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer perdidas;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer faltasCometidas;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer faltasRecibidas;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer tirosCampoFallados;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer tirosLibresFallados;

    // Tapones: GES NO los expone en este endpoint.
    // Se persisten en 0 hasta encontrar fuente alternativa.
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer taponesRealizados;

    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer taponesRecibidos;

    // Faltas especiales: GES tampoco las expone directamente.
    // Se derivan por heurística (ver MotorPuntuacion) o se dejan en false.
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean fueExpulsadoPorFaltas; // FaltaCometida >= 5

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean tieneFaltaTecnica; // No disponible en GES por ahora

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean fueDescalificado; // No disponible en GES por ahora

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean fueTitularEnPartidoReal; // CincoInicial del DTO

    // --- Resultado del partido (se enriquece al procesar el marcador) ---
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean equipoGano;

    // --- Puntaje calculado y persistido ---
    @Column(nullable = false, columnDefinition = "DECIMAL(6,2) DEFAULT 0.00")
    private Double puntajeFantasyCalculado;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();
}