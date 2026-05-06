package com.fantasy.lnb.feature.jornada;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PartidoRepository extends JpaRepository<Partido, Long> {

        List<Partido> findByJornada_Id(Long jornadaId);

        // Busca partidos finalizados no procesados para el scraper
        List<Partido> findByEstadoAndEstadisticasProcesadasFalse(
                        EstadoPartido estado);

        Optional<Partido> findByGesHash(String gesHash);

        boolean existsByGesHash(String gesHash);

        List<Partido> findByEstado(EstadoPartido estado);

        /**
         * Busca los partidos de una jornada donde participa un equipo dado
         * (como local o visitante). Devuelve una lista porque en básquet
         * un equipo puede jugar múltiples veces en la misma jornada.
         */
        @Query("""
                            SELECT p FROM Partido p
                            WHERE p.jornada.id = :jornadaId
                            AND (p.equipoLocal.id = :equipoId
                                 OR p.equipoVisitante.id = :equipoId)
                            AND p.estado IN ('FINALIZADO', 'PROCESADO')
                        """)
        List<Partido> findByJornadaIdAndEquipoId(
                        @Param("jornadaId") Long jornadaId,
                        @Param("equipoId") Long equipoId);
}