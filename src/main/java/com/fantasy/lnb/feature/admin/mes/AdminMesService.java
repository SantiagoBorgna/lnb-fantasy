package com.fantasy.lnb.feature.admin.mes;

import com.fantasy.lnb.feature.admin.dto.JugadorQuintetoDto;
import com.fantasy.lnb.feature.admin.mes.dto.MesDto;
import com.fantasy.lnb.feature.admin.mes.dto.MesRequestDto;
import com.fantasy.lnb.feature.admin.mes.dto.MesResumenDto;
import com.fantasy.lnb.feature.admin.mes.dto.MesResumenDto.*;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartido;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.mercado.JugadorReal;
import com.fantasy.lnb.feature.mercado.PosicionJugador;
import com.fantasy.lnb.feature.plantel.PlantelJornada;
import com.fantasy.lnb.feature.plantel.PlantelJornadaRepository;
import com.fantasy.lnb.feature.plantel.RolPlantel;
import com.fantasy.lnb.feature.usuario.EquipoVirtual;
import com.fantasy.lnb.feature.usuario.EquipoVirtualRepository;
import com.fantasy.lnb.feature.usuario.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pestaña "Mes" del admin: el admin elige qué jornadas forman el mes y acá se arma el resumen
 * (tabla mensual, quinteto del puntero, quinteto ideal y MVP por promedio, ganadores por jornada).
 */
@Service
@RequiredArgsConstructor
public class AdminMesService {

    static final int TAMANIO_TABLA = 10;
    static final int TAMANIO_MVP = 5;

    private final MesFantasyRepository mesRepo;
    private final JornadaRepository jornadaRepo;
    private final PlantelJornadaRepository plantelRepo;
    private final EstadisticaPartidoRepository estadisticaRepo;
    private final EquipoVirtualRepository equipoVirtualRepo;

    // ── CRUD ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MesDto> listar() {
        Map<Long, Jornada> jornadas = jornadaRepo.findAll().stream()
                .collect(Collectors.toMap(Jornada::getId, Function.identity()));
        return mesRepo.findAllByOrderByCreadoEnDesc().stream()
                .map(m -> aDto(m, jornadas))
                .toList();
    }

    @Transactional
    public MesDto crear(MesRequestDto request) {
        MesFantasy mes = new MesFantasy();
        aplicar(mes, request);
        return aDto(mesRepo.save(mes), todasLasJornadas());
    }

    @Transactional
    public MesDto actualizar(Long id, MesRequestDto request) {
        MesFantasy mes = buscar(id);
        aplicar(mes, request);
        return aDto(mesRepo.save(mes), todasLasJornadas());
    }

    @Transactional
    public void eliminar(Long id) {
        mesRepo.delete(buscar(id));
    }

    private MesFantasy buscar(Long id) {
        return mesRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Mes no encontrado"));
    }

    private void aplicar(MesFantasy mes, MesRequestDto request) {
        String nombre = request.getNombre() != null ? request.getNombre().trim() : "";
        if (nombre.isEmpty()) throw new IllegalArgumentException("El mes necesita un nombre");
        List<Long> ids = request.getJornadaIds() != null ? request.getJornadaIds() : List.of();
        if (ids.isEmpty()) throw new IllegalArgumentException("Elegí al menos una jornada");
        if (jornadaRepo.findAllById(ids).size() != new HashSet<>(ids).size()) {
            throw new IllegalArgumentException("Alguna de las jornadas no existe");
        }
        mes.setNombre(nombre);
        mes.getJornadaIds().clear();
        mes.getJornadaIds().addAll(ids);
    }

    private Map<Long, Jornada> todasLasJornadas() {
        return jornadaRepo.findAll().stream().collect(Collectors.toMap(Jornada::getId, Function.identity()));
    }

    private MesDto aDto(MesFantasy mes, Map<Long, Jornada> jornadas) {
        List<Jornada> delMes = mes.getJornadaIds().stream()
                .map(jornadas::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Jornada::getNumero))
                .toList();
        return MesDto.builder()
                .id(mes.getId())
                .nombre(mes.getNombre())
                .jornadaIds(delMes.stream().map(Jornada::getId).toList())
                .jornadaNumeros(delMes.stream().map(Jornada::getNumero).toList())
                .build();
    }

    // ── Resumen ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public MesResumenDto resumen(Long mesId) {
        MesFantasy mes = buscar(mesId);
        List<Jornada> jornadas = jornadaRepo.findAllById(mes.getJornadaIds()).stream()
                .sorted(Comparator.comparing(Jornada::getNumero))
                .toList();
        // Solo cuentan las jornadas que ya empezaron
        List<Jornada> jugadas = jornadas.stream()
                .filter(j -> j.estaEnJuego() || j.estaFinalizada())
                .toList();
        List<Long> idsJugadas = jugadas.stream().map(Jornada::getId).toList();

        List<PlantelJornada> planteles = idsJugadas.isEmpty() ? List.of()
                : plantelRepo.findByJornada_IdInAndTorneoIsNull(idsJugadas);
        List<EstadisticaPartido> stats = idsJugadas.isEmpty() ? List.of()
                : estadisticaRepo.findByJornada_IdIn(idsJugadas).stream()
                        .filter(e -> e.getJugadorReal() != null && e.getPuntajeFantasyCalculado() != null)
                        .toList();

        Map<Long, EquipoVirtual> equipos = equiposDe(planteles);

        List<PosicionMesDto> tablaCompleta = tabla(planteles, equipos);
        List<PosicionMesDto> tabla = tablaCompleta.stream().limit(TAMANIO_TABLA).toList();

        // Partidos mínimos para el promedio: la mitad de las jornadas con estadísticas (redondeando para arriba)
        long jornadasConStats = stats.stream().map(e -> e.getJornada().getId()).distinct().count();
        int minimoPartidos = (int) Math.max(1, (jornadasConStats + 1) / 2);
        List<JugadorPromedioMesDto> promedios = promedios(stats, minimoPartidos);

        return MesResumenDto.builder()
                .mesId(mes.getId())
                .nombre(mes.getNombre())
                .jornadas(jornadas.stream().map(j -> JornadaMesDto.builder()
                        .id(j.getId()).numero(j.getNumero()).estado(j.getEstado().name()).build()).toList())
                .tabla(tabla)
                .puntero(tabla.isEmpty() ? null : puntero(tabla.get(0), jugadas, planteles, stats))
                .quintetoIdeal(quintetoIdeal(promedios))
                .mvp(promedios.stream().limit(TAMANIO_MVP).toList())
                .minimoPartidos(minimoPartidos)
                .ganadoresJornada(ganadores(jugadas, planteles, equipos))
                .build();
    }

    private Map<Long, EquipoVirtual> equiposDe(List<PlantelJornada> planteles) {
        Set<Long> usuarioIds = planteles.stream().map(p -> p.getUsuario().getId()).collect(Collectors.toSet());
        if (usuarioIds.isEmpty()) return Map.of();
        return equipoVirtualRepo.findByUsuario_IdIn(usuarioIds).stream()
                .collect(Collectors.toMap(e -> e.getUsuario().getId(), e -> e, (a, b) -> a));
    }

    /** Suma por usuario; desempata la mejor fecha individual del mes. */
    private List<PosicionMesDto> tabla(List<PlantelJornada> planteles, Map<Long, EquipoVirtual> equipos) {
        Map<Long, List<PlantelJornada>> porUsuario = planteles.stream()
                .collect(Collectors.groupingBy(p -> p.getUsuario().getId()));

        List<PosicionMesDto> filas = new ArrayList<>();
        porUsuario.forEach((usuarioId, suyos) -> {
            Usuario u = suyos.get(0).getUsuario();
            EquipoVirtual ev = equipos.get(usuarioId);
            filas.add(PosicionMesDto.builder()
                    .usuarioId(usuarioId)
                    .nombreEquipo(ev != null ? ev.getNombre() : u.getNombreDisplay())
                    .nombreUsuario(u.getNombreDisplay())
                    .puntos(redondear(suyos.stream().mapToDouble(p -> valor(p.getPuntajeObtenidoFecha())).sum()))
                    .jornadasJugadas(suyos.size())
                    .mejorFecha(suyos.stream().mapToDouble(p -> valor(p.getPuntajeObtenidoFecha())).max().orElse(0))
                    .build());
        });
        filas.sort(Comparator.comparingDouble(PosicionMesDto::getPuntos).reversed()
                .thenComparing(Comparator.comparingDouble(PosicionMesDto::getMejorFecha).reversed())
                .thenComparing(PosicionMesDto::getNombreEquipo, Comparator.nullsLast(String::compareToIgnoreCase)));
        for (int i = 0; i < filas.size(); i++) filas.get(i).setPosicion(i + 1);
        return filas;
    }

    /**
     * Titulares del líder en la última jornada jugada del mes en la que tenga plantel,
     * cada uno con su promedio de puntos fantasy en el mes (sin el x1.5 del capitán).
     */
    private PunteroMesDto puntero(PosicionMesDto lider, List<Jornada> jugadas,
                                  List<PlantelJornada> planteles, List<EstadisticaPartido> stats) {
        Map<Long, PlantelJornada> suyosPorJornada = planteles.stream()
                .filter(p -> p.getUsuario().getId().equals(lider.getUsuarioId()))
                .collect(Collectors.toMap(p -> p.getJornada().getId(), p -> p, (a, b) -> a));

        for (int i = jugadas.size() - 1; i >= 0; i--) {
            Jornada jornada = jugadas.get(i);
            PlantelJornada plantel = suyosPorJornada.get(jornada.getId());
            if (plantel == null) continue;

            Map<Long, Double> promedioMes = stats.stream()
                    .collect(Collectors.groupingBy(e -> e.getJugadorReal().getId(),
                            Collectors.averagingDouble(EstadisticaPartido::getPuntajeFantasyCalculado)));

            List<JugadorQuintetoDto> jugadores = plantel.getTitulares().stream()
                    .map(pj -> JugadorQuintetoDto.desde(pj.getJugadorReal(),
                            redondear(promedioMes.getOrDefault(pj.getJugadorReal().getId(), 0.0)),
                            pj.getRol() == RolPlantel.CAPITAN))
                    .toList();

            return PunteroMesDto.builder()
                    .nombreEquipo(lider.getNombreEquipo())
                    .nombreUsuario(lider.getNombreUsuario())
                    .puntajeMes(lider.getPuntos())
                    .jornadaNumero(jornada.getNumero())
                    .puntajeJornada(valor(plantel.getPuntajeObtenidoFecha()))
                    .jugadores(jugadores)
                    .build();
        }
        return null;
    }

    /** Promedio de puntos fantasy por jugador en el mes, solo los que llegan al mínimo de partidos, de mayor a menor. */
    private List<JugadorPromedioMesDto> promedios(List<EstadisticaPartido> stats, int minimoPartidos) {
        Map<Long, List<EstadisticaPartido>> porJugador = stats.stream()
                .collect(Collectors.groupingBy(e -> e.getJugadorReal().getId()));

        return porJugador.values().stream()
                .filter(lista -> lista.size() >= minimoPartidos)
                .map(lista -> {
                    JugadorReal jr = lista.get(0).getJugadorReal();
                    double total = lista.stream().mapToDouble(EstadisticaPartido::getPuntajeFantasyCalculado).sum();
                    double promedio = redondear(total / lista.size());
                    return JugadorPromedioMesDto.builder()
                            .jugador(JugadorQuintetoDto.desde(jr, promedio, false))
                            .partidos(lista.size())
                            .promedio(promedio)
                            .total(redondear(total))
                            .build();
                })
                .sorted(Comparator.comparingDouble(JugadorPromedioMesDto::getPromedio).reversed()
                        .thenComparing(Comparator.comparingInt(JugadorPromedioMesDto::getPartidos).reversed()))
                .toList();
    }

    /** El de mejor promedio en cada posición (la lista ya viene ordenada por promedio). */
    private List<JugadorPromedioMesDto> quintetoIdeal(List<JugadorPromedioMesDto> promedios) {
        List<JugadorPromedioMesDto> quinteto = new ArrayList<>();
        for (PosicionJugador pos : PosicionJugador.values()) {
            if (pos == PosicionJugador.DESCONOCIDO) continue;
            promedios.stream()
                    .filter(p -> p.getJugador().getPosicion() == pos)
                    .findFirst()
                    .ifPresent(quinteto::add);
        }
        return quinteto;
    }

    private List<GanadorJornadaDto> ganadores(List<Jornada> jugadas, List<PlantelJornada> planteles,
                                              Map<Long, EquipoVirtual> equipos) {
        Map<Long, List<PlantelJornada>> porJornada = planteles.stream()
                .collect(Collectors.groupingBy(p -> p.getJornada().getId()));
        List<GanadorJornadaDto> ganadores = new ArrayList<>();
        for (Jornada j : jugadas) {
            porJornada.getOrDefault(j.getId(), List.of()).stream()
                    .max(Comparator.comparingDouble(p -> valor(p.getPuntajeObtenidoFecha())))
                    .ifPresent(p -> {
                        EquipoVirtual ev = equipos.get(p.getUsuario().getId());
                        ganadores.add(GanadorJornadaDto.builder()
                                .jornadaNumero(j.getNumero())
                                .nombreEquipo(ev != null ? ev.getNombre() : p.getUsuario().getNombreDisplay())
                                .nombreUsuario(p.getUsuario().getNombreDisplay())
                                .puntaje(valor(p.getPuntajeObtenidoFecha()))
                                .build());
                    });
        }
        return ganadores;
    }

    private static double valor(Double d) {
        return d != null ? d : 0.0;
    }

    private static double redondear(double d) {
        return Math.round(d * 100.0) / 100.0;
    }
}
