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

    // fechaHora viene de GES en hora argentina; el servidor puede correr en otra zona
    private static final java.time.ZoneId ZONA_PARTIDOS = java.time.ZoneId.of("America/Argentina/Buenos_Aires");

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

    public boolean partidoEmpezo() {
        LocalDateTime inicio = partido.getFechaHora();
        // 00:00 es un horario sin confirmar en el fixture: el evento no cierra durante ese día
        if (inicio.toLocalTime().equals(java.time.LocalTime.MIDNIGHT)) {
            inicio = inicio.plusDays(1);
        }
        return !inicio.isAfter(LocalDateTime.now(ZONA_PARTIDOS));
    }
}
