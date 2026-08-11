package com.fantasy.lnb.feature.showdown;

import com.fantasy.lnb.feature.jornada.Partido;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "showdown_evento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShowdownEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // El partido real asociado a este evento
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partido_id", nullable = false)
    private Partido partido;

    // Código único para compartir en el estadio (ej. "oliva-ferro")
    @Column(nullable = false, unique = true)
    private String codigoInscripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoShowdown estado;

    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<ShowdownParticipante> participantes = new java.util.ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    protected void onCreate() {
        this.creadoEn = LocalDateTime.now();
        if (this.estado == null) {
            this.estado = EstadoShowdown.ABIERTO;
        }
    }
}
