package com.fantasy.lnb.feature.notificaciones;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SuscripcionPushRepository extends JpaRepository<SuscripcionPush, Long> {

    // Buscar si ya guardamos esta suscripción exacta antes
    Optional<SuscripcionPush> findByEndpoint(String endpoint);

    List<SuscripcionPush> findByUsuario_Id(Long usuarioId);

    // Opcional: Borrar todas las suscripciones de un usuario (para un "Cerrar
    // sesión global")
    void deleteByUsuarioId(Long usuarioId);

    // Panel de admin: usuarios distintos con al menos un dispositivo suscripto
    @Query("SELECT COUNT(DISTINCT s.usuario.id) FROM SuscripcionPush s")
    long contarUsuariosConSuscripcion();
}