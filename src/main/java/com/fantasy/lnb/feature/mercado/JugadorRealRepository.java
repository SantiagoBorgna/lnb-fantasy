package com.fantasy.lnb.feature.mercado;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

@Repository
public interface JugadorRealRepository extends JpaRepository<JugadorReal, Long> {

        // ── Usado por el Scraper para cruzar ID de GES con nuestra BD ──────────
        Optional<JugadorReal> findByGesId(Long gesId);

        // ── Filtros del Mercado (Paso 2) ────────────────────────────────────────

        // Todos los jugadores de una posición (CON SU EQUIPO INCLUIDO)
        @Query("SELECT j FROM JugadorReal j JOIN FETCH j.equipoReal WHERE j.posicion = :posicion")
        List<JugadorReal> findByPosicion(@Param("posicion") PosicionJugador posicion, Sort sort);

        // Trae todos los jugadores EXCEPTO los de la posición (CON SU EQUIPO INCLUIDO)
        @Query("SELECT j FROM JugadorReal j JOIN FETCH j.equipoReal WHERE j.posicion != :posicion")
        List<JugadorReal> findByPosicionNot(@Param("posicion") PosicionJugador posicion, Sort sort);

        // Búsqueda inteligente por nombre de jugador, equipo o sigla (excluyendo una
        // posición)
        @Query("SELECT j FROM JugadorReal j JOIN FETCH j.equipoReal WHERE " +
                        "(LOWER(j.nombreCompleto) LIKE LOWER(CONCAT('%', :termino, '%')) " +
                        "OR LOWER(j.equipoReal.nombre) LIKE LOWER(CONCAT('%', :termino, '%')) " +
                        "OR LOWER(j.equipoReal.sigla) LIKE LOWER(CONCAT('%', :termino, '%'))) " +
                        "AND j.posicion != :posicionExcluida")
        List<JugadorReal> buscarPorJugadorOEquipo(
                        @Param("termino") String termino,
                        @Param("posicionExcluida") PosicionJugador posicionExcluida,
                        Sort sort);

        // Búsqueda inteligente por nombre de jugador, equipo o sigla (CON posición)
        @Query("SELECT j FROM JugadorReal j JOIN FETCH j.equipoReal WHERE " +
                        "(LOWER(j.nombreCompleto) LIKE LOWER(CONCAT('%', :termino, '%')) " +
                        "OR LOWER(j.equipoReal.nombre) LIKE LOWER(CONCAT('%', :termino, '%')) " +
                        "OR LOWER(j.equipoReal.sigla) LIKE LOWER(CONCAT('%', :termino, '%'))) " +
                        "AND j.posicion = :posicionBuscada")
        List<JugadorReal> buscarPorJugadorOEquipoYPosicion(
                        @Param("termino") String termino,
                        @Param("posicionBuscada") PosicionJugador posicionBuscada,
                        Sort sort);

        // Jugadores disponibles de un equipo real (para scouting)
        List<JugadorReal> findByEquipoReal_IdAndEstado(Long equipoId, EstadoJugador estado);

        // ── Query para el algoritmo de precios (Paso 2) ─────────────────────────
        // Trae los puntajes Fantasy de las últimas N jornadas finalizadas
        // de un jugador, ordenados del más reciente al más antiguo.
        @Query("""
                            SELECT e.puntajeFantasyCalculado
                            FROM EstadisticaPartido e
                            WHERE e.jugadorReal.id = :jugadorId
                            ORDER BY e.fechaPartido DESC
                            LIMIT :ultimas
                        """)
        List<Double> findUltimosPuntajes(
                        @Param("jugadorId") Long jugadorId,
                        @Param("ultimas") int ultimas);

        List<JugadorReal> findByEquipoReal_Id(Long equipoId);
}