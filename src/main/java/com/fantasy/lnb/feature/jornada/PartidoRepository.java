package com.fantasy.lnb.feature.jornada;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PartidoRepository extends JpaRepository<Partido, Long> {

     List<Partido> findByJornada_Id(Long jornadaId);

     long countByJornada_Id(Long jornadaId);

     // Busca partidos finalizados no procesados para el scraper
     List<Partido> findByEstadoAndEstadisticasProcesadasFalse(
               EstadoPartido estado);

     Optional<Partido> findByGesHash(String gesHash);

     boolean existsByGesHash(String gesHash);

     /**
      * Identidad real de un partido para el scraper de fixture: el hash de
      * GES viene de la URL de la página y NO es estable entre corridas (la
      * LNB lo regenera en cada carga), así que no sirve para detectar si un
      * partido ya existe.
      *
      * Usamos equipo local + visitante + el DÍA del partido (no la hora
      * exacta): la hora puede corregirse entre corridas sin dejar de ser
      * el mismo partido, pero el día sí distingue revanchas entre los
      * mismos dos equipos en series de playoffs.
      */
     Optional<Partido> findByEquipoLocal_IdAndEquipoVisitante_IdAndFechaHoraGreaterThanEqualAndFechaHoraLessThan(
               Long equipoLocalId, Long equipoVisitanteId, LocalDateTime desde, LocalDateTime hasta);

     // Fallback para cuando no se pudo parsear la fecha del partido.
     Optional<Partido> findByEquipoLocal_IdAndEquipoVisitante_Id(
               Long equipoLocalId, Long equipoVisitanteId);

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
     Optional<Partido> findFirstByEquipoLocal_NombreContainingIgnoreCaseAndEstadoOrderByFechaHoraAsc(String nombreLocal, EstadoPartido estado);
}