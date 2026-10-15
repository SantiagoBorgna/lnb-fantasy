package com.fantasy.lnb.feature.ranking;

import com.fantasy.lnb.feature.plantel.PlantelJornada;
import com.fantasy.lnb.feature.plantel.PlantelJornadaRepository;
import com.fantasy.lnb.feature.ranking.dto.MiResumenJornadaDto;
import com.fantasy.lnb.feature.ranking.dto.PosicionGlobalDto;
import com.fantasy.lnb.feature.usuario.EquipoVirtual;
import com.fantasy.lnb.feature.usuario.EquipoVirtualRepository;
import com.fantasy.lnb.feature.usuario.Usuario;
import com.fantasy.lnb.feature.torneo.Torneo;
import com.fantasy.lnb.feature.torneo.TorneoRepository;
import com.fantasy.lnb.feature.torneo.TorneoEquipoRepository;
import com.fantasy.lnb.feature.torneo.ModalidadTorneo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    private final EquipoVirtualRepository equipoVirtualRepo;
    private final PlantelJornadaRepository plantelJornadaRepo;
    private final TorneoRepository torneoRepo;
    private final TorneoEquipoRepository torneoEquipoRepo;

    /**
     * Ranking global — todos los equipos ordenados por puntajeGlobal.
     * 
     * @param limite Cuántos equipos devolver (default 100, max 500)
     */
    @Transactional(readOnly = true)
    public List<PosicionGlobalDto> obtenerRankingGlobal(int limite) {
        int limiteSeguro = Math.min(limite, 500);

        AtomicInteger posicion = new AtomicInteger(1);

        return equipoVirtualRepo
                .findAllOrderByPuntajeGlobalDesc(PageRequest.of(0, limiteSeguro))
                .stream()
                .map(ev -> {
                    String sigla = "";
                    String color = "";

                    // El equipo favorito es opcional en el perfil del usuario
                    if (ev.getUsuario().getEquipoFavorito() != null) {
                        sigla = ev.getUsuario().getEquipoFavorito().getSigla();
                        color = ev.getUsuario().getEquipoFavorito()
                                .getColorPrincipal();
                    }

                    return PosicionGlobalDto.builder()
                            .posicion(posicion.getAndIncrement())
                            .nombreEquipo(ev.getNombre())
                            .nombreUsuario(ev.getUsuario().getNombreDisplay())
                            .equipoFavoritoSigla(sigla)
                            .equipoFavoritoColor(color)
                            .puntajeGlobal(ev.getPuntajeGlobal())
                            .equipoVirtualId(ev.getId())
                            .build();
                })
                .toList();
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "ranking_jornada", key = "#jornadaId")
    public List<PosicionGlobalDto> obtenerRankingJornada(Long jornadaId, int limite) {
        int limiteSeguro = Math.min(limite, 500);
        AtomicInteger posicion = new AtomicInteger(1);

        List<PlantelJornada> planteles = plantelJornadaRepo
                .findByJornada_IdAndTorneoIsNull(jornadaId)
                .stream()
                .sorted(Comparator.comparingDouble(PlantelJornada::getPuntajeObtenidoFecha).reversed())
                .limit(limiteSeguro)
                .toList();

        if (planteles.isEmpty()) return List.of();

        List<Long> usuarioIds = planteles.stream().map(p -> p.getUsuario().getId()).toList();
        java.util.Map<Long, EquipoVirtual> equiposMap = equipoVirtualRepo.findByUsuario_IdIn(usuarioIds).stream()
                .collect(java.util.stream.Collectors.toMap(e -> e.getUsuario().getId(), e -> e));

        return planteles.stream()
                .map(plantel -> {
                    Usuario u = plantel.getUsuario();
                    String sigla = "";
                    String color = "";
                    if (u.getEquipoFavorito() != null) {
                        sigla = u.getEquipoFavorito().getSigla();
                        color = u.getEquipoFavorito().getColorPrincipal();
                    }
                    EquipoVirtual ev = equiposMap.get(u.getId());
                    return PosicionGlobalDto.builder()
                            .posicion(posicion.getAndIncrement())
                            .nombreEquipo(ev != null ? ev.getNombre() : "—")
                            .nombreUsuario(u.getNombreDisplay())
                            .equipoFavoritoSigla(sigla)
                            .equipoFavoritoColor(color)
                            .puntajeGlobal(plantel.getPuntajeObtenidoFecha())
                            .equipoVirtualId(ev != null ? ev.getId() : null)
                            .build();
                })
                .toList();
    }

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "ranking_jornada_torneo", key = "#torneoId + '_' + #jornadaId")
    public List<PosicionGlobalDto> obtenerRankingJornadaTorneo(Long torneoId, Long jornadaId, int limite) {
        Torneo torneo = torneoRepo.findById(torneoId).orElseThrow();
        int limiteSeguro = Math.min(limite, 500);
        AtomicInteger posicion = new AtomicInteger(1);

        List<PlantelJornada> planteles;

        if (torneo.getModalidad() == ModalidadTorneo.DRAFT) {
            // Es Draft: los planteles estǭn guardados con torneo_id especifico
            planteles = plantelJornadaRepo.findByTorneo_IdAndJornada_Id(torneoId, jornadaId);
        } else {
            // Es Clǭsico: filtramos los planteles globales
            List<Long> usuariosEnTorneo = torneoEquipoRepo.findByTorneo_Id(torneoId)
                .stream().map(te -> te.getEquipoVirtual().getUsuario().getId()).toList();
                
            planteles = plantelJornadaRepo.findByJornada_IdAndTorneoIsNull(jornadaId)
                .stream().filter(p -> usuariosEnTorneo.contains(p.getUsuario().getId())).toList();
        }

        planteles = planteles.stream()
                .sorted(Comparator.comparingDouble(PlantelJornada::getPuntajeObtenidoFecha).reversed())
                .limit(limiteSeguro)
                .toList();

        if (planteles.isEmpty()) return List.of();

        List<Long> usuarioIds = planteles.stream().map(p -> p.getUsuario().getId()).toList();
        java.util.Map<Long, EquipoVirtual> equiposMap = equipoVirtualRepo.findByUsuario_IdIn(usuarioIds).stream()
                .collect(java.util.stream.Collectors.toMap(e -> e.getUsuario().getId(), e -> e));

        return planteles.stream()
                .map(plantel -> {
                    Usuario u = plantel.getUsuario();
                    String sigla = "";
                    String color = "";
                    if (u.getEquipoFavorito() != null) {
                        sigla = u.getEquipoFavorito().getSigla();
                        color = u.getEquipoFavorito().getColorPrincipal();
                    }
                    EquipoVirtual ev = equiposMap.get(u.getId());
                    return PosicionGlobalDto.builder()
                            .posicion(posicion.getAndIncrement())
                            .nombreEquipo(ev != null ? ev.getNombre() : "—")
                            .nombreUsuario(u.getNombreDisplay())
                            .equipoFavoritoSigla(sigla)
                            .equipoFavoritoColor(color)
                            .puntajeGlobal(plantel.getPuntajeObtenidoFecha())
                            .equipoVirtualId(ev != null ? ev.getId() : null)
                            .build();
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<PosicionGlobalDto> obtenerMiPosicion(Long usuarioId) {
        List<PosicionGlobalDto> ranking = obtenerRankingGlobal(500);
        return ranking.stream()
                .filter(p -> {
                    // Comparamos por equipoVirtualId
                    EquipoVirtual ev = equipoVirtualRepo
                            .findByUsuario_Id(usuarioId).orElse(null);
                    return ev != null && ev.getId().equals(p.getEquipoVirtualId());
                })
                .findFirst();
    }

    /**
     * Resumen de la jornada del usuario (modo clásico global) para la tarjeta compartible.
     * A diferencia de obtenerRankingJornada no tiene tope de 100/500: el puesto se calcula
     * sobre todos los planteles de la fecha. Solo para jornadas FINALIZADAS.
     */
    @Transactional(readOnly = true)
    public Optional<MiResumenJornadaDto> obtenerMiResumenJornada(Long usuarioId, Long jornadaId) {
        Optional<PlantelJornada> miPlantelOpt = plantelJornadaRepo
                .findByUsuario_IdAndJornada_IdAndTorneoIsNull(usuarioId, jornadaId);
        if (miPlantelOpt.isEmpty() || !miPlantelOpt.get().getJornada().estaFinalizada()) {
            return Optional.empty();
        }

        PlantelJornada miPlantel = miPlantelOpt.get();
        double miPuntaje = valor(miPlantel.getPuntajeObtenidoFecha());

        List<PlantelJornada> planteles = plantelJornadaRepo.findByJornada_IdAndTorneoIsNull(jornadaId);
        long mejoresEnLaFecha = planteles.stream()
                .filter(p -> valor(p.getPuntajeObtenidoFecha()) > miPuntaje)
                .count();

        EquipoVirtual equipo = equipoVirtualRepo.findByUsuario_Id(usuarioId).orElse(null);
        Double puntajeGlobal = equipo != null ? valor(equipo.getPuntajeGlobal()) : null;
        Integer posicionGlobal = puntajeGlobal != null
                ? (int) equipoVirtualRepo.countByPuntajeGlobalGreaterThan(puntajeGlobal) + 1
                : null;

        Usuario usuario = miPlantel.getUsuario();
        String colorFavorito = usuario.getEquipoFavorito() != null
                ? usuario.getEquipoFavorito().getColorPrincipal()
                : null;

        return Optional.of(MiResumenJornadaDto.builder()
                .jornadaNumero(miPlantel.getJornada().getNumero())
                .nombreEquipo(equipo != null ? equipo.getNombre() : null)
                .puntajeFecha(miPuntaje)
                .posicionFecha((int) mejoresEnLaFecha + 1)
                .totalParticipantesFecha(planteles.size())
                .posicionGlobal(posicionGlobal)
                .puntajeGlobal(puntajeGlobal)
                .equipoFavoritoColor(colorFavorito)
                .build());
    }

    private static double valor(Double puntaje) {
        return puntaje != null ? puntaje : 0.0;
    }
}
