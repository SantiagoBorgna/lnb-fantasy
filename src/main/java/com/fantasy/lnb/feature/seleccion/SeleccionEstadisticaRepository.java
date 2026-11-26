package com.fantasy.lnb.feature.seleccion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SeleccionEstadisticaRepository extends JpaRepository<SeleccionEstadistica, Long> {
    List<SeleccionEstadistica> findAllByEventoId(Long eventoId);

    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("delete from SeleccionEstadistica e where e.evento.id = :eventoId")
    void borrarDelEvento(Long eventoId);

    boolean existsByJugadorId(Long jugadorId);
}
