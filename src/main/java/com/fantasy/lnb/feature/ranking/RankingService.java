package com.fantasy.lnb.feature.ranking;

import com.fantasy.lnb.feature.plantel.PlantelJornada;
import com.fantasy.lnb.feature.plantel.PlantelJornadaRepository;
import com.fantasy.lnb.feature.ranking.dto.PosicionGlobalDto;
import com.fantasy.lnb.feature.usuario.EquipoVirtual;
import com.fantasy.lnb.feature.usuario.EquipoVirtualRepository;
import com.fantasy.lnb.feature.usuario.Usuario;

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
    public List<PosicionGlobalDto> obtenerRankingJornada(Long jornadaId, int limite) {
        int limiteSeguro = Math.min(limite, 500);
        AtomicInteger posicion = new AtomicInteger(1);

        return plantelJornadaRepo
                .findAllByJornadaIdWithJugadores(jornadaId)
                .stream()
                .sorted(Comparator.comparingDouble(
                        PlantelJornada::getPuntajeObtenidoFecha).reversed())
                .limit(limiteSeguro)
                .map(plantel -> {
                    Usuario u = plantel.getUsuario();
                    String sigla = "";
                    String color = "";
                    if (u.getEquipoFavorito() != null) {
                        sigla = u.getEquipoFavorito().getSigla();
                        color = u.getEquipoFavorito().getColorPrincipal();
                    }
                    return PosicionGlobalDto.builder()
                            .posicion(posicion.getAndIncrement())
                            .nombreEquipo(
                                    equipoVirtualRepo.findByUsuario_Id(u.getId())
                                            .map(EquipoVirtual::getNombre)
                                            .orElse("—"))
                            .nombreUsuario(u.getNombreDisplay())
                            .equipoFavoritoSigla(sigla)
                            .equipoFavoritoColor(color)
                            .puntajeGlobal(plantel.getPuntajeObtenidoFecha())
                            .equipoVirtualId(
                                    equipoVirtualRepo.findByUsuario_Id(u.getId())
                                            .map(EquipoVirtual::getId)
                                            .orElse(null))
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
}