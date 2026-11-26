package com.fantasy.lnb.feature.seleccion;

import com.fantasy.lnb.feature.mercado.PosicionJugador;
import com.fantasy.lnb.feature.seleccion.dto.SeleccionMercadoJugadorDto;
import com.fantasy.lnb.feature.showdown.EstadoShowdown;
import com.fantasy.lnb.feature.showdown.dto.ParticiparShowdownRequest;
import com.fantasy.lnb.feature.showdown.dto.ShowdownEventoDto;
import com.fantasy.lnb.feature.showdown.dto.ShowdownJugadorStatsDto;
import com.fantasy.lnb.feature.showdown.dto.ShowdownMiEquipoDto;
import com.fantasy.lnb.feature.showdown.dto.ShowdownParticipanteDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Lado público del Relámpago Selección. Responde con los mismos DTOs que el Relámpago
 * de la liga para que el frontend use las mismas pantallas.
 */
@Service
@RequiredArgsConstructor
public class SeleccionService {

    static final double PRESUPUESTO = 50.0;

    private final SeleccionEventoRepository eventoRepo;
    private final SeleccionJugadorRepository jugadorRepo;
    private final SeleccionParticipanteRepository participanteRepo;
    private final SeleccionEstadisticaRepository estadisticaRepo;

    /** El próximo evento con la inscripción abierta, para el banner del juego. */
    @Transactional(readOnly = true)
    public Optional<ShowdownEventoDto> getEventoActivo() {
        return eventoRepo.findAllByOrderByFechaHoraAsc().stream()
                .filter(e -> e.estadoActual() == EstadoShowdown.ABIERTO)
                .findFirst()
                .map(this::aDto);
    }

    @Transactional(readOnly = true)
    public ShowdownEventoDto getEvento(String codigo) {
        return aDto(buscar(codigo));
    }

    @Transactional(readOnly = true)
    public List<SeleccionMercadoJugadorDto> getMercado(String codigo) {
        SeleccionEvento evento = buscar(codigo);
        return jugadorRepo.findAllByEquipoIdInOrderByNombreCompletoAsc(
                        List.of(evento.getLocal().getId(), evento.getVisitante().getId()))
                .stream()
                .sorted(Comparator.comparing(SeleccionJugador::getValor).reversed())
                .map(SeleccionService::aMercadoDto)
                .toList();
    }

    @Transactional
    public Long participar(String codigo, ParticiparShowdownRequest request) {
        SeleccionEvento evento = buscar(codigo);

        String email = validarInscripcion(evento, request.getEmail(), request.getUuidDispositivo());

        SeleccionJugador base = jugadorDelPartido(evento, request.getBaseId(), PosicionJugador.BASE);
        SeleccionJugador escolta = jugadorDelPartido(evento, request.getEscoltaId(), PosicionJugador.ESCOLTA);
        SeleccionJugador alero = jugadorDelPartido(evento, request.getAleroId(), PosicionJugador.ALERO);
        SeleccionJugador alaPivot = jugadorDelPartido(evento, request.getAlapivotId(), PosicionJugador.ALA_PIVOT);
        SeleccionJugador pivot = jugadorDelPartido(evento, request.getPivotId(), PosicionJugador.PIVOT);
        List<SeleccionJugador> quinteto = List.of(base, escolta, alero, alaPivot, pivot);

        Set<Long> ids = new HashSet<>(quinteto.stream().map(SeleccionJugador::getId).toList());
        if (ids.size() != 5) {
            throw new IllegalArgumentException("No podés repetir jugadores");
        }
        double totalPrecio = quinteto.stream().mapToDouble(SeleccionJugador::getValor).sum();
        if (totalPrecio > PRESUPUESTO + 0.001) {
            throw new IllegalArgumentException("El presupuesto excede los 50cr");
        }
        if (!ids.contains(request.getCapitanId())) {
            throw new IllegalArgumentException("El capitan debe ser uno de los 5 jugadores elegidos");
        }

        // Si ya participó desde este dispositivo, se actualiza su equipo
        SeleccionParticipante participante = participanteRepo
                .findByEventoIdAndUuidDispositivo(evento.getId(), request.getUuidDispositivo())
                .orElseGet(() -> SeleccionParticipante.builder()
                        .evento(evento)
                        .uuidDispositivo(request.getUuidDispositivo())
                        .build());

        participante.setNombre(request.getNombre().trim());
        participante.setApellido(request.getApellido().trim());
        participante.setEmail(email);
        participante.setCapitanId(request.getCapitanId());
        participante.setBase(base);
        participante.setEscolta(escolta);
        participante.setAlero(alero);
        participante.setAlaPivot(alaPivot);
        participante.setPivot(pivot);

        return participanteRepo.save(participante).getId();
    }

    /** Validación del primer paso (datos personales), antes de armar el equipo. */
    @Transactional(readOnly = true)
    public void verificarInscripcion(String codigo, String email, String uuidDispositivo) {
        validarInscripcion(buscar(codigo), email, uuidDispositivo);
    }

    /** Inscripción abierta y email libre (o del mismo dispositivo, que puede rehacer su equipo). */
    private String validarInscripcion(SeleccionEvento evento, String emailIngresado, String uuidDispositivo) {
        if (evento.estadoActual() != EstadoShowdown.ABIERTO) {
            throw new IllegalStateException("El partido ya empezó: la inscripción está cerrada");
        }
        // Una participación por persona: cada email entra una sola vez por evento
        String email = emailIngresado.trim().toLowerCase(Locale.ROOT);
        participanteRepo.findFirstByEventoIdAndEmailIgnoreCase(evento.getId(), email)
                .filter(p -> !p.getUuidDispositivo().equals(uuidDispositivo))
                .ifPresent(p -> {
                    throw new IllegalStateException("Ese email ya está participando en este evento");
                });
        return email;
    }

    @Transactional(readOnly = true)
    public List<ShowdownParticipanteDto> getRanking(String codigo, String uuidDispositivo) {
        SeleccionEvento evento = buscar(codigo);
        return participanteRepo.findAllByEventoIdOrderByPuntosTotalesDesc(evento.getId()).stream()
                .map(p -> ShowdownParticipanteDto.builder()
                        .id(p.getId())
                        .nombre(p.getNombre())
                        .apellido(p.getApellido())
                        .puntosTotales(p.getPuntosTotales())
                        .esMio(p.getUuidDispositivo().equals(uuidDispositivo))
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public ShowdownMiEquipoDto getMiEquipo(String codigo, String uuidDispositivo) {
        SeleccionEvento evento = buscar(codigo);
        SeleccionParticipante participante = participanteRepo
                .findByEventoIdAndUuidDispositivo(evento.getId(), uuidDispositivo)
                .orElseThrow(() -> new IllegalArgumentException("No estás participando en este evento"));

        Map<Long, SeleccionEstadistica> planillas = SeleccionPuntuacion.porJugador(
                estadisticaRepo.findAllByEventoId(evento.getId()));

        List<ShowdownJugadorStatsDto> jugadores = participante.quinteto().stream()
                .map(j -> aStatsDto(j, planillas.get(j.getId()), j.getId().equals(participante.getCapitanId())))
                .toList();

        return ShowdownMiEquipoDto.builder()
                .participanteId(participante.getId())
                .puntosTotales(participante.getPuntosTotales())
                .jugadores(jugadores)
                .build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private SeleccionEvento buscar(String codigo) {
        return eventoRepo.findByCodigoInscripcion(codigo)
                .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado"));
    }

    private SeleccionJugador jugadorDelPartido(SeleccionEvento evento, Long jugadorId, PosicionJugador posicion) {
        SeleccionJugador jugador = jugadorRepo.findById(jugadorId)
                .orElseThrow(() -> new IllegalArgumentException("Jugador no encontrado"));
        Long equipoId = jugador.getEquipo().getId();
        if (!equipoId.equals(evento.getLocal().getId()) && !equipoId.equals(evento.getVisitante().getId())) {
            throw new IllegalArgumentException(jugador.getNombreCompleto() + " no juega este partido");
        }
        if (jugador.getPosicion() != posicion) {
            throw new IllegalArgumentException(jugador.getNombreCompleto() + " no juega de " + posicion.name().toLowerCase(Locale.ROOT));
        }
        return jugador;
    }

    private ShowdownEventoDto aDto(SeleccionEvento evento) {
        return ShowdownEventoDto.builder()
                .id(evento.getId())
                .codigoInscripcion(evento.getCodigoInscripcion())
                .estado(evento.estadoActual())
                .localSigla(evento.getLocal().getSigla())
                .localNombre(evento.getLocal().getNombre())
                .visitanteSigla(evento.getVisitante().getSigla())
                .visitanteNombre(evento.getVisitante().getNombre())
                .fecha(evento.getFechaHora().toString())
                .build();
    }

    private static SeleccionMercadoJugadorDto aMercadoDto(SeleccionJugador j) {
        SeleccionEquipo e = j.getEquipo();
        return new SeleccionMercadoJugadorDto(
                j.getId(), j.getNombreCompleto(), j.getNumeroCamiseta(), j.getPosicion().name(), "DISPONIBLE",
                j.getValor(),
                new SeleccionMercadoJugadorDto.Equipo(e.getNombre(), e.getSigla(), e.getColorPrincipal(),
                        e.getColorSecundario(), e.getModeloCamiseta()));
    }

    private static ShowdownJugadorStatsDto aStatsDto(SeleccionJugador j, SeleccionEstadistica stat, boolean esCapitan) {
        SeleccionEquipo equipo = j.getEquipo();
        double valFantasy = stat != null ? stat.getPuntajeFantasy() : 0.0;
        return ShowdownJugadorStatsDto.builder()
                .id(j.getId())
                .nombreCompleto(j.getNombreCompleto())
                .equipoSigla(equipo.getSigla())
                .equipoColorPrincipal(equipo.getColorPrincipal())
                .equipoColorSecundario(equipo.getColorSecundario())
                .equipoModeloCamiseta(equipo.getModeloCamiseta())
                .numeroCamiseta(j.getNumeroCamiseta())
                .posicion(j.getPosicion().name())
                .esCapitan(esCapitan)
                .fueTitular(stat != null && stat.getTitular())
                .gano(stat != null && stat.getEquipoGano())
                .puntos(stat != null ? stat.getPuntos() : 0)
                .asistencias(stat != null ? stat.getAsistencias() : 0)
                .rebotesDefensivos(stat != null ? stat.getRebotesDefensivos() : 0)
                .rebotesOfensivos(stat != null ? stat.getRebotesOfensivos() : 0)
                .recuperaciones(stat != null ? stat.getRecuperaciones() : 0)
                .taponesRealizados(stat != null ? stat.getTaponesRealizados() : 0)
                .faltasRecibidas(stat != null ? stat.getFaltasRecibidas() : 0)
                .perdidas(stat != null ? stat.getPerdidas() : 0)
                .taponesRecibidos(stat != null ? stat.getTaponesRecibidos() : 0)
                .faltasCometidas(stat != null ? stat.getFaltasCometidas() : 0)
                .tirosDeCampoFallados(stat != null ? stat.getTirosCampoFallados() : 0)
                .tirosLibresFallados(stat != null ? stat.getTirosLibresFallados() : 0)
                .dobleDoble(stat != null && SeleccionPuntuacion.categoriasConDiez(stat) == 2)
                .tripleDoble(stat != null && SeleccionPuntuacion.categoriasConDiez(stat) >= 3)
                .fueMvp(stat != null && stat.getFueMvp())
                .valFantasy(valFantasy)
                .puntosAportados(esCapitan ? valFantasy * SeleccionPuntuacion.MULTIPLICADOR_CAPITAN : valFantasy)
                .build();
    }
}
