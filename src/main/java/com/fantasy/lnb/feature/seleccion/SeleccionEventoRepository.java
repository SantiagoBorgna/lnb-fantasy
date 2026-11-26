package com.fantasy.lnb.feature.seleccion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeleccionEventoRepository extends JpaRepository<SeleccionEvento, Long> {
    Optional<SeleccionEvento> findByCodigoInscripcion(String codigoInscripcion);
    List<SeleccionEvento> findAllByOrderByFechaHoraAsc();
    boolean existsByLocalIdOrVisitanteId(Long localId, Long visitanteId);
}
