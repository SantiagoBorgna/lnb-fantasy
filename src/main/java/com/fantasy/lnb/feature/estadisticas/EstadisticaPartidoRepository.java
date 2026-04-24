package com.fantasy.lnb.feature.estadisticas;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EstadisticaPartidoRepository
                extends JpaRepository<EstadisticaPartido, Long> {

        /**
         * Clave para la regla del primer partido cronológico:
         * antes de persistir, verificamos si ya existe una estadística
         * para este jugador en esta jornada. Si existe, la ignoramos.
         */
        boolean existsByJugadorReal_IdAndJornada_Id(Long jugadorRealId, Long jornadaId);

        /**
         * Útil para el algoritmo de variación de precios (Módulo 3):
         * recupera las últimas N estadísticas de un jugador ordenadas
         * por fecha para calcular el promedio móvil de 3 partidos.
         */
        Optional<EstadisticaPartido> findTopByJugadorReal_IdOrderByFechaPartidoDesc(
                        Long jugadorRealId);

        /**
         * Verifica si ya existe al menos un jugador del equipo dado
         * con estadísticas persistidas en esta jornada.
         * Clave para la regla del fixture asimétrico.
         */
        @Query("""
                        SELECT COUNT(e) > 0
                        FROM EstadisticaPartido e
                        WHERE e.jugadorReal.equipoReal.id = :equipoId
                        AND   e.jornada.id                = :jornadaId
                        """)
        boolean existsByEquipoRealIdAndJornadaId(
                        @Param("equipoId") Long equipoId,
                        @Param("jornadaId") Long jornadaId);
}