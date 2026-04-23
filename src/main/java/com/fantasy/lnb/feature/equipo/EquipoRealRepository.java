package com.fantasy.lnb.feature.equipo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface EquipoRealRepository extends JpaRepository<EquipoReal, Long> {

    Optional<EquipoReal> findByNombreIgnoreCase(String nombre);
}