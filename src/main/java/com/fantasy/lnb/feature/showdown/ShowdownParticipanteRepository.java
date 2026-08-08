package com.fantasy.lnb.feature.showdown;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ShowdownParticipanteRepository extends JpaRepository<ShowdownParticipante, Long> {
    List<ShowdownParticipante> findAllByEventoIdOrderByPuntosTotalesDesc(Long eventoId);
    Optional<ShowdownParticipante> findByEventoIdAndUuidDispositivo(Long eventoId, String uuidDispositivo);
}
