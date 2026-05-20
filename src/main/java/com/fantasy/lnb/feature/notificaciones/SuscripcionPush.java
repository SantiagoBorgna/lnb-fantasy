package com.fantasy.lnb.feature.notificaciones;

import com.fantasy.lnb.feature.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "suscripcion_push")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuscripcionPush {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // El endpoint único de Google/Apple para este dispositivo
    @Column(nullable = false, length = 1000)
    private String endpoint;

    // Claves criptográficas necesarias para mandarle el mensaje a ese dispositivo
    @Column(nullable = false)
    private String p256dh;

    @Column(nullable = false)
    private String auth;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
}