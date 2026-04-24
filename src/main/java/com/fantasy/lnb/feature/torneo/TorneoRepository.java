package com.fantasy.lnb.feature.torneo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TorneoRepository extends JpaRepository<Torneo, Long> {

    // Buscador de torneos públicos — para la pantalla de Torneos del frontend
    List<Torneo> findByTipoOrderByNombreAsc(TipoTorneo tipo);

    // Búsqueda por nombre parcial dentro de los públicos
    List<Torneo> findByTipoAndNombreContainingIgnoreCase(
            TipoTorneo tipo, String nombre);

    // Unirse a torneo privado por UUID
    Optional<Torneo> findByCodigoInvitacion(String codigoInvitacion);

    // Torneos creados por un usuario
    List<Torneo> findByCreador_Id(Long creadorId);
}