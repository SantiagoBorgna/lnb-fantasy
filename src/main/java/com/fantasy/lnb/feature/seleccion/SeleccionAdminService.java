package com.fantasy.lnb.feature.seleccion;

import com.fantasy.lnb.feature.seleccion.dto.AdminSeleccionEventoDto;
import com.fantasy.lnb.feature.seleccion.dto.PlanillaSeleccionDto;
import com.fantasy.lnb.feature.seleccion.dto.ResumenSeleccionDto;
import com.fantasy.lnb.feature.seleccion.dto.SeleccionEquipoDto;
import com.fantasy.lnb.feature.seleccion.dto.SeleccionEventoRequest;
import com.fantasy.lnb.feature.seleccion.dto.SeleccionJugadorDto;
import com.fantasy.lnb.feature.showdown.EstadoShowdown;
import com.fantasy.lnb.feature.showdown.dto.AdminShowdownParticipanteDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Admin del Relámpago Selección: selecciones, jugadores, partidos y carga manual de planillas. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeleccionAdminService {

    private final SeleccionEquipoRepository equipoRepo;
    private final SeleccionJugadorRepository jugadorRepo;
    private final SeleccionEventoRepository eventoRepo;
    private final SeleccionParticipanteRepository participanteRepo;
    private final SeleccionEstadisticaRepository estadisticaRepo;

    // ── Selecciones ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SeleccionEquipoDto> getEquipos() {
        return equipoRepo.findAll().stream().map(SeleccionAdminService::aDto).toList();
    }

    @Transactional
    public SeleccionEquipoDto guardarEquipo(Long id, SeleccionEquipoDto dto) {
        SeleccionEquipo equipo = id == null ? new SeleccionEquipo() : buscarEquipo(id);
        equipo.setNombre(dto.nombre().trim());
        equipo.setSigla(dto.sigla().trim().toUpperCase(Locale.ROOT));
        equipo.setColorPrincipal(dto.colorPrincipal());
        equipo.setColorSecundario(dto.colorSecundario());
        equipo.setModeloCamiseta(dto.modeloCamiseta() != null ? dto.modeloCamiseta() : 1);
        return aDto(equipoRepo.save(equipo));
    }

    @Transactional
    public void eliminarEquipo(Long id) {
        if (jugadorRepo.existsByEquipoId(id) || eventoRepo.existsByLocalIdOrVisitanteId(id, id)) {
            throw new IllegalStateException("La selección tiene jugadores o partidos: borralos primero");
        }
        equipoRepo.delete(buscarEquipo(id));
    }

    // ── Jugadores ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SeleccionJugadorDto> getJugadores() {
        return jugadorRepo.findAllByOrderByNombreCompletoAsc().stream().map(SeleccionAdminService::aDto).toList();
    }

    @Transactional
    public SeleccionJugadorDto guardarJugador(Long id, SeleccionJugadorDto dto) {
        SeleccionJugador jugador = id == null ? new SeleccionJugador() : buscarJugador(id);
        if (id != null && !jugador.getEquipo().getId().equals(dto.equipoId()) && estaEnUso(id)) {
            throw new IllegalStateException("No se puede cambiar de selección a un jugador que ya fue elegido o tiene estadísticas");
        }
        jugador.setEquipo(buscarEquipo(dto.equipoId()));
        jugador.setNombreCompleto(dto.nombreCompleto().trim());
        jugador.setNumeroCamiseta(dto.numeroCamiseta());
        jugador.setPosicion(dto.posicion());
        jugador.setValor(dto.valor());
        return aDto(jugadorRepo.save(jugador));
    }

    @Transactional
    public void eliminarJugador(Long id) {
        if (estaEnUso(id)) {
            throw new IllegalStateException("El jugador ya fue elegido en algún equipo o tiene estadísticas: no se puede borrar");
        }
        jugadorRepo.delete(buscarJugador(id));
    }

    // ── Partidos / eventos ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<AdminSeleccionEventoDto> getEventos() {
        return eventoRepo.findAllByOrderByFechaHoraAsc().stream().map(this::aDto).toList();
    }

    @Transactional
    public AdminSeleccionEventoDto crearEvento(SeleccionEventoRequest request) {
        SeleccionEvento evento = SeleccionEvento.builder()
                .codigoInscripcion(UUID.randomUUID().toString().substring(0, 8))
                .estado(EstadoShowdown.ABIERTO)
                .mostrarBanner(true)
                .build();
        aplicar(evento, request);
        return aDto(eventoRepo.save(evento));
    }

    /** Para corregir el horario o los equipos (ej. si se posterga el partido). */
    @Transactional
    public AdminSeleccionEventoDto editarEvento(Long id, SeleccionEventoRequest request) {
        SeleccionEvento evento = buscarEvento(id);
        boolean cambianEquipos = !evento.getLocal().getId().equals(request.localId())
                || !evento.getVisitante().getId().equals(request.visitanteId());
        if (cambianEquipos && participanteRepo.countByEventoId(id) > 0) {
            throw new IllegalStateException("Ya hay inscriptos: no se pueden cambiar los equipos del partido");
        }
        aplicar(evento, request);
        return aDto(eventoRepo.save(evento));
    }

    /** Prende o apaga el banner del inicio para este partido (no toca inscriptos ni resultados). */
    @Transactional
    public AdminSeleccionEventoDto cambiarBanner(Long id, boolean mostrar) {
        SeleccionEvento evento = buscarEvento(id);
        evento.setMostrarBanner(mostrar);
        return aDto(eventoRepo.save(evento));
    }

    @Transactional
    public void eliminarEvento(Long id) {
        SeleccionEvento evento = buscarEvento(id);
        estadisticaRepo.borrarDelEvento(id);
        participanteRepo.borrarDelEvento(id);
        eventoRepo.delete(evento);
    }

    @Transactional(readOnly = true)
    public List<AdminShowdownParticipanteDto> getParticipantes(Long eventoId) {
        buscarEvento(eventoId);
        return participanteRepo.findAllByEventoIdOrderByPuntosTotalesDescIdAsc(eventoId).stream()
                .map(p -> AdminShowdownParticipanteDto.builder()
                        .id(p.getId())
                        .nombre(p.getNombre())
                        .apellido(p.getApellido())
                        .email(p.getEmail())
                        .puntosTotales(p.getPuntosTotales())
                        .build())
                .toList();
    }

    // ── Planilla ─────────────────────────────────────────────────────────────

    /** Todos los jugadores de los dos equipos, con lo ya cargado (o en cero). */
    @Transactional(readOnly = true)
    public PlanillaSeleccionDto getPlanilla(Long eventoId) {
        SeleccionEvento evento = buscarEvento(eventoId);
        Map<Long, SeleccionEstadistica> cargadas = SeleccionPuntuacion.porJugador(estadisticaRepo.findAllByEventoId(eventoId));

        List<PlanillaSeleccionDto.Fila> filas = new ArrayList<>();
        for (SeleccionEquipo equipo : List.of(evento.getLocal(), evento.getVisitante())) {
            for (SeleccionJugador j : jugadorRepo.findAllByEquipoIdInOrderByNombreCompletoAsc(List.of(equipo.getId()))) {
                filas.add(aFila(j, equipo == evento.getLocal(), cargadas.get(j.getId())));
            }
        }
        return new PlanillaSeleccionDto(evento.getPuntosLocal(), evento.getPuntosVisitante(), filas);
    }

    /**
     * Guarda resultado y planilla (reemplaza la anterior), calcula los puntajes, recalcula
     * el ranking y deja el evento FINALIZADO. Se puede volver a guardar para corregir.
     */
    @Transactional
    public PlanillaSeleccionDto guardarPlanilla(Long eventoId, PlanillaSeleccionDto planilla) {
        SeleccionEvento evento = buscarEvento(eventoId);
        if (!evento.partidoEmpezo()) {
            throw new IllegalStateException("El partido todavía no empezó");
        }

        Set<Long> equipos = Set.of(evento.getLocal().getId(), evento.getVisitante().getId());
        Set<Long> vistos = new HashSet<>();
        List<SeleccionEstadistica> nuevas = new ArrayList<>();
        for (PlanillaSeleccionDto.Fila fila : planilla.jugadores()) {
            if (!vistos.add(fila.jugadorId())) {
                throw new IllegalArgumentException("Hay un jugador repetido en la planilla");
            }
            SeleccionJugador jugador = buscarJugador(fila.jugadorId());
            if (!equipos.contains(jugador.getEquipo().getId())) {
                throw new IllegalArgumentException(jugador.getNombreCompleto() + " no juega este partido");
            }
            nuevas.add(aEstadistica(evento, jugador, fila));
        }

        evento.setPuntosLocal(planilla.puntosLocal());
        evento.setPuntosVisitante(planilla.puntosVisitante());
        SeleccionPuntuacion.calcular(evento, nuevas);

        estadisticaRepo.borrarDelEvento(eventoId);
        estadisticaRepo.saveAll(nuevas);

        Map<Long, Double> puntajes = SeleccionPuntuacion.puntajePorJugador(nuevas);
        List<SeleccionParticipante> participantes = participanteRepo.findAllByEventoIdOrderByPuntosTotalesDescIdAsc(eventoId);
        participantes.forEach(p -> p.setPuntosTotales(SeleccionPuntuacion.puntosDe(p, puntajes)));
        participanteRepo.saveAll(participantes);

        evento.setEstado(EstadoShowdown.FINALIZADO);
        eventoRepo.save(evento);
        log.info("[SELECCION] Planilla de {} vs {} guardada: {} jugadores, {} participantes",
                evento.getLocal().getSigla(), evento.getVisitante().getSigla(), nuevas.size(), participantes.size());

        return getPlanilla(eventoId);
    }

    // ── Historias del resultado ──────────────────────────────────────────────

    /** Ganador(es) y quinteto ideal del partido, para exportar las historias. Solo con la planilla cargada. */
    @Transactional(readOnly = true)
    public ResumenSeleccionDto getResumen(Long eventoId) {
        SeleccionEvento evento = buscarEvento(eventoId);
        if (evento.getEstado() != EstadoShowdown.FINALIZADO) {
            throw new IllegalStateException("Todavía no está cargada la planilla de este partido");
        }
        List<SeleccionEstadistica> planillas = estadisticaRepo.findAllByEventoId(eventoId);
        Map<Long, Double> puntajes = SeleccionPuntuacion.puntajePorJugador(planillas);

        // Los primeros del ranking (con empate son varios)
        List<SeleccionParticipante> ranking = participanteRepo.findAllByEventoIdOrderByPuntosTotalesDescIdAsc(eventoId);
        List<ResumenSeleccionDto.Ganador> ganadores = ranking.stream()
                .filter(p -> p.getPuntosTotales().equals(ranking.get(0).getPuntosTotales()))
                .map(p -> new ResumenSeleccionDto.Ganador(p.getId(), p.getNombre(), p.getApellido(), p.getPuntosTotales(),
                        p.quinteto().stream().map(j -> {
                            boolean capitan = j.getId().equals(p.getCapitanId());
                            double pts = puntajes.getOrDefault(j.getId(), 0.0);
                            return aJugadorHistoria(j, capitan ? pts * SeleccionPuntuacion.MULTIPLICADOR_CAPITAN : pts, capitan);
                        }).toList()))
                .toList();

        // Quinteto ideal: el mejor puntaje de cada posición entre los que jugaron
        List<ResumenSeleccionDto.Jugador> ideal = new ArrayList<>();
        for (com.fantasy.lnb.feature.mercado.PosicionJugador posicion : List.of(
                com.fantasy.lnb.feature.mercado.PosicionJugador.BASE, com.fantasy.lnb.feature.mercado.PosicionJugador.ESCOLTA,
                com.fantasy.lnb.feature.mercado.PosicionJugador.ALERO, com.fantasy.lnb.feature.mercado.PosicionJugador.ALA_PIVOT,
                com.fantasy.lnb.feature.mercado.PosicionJugador.PIVOT)) {
            planillas.stream()
                    .filter(e -> e.getJugador().getPosicion() == posicion)
                    .max(java.util.Comparator.comparing(SeleccionEstadistica::getPuntajeFantasy)
                            .thenComparing(SeleccionEstadistica::getValoracion))
                    .ifPresent(e -> ideal.add(aJugadorHistoria(e.getJugador(), e.getPuntajeFantasy(), false)));
        }

        return new ResumenSeleccionDto(evento.getLocal().getNombre(), evento.getVisitante().getNombre(),
                evento.getPuntosLocal(), evento.getPuntosVisitante(), ganadores, ideal);
    }

    private static ResumenSeleccionDto.Jugador aJugadorHistoria(SeleccionJugador j, double puntos, boolean capitan) {
        SeleccionEquipo e = j.getEquipo();
        return new ResumenSeleccionDto.Jugador(j.getNombreCompleto(), j.getNumeroCamiseta(), j.getPosicion().name(),
                e.getSigla(), e.getColorPrincipal(), e.getColorSecundario(), e.getModeloCamiseta(),
                Math.round(puntos * 100.0) / 100.0, capitan);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean estaEnUso(Long jugadorId) {
        return participanteRepo.jugadorElegido(jugadorId) || estadisticaRepo.existsByJugadorId(jugadorId);
    }

    private void aplicar(SeleccionEvento evento, SeleccionEventoRequest request) {
        if (request.localId().equals(request.visitanteId())) {
            throw new IllegalArgumentException("Local y visitante tienen que ser distintos");
        }
        evento.setLocal(buscarEquipo(request.localId()));
        evento.setVisitante(buscarEquipo(request.visitanteId()));
        evento.setFechaHora(request.fechaHora().withSecond(0).withNano(0));
    }

    private SeleccionEquipo buscarEquipo(Long id) {
        return equipoRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Selección no encontrada"));
    }

    private SeleccionJugador buscarJugador(Long id) {
        return jugadorRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Jugador no encontrado"));
    }

    private SeleccionEvento buscarEvento(Long id) {
        return eventoRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Evento no encontrado"));
    }

    private static int n(Integer valor) {
        return valor != null ? valor : 0;
    }

    private static SeleccionEstadistica aEstadistica(SeleccionEvento evento, SeleccionJugador jugador, PlanillaSeleccionDto.Fila f) {
        return SeleccionEstadistica.builder()
                .evento(evento)
                .jugador(jugador)
                .titular(Boolean.TRUE.equals(f.titular()))
                .puntos(n(f.puntos()))
                .tirosCampoFallados(n(f.tirosCampoFallados()))
                .tirosLibresFallados(n(f.tirosLibresFallados()))
                .rebotesOfensivos(n(f.rebotesOfensivos()))
                .rebotesDefensivos(n(f.rebotesDefensivos()))
                .asistencias(n(f.asistencias()))
                .recuperaciones(n(f.recuperaciones()))
                .perdidas(n(f.perdidas()))
                .taponesRealizados(n(f.taponesRealizados()))
                .taponesRecibidos(n(f.taponesRecibidos()))
                .faltasCometidas(n(f.faltasCometidas()))
                .faltasRecibidas(n(f.faltasRecibidas()))
                .valoracionManual(f.valoracionManual())
                .build();
    }

    private static PlanillaSeleccionDto.Fila aFila(SeleccionJugador j, boolean esLocal, SeleccionEstadistica e) {
        return new PlanillaSeleccionDto.Fila(
                j.getId(), j.getNombreCompleto(), j.getNumeroCamiseta(), j.getEquipo().getSigla(), esLocal,
                e != null && e.getTitular(),
                e != null ? e.getPuntos() : 0,
                e != null ? e.getTirosCampoFallados() : 0,
                e != null ? e.getTirosLibresFallados() : 0,
                e != null ? e.getRebotesOfensivos() : 0,
                e != null ? e.getRebotesDefensivos() : 0,
                e != null ? e.getAsistencias() : 0,
                e != null ? e.getRecuperaciones() : 0,
                e != null ? e.getPerdidas() : 0,
                e != null ? e.getTaponesRealizados() : 0,
                e != null ? e.getTaponesRecibidos() : 0,
                e != null ? e.getFaltasCometidas() : 0,
                e != null ? e.getFaltasRecibidas() : 0,
                e != null ? e.getValoracionManual() : null,
                e != null ? e.getValoracion() : null,
                e != null && e.getFueMvp(),
                e != null ? e.getPuntajeFantasy() : null);
    }

    private static SeleccionEquipoDto aDto(SeleccionEquipo e) {
        return new SeleccionEquipoDto(e.getId(), e.getNombre(), e.getSigla(), e.getColorPrincipal(),
                e.getColorSecundario(), e.getModeloCamiseta());
    }

    private static SeleccionJugadorDto aDto(SeleccionJugador j) {
        return new SeleccionJugadorDto(j.getId(), j.getEquipo().getId(), j.getEquipo().getSigla(),
                j.getNombreCompleto(), j.getNumeroCamiseta(), j.getPosicion(), j.getValor());
    }

    private AdminSeleccionEventoDto aDto(SeleccionEvento e) {
        return new AdminSeleccionEventoDto(
                e.getId(), e.getCodigoInscripcion(), e.estadoActual(),
                e.getLocal().getId(), e.getLocal().getSigla(), e.getLocal().getNombre(),
                e.getVisitante().getId(), e.getVisitante().getSigla(), e.getVisitante().getNombre(),
                e.getFechaHora().toString(), e.getPuntosLocal(), e.getPuntosVisitante(),
                e.getId() != null ? participanteRepo.countByEventoId(e.getId()) : 0,
                e.bannerVisible());
    }
}
