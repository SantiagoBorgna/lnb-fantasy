package com.fantasy.lnb.feature.plantel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlantelJornadaRepository extends JpaRepository<PlantelJornada, Long> {

        // Búsqueda principal: el plantel de un usuario en una jornada específica
        Optional<PlantelJornada> findByUsuario_IdAndJornada_Id(
                        Long usuarioId, Long jornadaId);

        // Verifica si el usuario ya armó su plantel para la jornada activa
        boolean existsByUsuario_IdAndJornada_Id(Long usuarioId, Long jornadaId);

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