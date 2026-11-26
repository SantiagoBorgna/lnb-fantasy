package com.fantasy.lnb.feature.seleccion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SeleccionParticipanteRepository extends JpaRepository<SeleccionParticipante, Long> {
    List<SeleccionParticipante> findAllByEventoIdOrderByPuntosTotalesDesc(Long eventoId);
    Optional<SeleccionParticipante> findByEventoIdAndUuidDispositivo(Long eventoId, String uuidDispositivo);
    Optional<SeleccionParticipante> findFirstByEventoIdAndEmailIgnoreCase(Long eventoId, String email);
    int countByEventoId(Long eventoId);

    @Query("select count(p) > 0 from SeleccionParticipante p where p.base.id = :jugadorId or p.escolta.id = :jugadorId"
            + " or p.alero.id = :jugadorId or p.alaPivot.id = :jugadorId or p.pivot.id = :jugadorId")
    boolean jugadorElegido(Long jugadorId);

    @Query("delete from SeleccionParticipante p where p.evento.id = :eventoId")
    @org.springframework.data.jpa.repository.Modifying
    void borrarDelEvento(Long eventoId);
}
