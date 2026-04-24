package com.fantasy.lnb.feature.ranking;

import com.fantasy.lnb.feature.ranking.dto.PosicionGlobalDto;
import com.fantasy.lnb.feature.usuario.EquipoVirtualRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    private final EquipoVirtualRepository equipoVirtualRepo;

    /**
     * Ranking global — todos los equipos ordenados por puntajeGlobal.
     * 
     * @param limite Cuántos equipos devolver (default 100, max 500)
     */
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
}