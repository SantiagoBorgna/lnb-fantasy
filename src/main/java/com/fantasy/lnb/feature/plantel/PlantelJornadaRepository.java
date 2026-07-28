package com.fantasy.lnb.feature.plantel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlantelJornadaRepository extends JpaRepository<PlantelJornada, Long> {

        // Búsqueda del plantel Global (sin torneo)
        Optional<PlantelJornada> findByUsuario_IdAndJornada_IdAndTorneoIsNull(
                        Long usuarioId, Long jornadaId);

        // Verifica si el usuario armó su plantel global
        boolean existsByUsuario_IdAndJornada_IdAndTorneoIsNull(Long usuarioId, Long jornadaId);

        // Búsqueda del plantel Draft (con torneo)
        Optional<PlantelJornada> findByUsuario_IdAndJornada_IdAndTorneo_Id(
                        Long usuarioId, Long jornadaId, Long torneoId);

        // Verifica si el usuario armó su plantel draft
        boolean existsByUsuario_IdAndJornada_IdAndTorneo_Id(Long usuarioId, Long jornadaId, Long torneoId);

        // Verifica exclusividad del DT en el torneo
        boolean existsByTorneo_IdAndJornada_IdAndDt_Id(Long torneoId, Long jornadaId, Long dtId);

        // Busca todos los planteles de un torneo en una jornada
        java.util.List<PlantelJornada> findByTorneo_IdAndJornada_Id(Long torneoId, Long jornadaId);

        // Busca todos los planteles globales en una jornada
        java.util.List<PlantelJornada> findByJornada_IdAndTorneoIsNull(Long jornadaId);

        // Para el motor de puntuación (Paso 4):
        // trae todos los planteles de una jornada para calcular puntajes en batch
        @Query("""
                            SELECT p FROM PlantelJornada p
                            JOIN FETCH p.jugadores j
                            JOIN FETCH j.jugadorReal
                            WHERE p.jornada.id = :jornadaId
                        """)
        java.util.List<PlantelJornada> findAllByJornadaIdWithJugadores(
                        @Param("jornadaId") Long jornadaId);

}