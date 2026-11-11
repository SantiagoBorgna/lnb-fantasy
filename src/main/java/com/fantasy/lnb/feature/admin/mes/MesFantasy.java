package com.fantasy.lnb.feature.admin.mes;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Agrupación de jornadas que el admin arma a mano para el resumen mensual ("noviembre" = jornadas 5 a 8).
 * Las jornadas se guardan solo por id (sin FK), así borrar una jornada no choca con los meses:
 * el resumen ignora los ids que ya no existen.
 */
@Entity
@Table(name = "mes_fantasy")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MesFantasy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String nombre;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "mes_fantasy_jornada", joinColumns = @JoinColumn(name = "mes_id"))
    @Column(name = "jornada_id", nullable = false)
    @Builder.Default
    private Set<Long> jornadaIds = new HashSet<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    protected void onCreate() {
        this.creadoEn = LocalDateTime.now();
    }
}
