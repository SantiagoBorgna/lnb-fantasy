package com.fantasy.lnb.feature.mercado;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WaiverClaimRepository extends JpaRepository<WaiverClaim, Long> {

    List<WaiverClaim> findByTorneo_IdAndJornada_IdAndEstado(Long torneoId, Long jornadaId, EstadoClaim estado);

    List<WaiverClaim> findByUsuario_IdAndTorneo_IdAndEstado(Long usuarioId, Long torneoId, EstadoClaim estado);

    List<WaiverClaim> findByJornada_IdAndEstado(Long jornadaId, EstadoClaim estado);

    // Contar cuántos reclamos tiene un usuario en una jornada
    long countByUsuario_IdAndTorneo_IdAndJornada_IdAndEstado(Long usuarioId, Long torneoId, Long jornadaId, EstadoClaim estado);
    
    long countByUsuario_IdAndTorneo_IdAndJornada_Id(Long usuarioId, Long torneoId, Long jornadaId);

    List<WaiverClaim> findByUsuario_IdAndTorneo_IdAndJornada_IdOrderByCreadoEnAsc(Long usuarioId, Long torneoId, Long jornadaId);
}
