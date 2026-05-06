package com.fantasy.lnb.feature.usuario;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

import com.fantasy.lnb.feature.equipo.EquipoReal;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String nombreDisplay;

    // "google" o "microsoft" — identifica el proveedor OAuth2
    @Column(nullable = false)
    private String provider;

    // ID único que nos da el proveedor (el "sub" de Google, el "oid" de Azure)
    @Column(nullable = false)
    private String providerId;

    // URL del avatar provisto por Google/Microsoft (opcional)
    private String avatarUrl;

    @Column(nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(nullable = false)
    private LocalDateTime ultimoLogin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipo_favorito_id")
    private EquipoReal equipoFavorito;

    /**
     * Estado del onboarding del usuario.
     * NUEVO → acaba de registrarse, no completó el perfil
     * PERFIL_COMPLETO → eligió equipo favorito y nombre de equipo virtual
     * ACTIVO → completó el onboarding completo (armó su primer plantel)
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoOnboarding estadoOnboarding;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RolUsuario rol;

    @PrePersist
    protected void onCreate() {
        this.creadoEn = LocalDateTime.now();
        this.ultimoLogin = LocalDateTime.now();
        this.estadoOnboarding = EstadoOnboarding.NUEVO;
        this.rol = RolUsuario.USER; // ← todo usuario empieza como USER
    }

    @PreUpdate
    protected void onUpdate() {
        this.ultimoLogin = LocalDateTime.now();
    }
}