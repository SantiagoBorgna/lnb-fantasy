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
    @Builder.Default
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer puntos = 0;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer rebotesDefensivos = 0;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer rebotesOfensivos = 0;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer asistencias = 0;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer recuperaciones = 0;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer perdidas = 0;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer faltasCometidas = 0;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer faltasRecibidas = 0;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer tirosCampoFallados = 0;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer tirosLibresFallados = 0;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer taponesRealizados = 0;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer taponesRecibidos = 0;

    // Faltas especiales: GES tampoco las expone directamente.
    // Se derivan por heurística (ver MotorPuntuacion) o se dejan en false.
    @Builder.Default
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean fueExpulsadoPorFaltas = false; // FaltaCometida >= 5

    @Builder.Default
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean tieneFaltaTecnica = false; // No disponible en GES por ahora

    @Builder.Default
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean fueDescalificado = false; // No disponible en GES por ahora

    @Builder.Default
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean fueTitularEnPartidoReal = false; // CincoInicial del DTO

    // --- Resultado del partido (se enriquece al procesar el marcador) ---
    @Builder.Default
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean equipoGano = false;

    // --- Puntaje calculado y persistido ---
    @Builder.Default
    @Column(nullable = false, columnDefinition = "DECIMAL(6,2) DEFAULT 0.00")
    private Double puntajeFantasyCalculado = 0.0;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private LocalDateTime creadoEn = LocalDateTime.now();
}