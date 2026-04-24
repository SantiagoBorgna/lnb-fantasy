package com.fantasy.lnb.feature.plantel;

import com.fantasy.lnb.feature.mercado.JugadorReal;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "jugador_plantel", uniqueConstraints = @UniqueConstraint(name = "uk_jugador_en_plantel", columnNames = {
        "plantel_jornada_id", "jugador_real_id" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JugadorPlantel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plantel_jornada_id", nullable = false)
    private PlantelJornada plantelJornada;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jugador_real_id", nullable = false)
    private JugadorReal jugadorReal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RolPlantel rol;

    // Precio al que fue comprado — se congela en el momento de la compra.
    // Si el jugador sube de precio después, el mánager no paga más ni menos.
    @Column(nullable = false)
    private Double precioDeCompra;

    // ── Helper de dominio ───────────────────────────────────────────────────

    /** Devuelve el multiplicador de puntaje según el rol del PRD */
    public double getMultiplicador() {
        return switch (rol) {
            case CAPITAN -> 1.5;
            case TITULAR -> 1.0;
            case SEXTO_HOMBRE -> 0.75;
            case SUPLENTE -> 0.5;
        };
    }
}