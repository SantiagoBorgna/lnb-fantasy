package com.fantasy.lnb.feature.showdown;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ShowdownParticipanteRepository extends JpaRepository<ShowdownParticipante, Long> {
    // A igualdad de puntos, por orden de inscripción (el id es el orden de alta)
    List<ShowdownParticipante> findAllByEventoIdOrderByPuntosTotalesDescIdAsc(Long eventoId);
    Optional<ShowdownParticipante> findByEventoIdAndUuidDispositivo(Long eventoId, String uuidDispositivo);
    Optional<ShowdownParticipante> findFirstByEventoIdAndEmailIgnoreCase(Long eventoId, String email);
    int countByEventoId(Long eventoId);
}
