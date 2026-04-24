package com.fantasy.lnb.feature.usuario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface EquipoVirtualRepository extends JpaRepository<EquipoVirtual, Long> {
    Optional<EquipoVirtual> findByUsuario_Id(Long usuarioId);
}