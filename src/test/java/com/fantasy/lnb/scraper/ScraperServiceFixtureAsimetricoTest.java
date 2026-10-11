package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartido;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.mercado.JugadorReal;
import com.fantasy.lnb.feature.mercado.JugadorRealRepository;
import com.fantasy.lnb.scraper.dto.JugadorStatsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regla del fixture asimétrico: de los partidos que un equipo juega en una
 * jornada solo cuenta el primero. La regla es por equipo, no por partido.
 */
class ScraperServiceFixtureAsimetricoTest {

    private static final long GES_ID_QUIMSA = 100L;
    private static final long GES_ID_BOCA = 200L;

    private final EquipoReal quimsa = EquipoReal.builder().id(3L).sigla("QUI").build();
    private final EquipoReal boca = EquipoReal.builder().id(2L).sigla("BOC").build();
    private final Jornada jornada = Jornada.builder().id(1L).numero(1).build();

    private EstadisticaPartidoRepository estadisticaRepo;
    private ScraperService scraper;

    @BeforeEach
    void setUp() {
        estadisticaRepo = mock(EstadisticaPartidoRepository.class);
        JugadorRealRepository jugadorRepo = mock(JugadorRealRepository.class);

        when(jugadorRepo.findByGesId(GES_ID_QUIMSA)).thenReturn(Optional.of(jugador(1L, GES_ID_QUIMSA, quimsa)));
        when(jugadorRepo.findByGesId(GES_ID_BOCA)).thenReturn(Optional.of(jugador(2L, GES_ID_BOCA, boca)));

        scraper = new ScraperService(estadisticaRepo, jugadorRepo, mock(JornadaRepository.class));
    }

    @Test
    void elRivalQueTodaviaNoJugoCargaSuPrimerPartidoAunqueElOtroEquipoYaTengaStats() {
        // Boca ya jugó antes en la jornada (Boca vs Oberá), Quimsa no
        when(estadisticaRepo.existsByEquipoRealIdAndJornadaId(boca.getId(), jornada.getId())).thenReturn(true);

        persistirQuimsaVsBoca();

        ArgumentCaptor<EstadisticaPartido> guardadas = ArgumentCaptor.forClass(EstadisticaPartido.class);
        verify(estadisticaRepo).save(guardadas.capture());
        assertEquals(GES_ID_QUIMSA, guardadas.getValue().getJugadorReal().getGesId());
    }

    @Test
    void ignoraElPartidoCuandoAmbosEquiposYaJugaron() {
        when(estadisticaRepo.existsByEquipoRealIdAndJornadaId(boca.getId(), jornada.getId())).thenReturn(true);
        when(estadisticaRepo.existsByEquipoRealIdAndJornadaId(quimsa.getId(), jornada.getId())).thenReturn(true);

        persistirQuimsaVsBoca();

        verify(estadisticaRepo, never()).save(any());
    }

    @Test
    void cargaLosDosEquiposCuandoEsElPrimerPartidoDeAmbos() {
        persistirQuimsaVsBoca();

        verify(estadisticaRepo, times(2)).save(any(EstadisticaPartido.class));
    }

    private void persistirQuimsaVsBoca() {
        scraper.persistirEstadisticas(
                List.of(dto(GES_ID_QUIMSA), dto(GES_ID_BOCA)),
                "hash-quimsa-boca",
                LocalDateTime.of(2026, 10, 10, 20, 30),
                quimsa.getId(),
                boca.getId(),
                true,
                jornada);
    }

    private JugadorReal jugador(Long id, Long gesId, EquipoReal equipo) {
        return JugadorReal.builder().id(id).gesId(gesId).equipoReal(equipo).build();
    }

    private JugadorStatsDto dto(Long idJugador) {
        JugadorStatsDto dto = new JugadorStatsDto();
        dto.setIdJugador(idJugador);
        dto.setNombre("Jugador " + idJugador);
        dto.setPuntos(10);
        return dto;
    }
}
