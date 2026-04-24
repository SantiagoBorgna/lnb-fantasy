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
}