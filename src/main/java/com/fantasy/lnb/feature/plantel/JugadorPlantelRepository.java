package com.fantasy.lnb.feature.plantel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JugadorPlantelRepository extends JpaRepository<JugadorPlantel, Long> {

    // Verifica si un jugador ya está en el plantel del usuario
    // (un jugador no puede aparecer dos veces en el mismo plantel)
    boolean existsByPlantelJornada_IdAndJugadorReal_Id(
            Long plantelJornadaId, Long jugadorRealId);

    // Para calcular cuánto gastó el usuario en jugadores de una posición
    List<JugadorPlantel> findByPlantelJornada_Id(Long plantelJornadaId);

    // Valida exclusividad: Verifica si un jugador ya fue fichado por ALGUIEN en un torneo draft en una jornada
    boolean existsByPlantelJornada_Torneo_IdAndPlantelJornada_Jornada_IdAndJugadorReal_Id(
            Long torneoId, Long jornadaId, Long jugadorRealId);

    @org.springframework.data.jpa.repository.Query("SELECT jp.jugadorReal.id FROM JugadorPlantel jp WHERE jp.plantelJornada.torneo.id = :torneoId AND jp.plantelJornada.jornada.id = :jornadaId")
    List<Long> findJugadorRealIdsByTorneoAndJornada(@org.springframework.data.repository.query.Param("torneoId") Long torneoId, @org.springframework.data.repository.query.Param("jornadaId") Long jornadaId);

    @org.springframework.data.jpa.repository.Query("SELECT jp.jugadorReal.id FROM JugadorPlantel jp WHERE jp.plantelJornada.id IN (SELECT MAX(pj.id) FROM PlantelJornada pj WHERE pj.torneo.id = :torneoId GROUP BY pj.usuario.id)")
    List<Long> findJugadoresOcupadosEnTorneo(@org.springframework.data.repository.query.Param("torneoId") Long torneoId);
}