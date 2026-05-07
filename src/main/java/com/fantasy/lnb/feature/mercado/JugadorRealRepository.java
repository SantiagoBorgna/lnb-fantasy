package com.fantasy.lnb.feature.mercado;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JugadorRealRepository extends JpaRepository<JugadorReal, Long> {

        // ── Usado por el Scraper para cruzar ID de GES con nuestra BD ──────────
        Optional<JugadorReal> findByGesId(Long gesId);

        // ── Filtros del Mercado (Paso 2) ────────────────────────────────────────

        // Todos los jugadores de una posición, ordenados por precio descendente
        List<JugadorReal> findByPosicionOrderByValorMercadoActualDesc(
                        PosicionJugador posicion);

        // Trae todos los jugadores EXCEPTO los de la posición indicada
        List<JugadorReal> findByPosicionNot(PosicionJugador posicion);

        // NUEVO: Búsqueda inteligente por nombre de jugador, equipo o sigla (excluyendo
        // una posición)
        @Query("SELECT j FROM JugadorReal j WHERE " +
                        "(LOWER(j.nombreCompleto) LIKE LOWER(CONCAT('%', :termino, '%')) " +
                        "OR LOWER(j.equipoReal.nombre) LIKE LOWER(CONCAT('%', :termino, '%')) " +
                        "OR LOWER(j.equipoReal.sigla) LIKE LOWER(CONCAT('%', :termino, '%'))) " +
                        "AND j.posicion != :posicionExcluida")
        List<JugadorReal> buscarPorJugadorOEquipo(
                        @Param("termino") String termino,
                        @Param("posicionExcluida") PosicionJugador posicionExcluida);

        // Agregá esta nueva query justo abajo de la otra que hicimos antes:
        @Query("SELECT j FROM JugadorReal j WHERE " +
                        "(LOWER(j.nombreCompleto) LIKE LOWER(CONCAT('%', :termino, '%')) " +
                        "OR LOWER(j.equipoReal.nombre) LIKE LOWER(CONCAT('%', :termino, '%')) " +
                        "OR LOWER(j.equipoReal.sigla) LIKE LOWER(CONCAT('%', :termino, '%'))) " +
                        "AND j.posicion = :posicionBuscada")
        List<JugadorReal> buscarPorJugadorOEquipoYPosicion(
                        @Param("termino") String termino,
                        @Param("posicionBuscada") PosicionJugador posicionBuscada);

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