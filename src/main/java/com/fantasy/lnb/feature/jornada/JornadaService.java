package com.fantasy.lnb.feature.jornada;

import com.fantasy.lnb.feature.jornada.dto.CrearJornadaRequest;
import com.fantasy.lnb.feature.jornada.dto.JornadaDto;
import com.fantasy.lnb.feature.jornada.dto.PartidoDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class JornadaService {

    private final JornadaRepository jornadaRepo;
    private final EstadisticaPartidoRepository estadisticaRepo;
    private final PartidoRepository partidoRepo;

    // ── Consultas ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<JornadaDto> listarTodas() {
        return jornadaRepo.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<JornadaDto> obtenerProximaAbierta() {
        return jornadaRepo
                .findFirstByEstadoOrderByFechaInicioAsc(EstadoJornada.ABIERTA_A_CAMBIOS)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<JornadaDto> obtenerEnJuego() {
        return jornadaRepo
                .findByEstado(EstadoJornada.EN_JUEGO)
                .map(this::toDto);
    }

    public Optional<Jornada> obtenerJornadaEnJuegoEntidad() {
        return jornadaRepo.findByEstado(EstadoJornada.EN_JUEGO);
    }

    @Transactional(readOnly = true)
    public List<PartidoDto> obtenerPartidosDeJornada(Long jornadaId) {
        return partidoRepo.findByJornada_Id(jornadaId).stream()
                // Ordenamos cronológicamente
                .sorted(Comparator.comparing(Partido::getFechaHora))
                .map(p -> PartidoDto.builder()
                        .id(p.getId())
                        .equipoLocal(p.getEquipoLocal().getNombre())
                        .siglaLocal(p.getEquipoLocal().getSigla())
                        .equipoVisitante(p.getEquipoVisitante().getNombre())
                        .siglaVisitante(p.getEquipoVisitante().getSigla())
                        .fechaHora(p.getFechaHora())
                        .estado(p.getEstado().name())
                        .puntosLocal(p.getPuntosLocal())
                        .puntosVisitante(p.getPuntosVisitante())
                        .build())
                .toList();
    }

    // ── Creación (administración del fixture) ───────────────────────────────

    /**
     * Crea una nueva jornada en estado ABIERTA_A_CAMBIOS.
     * En producción este endpoint estará protegido por un rol ADMIN
     * que agregaremos cuando implementemos roles en el Módulo 6.
     */
    public JornadaDto crearJornada(CrearJornadaRequest request) {
        Jornada jornada = Jornada.builder()
                .numero(request.getNumero())
                .fechaInicio(request.getFechaInicio())
                .fechaFin(request.getFechaFin())
                .estado(EstadoJornada.ABIERTA_A_CAMBIOS)
                .build();

        Jornada guardada = jornadaRepo.save(jornada);
        log.info("[JORNADA] Jornada {} creada. Ventana: {} a {}",
                guardada.getNumero(), guardada.getFechaInicio(), guardada.getFechaFin());

        return toDto(guardada);
    }

    // ── Mapper ──────────────────────────────────────────────────────────────

    public JornadaDto toDto(Jornada j) {
        long segundos = 0;
        LocalDateTime ahora = LocalDateTime.now();

        if (j.estaAbierta() && ahora.isBefore(j.getFechaInicio())) {
            segundos = ChronoUnit.SECONDS.between(ahora, j.getFechaInicio());
        }

        return JornadaDto.builder()
                .id(j.getId())
                .numero(j.getNumero())
                .fechaInicio(j.getFechaInicio())
                .fechaFin(j.getFechaFin())
                .estado(j.getEstado())
                .segundosHastaInicio(segundos)
                .build();
    }

    // ── Transiciones de estado ──────────────────────────────────────────────────

    /**
     * ABIERTA_A_CAMBIOS → EN_JUEGO
     * Se llama cuando fechaInicio de la jornada es alcanzada.
     * A partir de este momento el frontend debe bloquear cambios de plantel.
     */
    public void iniciarJornada(Long jornadaId) {
        Jornada jornada = jornadaRepo.findById(jornadaId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Jornada no encontrada: " + jornadaId));

        if (!jornada.estaAbierta()) {
            log.warn("[JORNADA] Intento de iniciar jornada {} en estado incorrecto: {}",
                    jornadaId, jornada.getEstado());
            return;
        }

        jornada.setEstado(EstadoJornada.EN_JUEGO);
        jornadaRepo.save(jornada);
        log.info("[JORNADA] Jornada {} EN_JUEGO", jornada.getNumero());
    }

    /**
     * EN_JUEGO → FINALIZADA
     * Se llama cuando fechaFin de la jornada es superada Y
     * el scraper ya procesó todos los partidos de la ventana.
     * Después de este paso el CronJob de precios puede correr.
     */
    public void finalizarJornada(Long jornadaId) {
        Jornada jornada = jornadaRepo.findById(jornadaId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Jornada no encontrada: " + jornadaId));

        if (!jornada.estaEnJuego()) {
            log.warn("[JORNADA] Intento de finalizar jornada {} en estado incorrecto: {}",
                    jornadaId, jornada.getEstado());
            return;
        }

        jornada.setEstado(EstadoJornada.FINALIZADA);
        jornadaRepo.save(jornada);
        log.info("[JORNADA] Jornada {} FINALIZADA", jornada.getNumero());
    }

    /**
     * Verifica si un partido (identificado por su timestamp) debe ser
     * contabilizado para la jornada activa, aplicando la regla del
     * fixture asimétrico: solo el PRIMER partido cronológico por equipo.
     *
     * @param equipoId      ID del equipo real en nuestra BD
     * @param fechaPartido  Timestamp del partido candidato
     * @param jornadaActiva La jornada EN_JUEGO
     * @return true si el partido debe ser procesado
     */
    public boolean esPartidoContabilizable(
            Long equipoId,
            LocalDateTime fechaPartido,
            Jornada jornadaActiva) {

        // 1 — El timestamp debe caer dentro de la ventana de la jornada
        if (!jornadaActiva.contieneTimestamp(fechaPartido)) {
            log.debug("[FIXTURE] Partido fuera de ventana de jornada {}. Ignorado.",
                    jornadaActiva.getNumero());
            return false;
        }

        // 2 — Verificar si ya existe una estadística de ese equipo en esta jornada
        // (significa que ya procesamos el primer partido — este sería el segundo)
        boolean yaSeProcesoUnPartido = estadisticaRepo
                .existsByEquipoRealIdAndJornadaId(equipoId, jornadaActiva.getId());

        if (yaSeProcesoUnPartido) {
            log.info("[FIXTURE] Equipo {} ya tiene partido procesado en jornada {}." +
                    " Segundo partido ignorado (regla fixture asimétrico).",
                    equipoId, jornadaActiva.getNumero());
            return false;
        }

        return true;
    }
}