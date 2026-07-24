package com.fantasy.lnb.feature.torneo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EnfrentamientoH2HRepository extends JpaRepository<EnfrentamientoH2H, Long> {

    List<EnfrentamientoH2H> findByTorneo_Id(Long torneoId);

    List<EnfrentamientoH2H> findByTorneo_IdAndJornada_Id(Long torneoId, Long jornadaId);

    List<EnfrentamientoH2H> findByJornada_IdAndProcesadoFalse(Long jornadaId);
}
