package com.fantasy.lnb.feature.seleccion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SeleccionJugadorRepository extends JpaRepository<SeleccionJugador, Long> {
    List<SeleccionJugador> findAllByEquipoIdInOrderByNombreCompletoAsc(Collection<Long> equipoIds);
    List<SeleccionJugador> findAllByOrderByNombreCompletoAsc();
    boolean existsByEquipoId(Long equipoId);
}
