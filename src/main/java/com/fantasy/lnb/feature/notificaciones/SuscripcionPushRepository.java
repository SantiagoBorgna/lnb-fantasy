package com.fantasy.lnb.feature.notificaciones;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SuscripcionPushRepository extends JpaRepository<SuscripcionPush, Long> {

    // Buscar si ya guardamos esta suscripción exacta antes
    Optional<SuscripcionPush> findByEndpoint(String endpoint);

    // Opcional: Borrar todas las suscripciones de un usuario (para un "Cerrar
    // sesión global")
    void deleteByUsuarioId(Long usuarioId);
}