package com.fantasy.lnb.feature.torneo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TorneoEquipoRepository extends JpaRepository<TorneoEquipo, Long> {

    // Verifica si un equipo ya está inscripto en un torneo
    boolean existsByTorneo_IdAndEquipoVirtual_Id(
            Long torneoId, Long equipoVirtualId);

    List<TorneoEquipo> findByTorneo_Id(Long torneoId);

    // Tabla de posiciones de un torneo específico
    // Ordenada por puntajeGlobal descendente
    @Query("""
                SELECT te FROM TorneoEquipo te
                JOIN FETCH te.equipoVirtual ev
                JOIN FETCH ev.usuario u
                WHERE te.torneo.id = :torneoId
                ORDER BY ev.puntajeGlobal DESC
            """)
    List<TorneoEquipo> findTablaByTorneoId(@Param("torneoId") Long torneoId);

    // Todos los torneos en los que participa un equipo virtual
    List<TorneoEquipo> findByEquipoVirtual_Id(Long equipoVirtualId);
}