package com.fantasy.lnb.feature.torneo;

import com.fantasy.lnb.feature.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "torneo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Torneo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoTorneo tipo;

    // UUID único para URLs de invitación seguras
    // Ejemplo: /torneos/unirse/a1b2c3d4-e5f6-...
    // Se genera automáticamente al crear el torneo
    @Column(nullable = false, unique = true, updatable = false)
    private String codigoInvitacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creador_id", nullable = false)
    private Usuario creador;

    // Relación con los equipos participantes
    @OneToMany(mappedBy = "torneo", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TorneoEquipo> participantes = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(nullable = false)
    private LocalDateTime actualizadoEn;

    @PrePersist
    protected void onCreate() {
        // Generamos el UUID aquí como fallback — el Service también lo setea
        if (this.codigoInvitacion == null) {
            this.codigoInvitacion = UUID.randomUUID().toString();
        }
        this.creadoEn = LocalDateTime.now();
        this.actualizadoEn = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.actualizadoEn = LocalDateTime.now();
    }

    // ── Helpers de dominio ──────────────────────────────────────────────────

    public boolean esPublico() {
        return tipo == TipoTorneo.PUBLICO;
    }

    public boolean esPrivado() {
        return tipo == TipoTorneo.PRIVADO;
    }

    public int cantidadParticipantes() {
        return participantes.size();
    }
}