package com.fantasy.lnb.feature.jornada;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface JornadaRepository extends JpaRepository<Jornada, Long> {

        // ── Usado por el ScraperCronJob para encontrar la jornada activa ────────
        Optional<Jornada> findByEstado(EstadoJornada estado);

        // ── Usado por el CronJob de transición de estados ───────────────────────
        // Encuentra jornadas cuya ventana ya comenzó pero siguen ABIERTAS
        Optional<Jornada> findByEstadoAndFechaInicioLessThanEqual(
                        EstadoJornada estado,
                        LocalDateTime ahora);

        // Encuentra jornadas cuya ventana ya terminó pero siguen EN_JUEGO
        Optional<Jornada> findByEstadoAndFechaFinLessThan(
                        EstadoJornada estado,
                        LocalDateTime ahora);

        // ── Consulta pública (frontend Dashboard) ───────────────────────────────
        // La próxima jornada abierta para mostrar el countdown
        Optional<Jornada> findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada estado);

    Optional<Jornada> findFirstByEstadoAndFechaInicioLessThanEqual(EstadoJornada estado, LocalDateTime fecha);

    Optional<Jornada> findFirstByEstadoAndFechaInicioLessThanEqualAndNotificacionPreviaEnviadaFalse(EstadoJornada estado, LocalDateTime fecha);

        // Jornada más reciente finalizada (para mostrar resultados)
        Optional<Jornada> findFirstByEstadoOrderByFechaFinDesc(EstadoJornada estado);

        /**
         * Busca la jornada FINALIZADA más reciente.
         * Usada para encontrar el plantel a clonar.
         */
        Optional<Jornada> findFirstByEstadoOrderByNumeroDesc(EstadoJornada estado);

        // Agregá esto al final de las consultas de tipo "findFirst"
        Optional<Jornada> findFirstByEstadoOrderByNumeroAsc(EstadoJornada estado);

        /**
         * Encuentra la jornada más reciente con un estado dado
         * cuyo número sea menor al indicado.
         * Usada para resolver "la jornada finalizada anterior a la abierta".
         */
        Optional<Jornada> findFirstByEstadoAndNumeroLessThanOrderByNumeroDesc(
                        EstadoJornada estado,
                        Integer numero);

        Optional<Jornada> findFirstByEstadoAndNumeroGreaterThanOrderByNumeroAsc(
                        EstadoJornada estado,
                        Integer numero);
}