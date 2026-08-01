package com.fantasy.lnb.feature.usuario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

@Repository
public interface EquipoVirtualRepository extends JpaRepository<EquipoVirtual, Long> {
    Optional<EquipoVirtual> findByUsuario_Id(Long usuarioId);
    List<EquipoVirtual> findByUsuario_IdIn(java.util.Collection<Long> usuarioIds);

    @Query("""
                SELECT ev FROM EquipoVirtual ev
                JOIN FETCH ev.usuario u
                LEFT JOIN FETCH u.equipoFavorito ef
                ORDER BY ev.puntajeGlobal DESC
            """)
    List<EquipoVirtual> findAllOrderByPuntajeGlobalDesc(Pageable pageable);
}
