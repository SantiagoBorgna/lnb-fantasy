package com.fantasy.lnb.feature.plantel;

import com.fantasy.lnb.feature.dt.DirectorTecnico;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.dt.DirectorTecnico;
import com.fantasy.lnb.feature.mercado.JugadorReal;
import com.fantasy.lnb.feature.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "plantel_jornada", uniqueConstraints = @UniqueConstraint(name = "uk_equipo_jornada_torneo", columnNames = {
        "usuario_id", "jornada_id", "torneo_id" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlantelJornada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "torneo_id")
    private com.fantasy.lnb.feature.torneo.Torneo torneo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "jornada_id", nullable = false)
    private Jornada jornada;

    // Director Técnico — relación separada de JugadorPlantel
    // porque tiene lógica de puntuación propia (Módulo 5 Paso 4)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dt_id")
    private DirectorTecnico dt;

    // Formación activa — validada antes de guardar
    // Formato: "1-2-2", "1-3-1", "2-1-2", "2-2-1", "3-1-1"
    @Column(length = 10)
    private String formacion;

    // Puntaje acumulado de esta jornada con multiplicadores aplicados
    @Column(nullable = false, columnDefinition = "DECIMAL(8,2) DEFAULT 0.00")
    private Double puntajeObtenidoFecha;

    // Transferencias usadas en esta jornada (máximo 3)
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer transferenciasUsadas;

    // Jugadores del plantel con sus roles
    @OneToMany(mappedBy = "plantelJornada", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<JugadorPlantel> jugadores = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(nullable = false)
    private LocalDateTime actualizadoEn;

    @PrePersist
    protected void onCreate() {
        this.creadoEn = LocalDateTime.now();
        this.actualizadoEn = LocalDateTime.now();
        this.puntajeObtenidoFecha = 0.0;
        this.transferenciasUsadas = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        this.actualizadoEn = LocalDateTime.now();
    }

    // ── Helpers de dominio ──────────────────────────────────────────────────

    public boolean puedeHacerTransferencia() {
        if (usuario != null && usuario.isPremium()) return true;
        int limite = torneo != null ? 4 : 3;
        return transferenciasUsadas < limite;
    }

    public int transferenciasRestantes() {
        if (usuario != null && usuario.isPremium()) return 99; // infinito simbólico
        int limite = torneo != null ? 4 : 3;
        return Math.max(0, limite - transferenciasUsadas);
    }

    /** Devuelve solo los titulares + capitán (los 5 que suman x1.0 o x1.5) */
    public List<JugadorPlantel> getTitulares() {
        return jugadores.stream()
                .filter(j -> j.getRol() == RolPlantel.TITULAR
                        || j.getRol() == RolPlantel.CAPITAN)
                .toList();
    }

    /** Devuelve el banco completo (sexto hombre + 4 suplentes) */
    public List<JugadorPlantel> getBanco() {
        return jugadores.stream()
                .filter(j -> j.getRol() == RolPlantel.SEXTO_HOMBRE
                        || j.getRol() == RolPlantel.SUPLENTE)
                .toList();
    }
}