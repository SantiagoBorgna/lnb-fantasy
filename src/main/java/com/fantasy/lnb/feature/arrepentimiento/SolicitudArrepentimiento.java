package com.fantasy.lnb.feature.arrepentimiento;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Pedido de revocación de la suscripción Premium hecho con el "Botón de arrepentimiento"
 * (Res. 424/2020). La cancelación y la devolución en Mercado Pago se hacen a mano.
 */
@Entity
@Table(name = "solicitud_arrepentimiento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolicitudArrepentimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    private LocalDate fechaContratacion;

    @Column(length = 500)
    private String motivo;

    // Si el email es de un usuario registrado
    private Long usuarioId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    private LocalDateTime resueltoEn;

    @PrePersist
    protected void onCreate() {
        this.creadoEn = LocalDateTime.now();
    }

    /** Código que se le da a la persona como constancia del pedido (ej. ARR-000123). */
    public String getCodigo() {
        return codigo(id);
    }

    public static String codigo(Long id) {
        return id == null ? null : String.format("ARR-%06d", id);
    }
}
