package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartido;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository;
import com.fantasy.lnb.feature.estadisticas.MotorPuntuacion;
import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.jornada.Partido;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.feature.mercado.JugadorReal;
import com.fantasy.lnb.feature.mercado.JugadorRealRepository;
import com.fantasy.lnb.feature.plantel.PuntuacionService;
import com.fantasy.lnb.scraper.dto.JugadorStatsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.CacheManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MvpJornadaServiceTest {

    private final EquipoReal quimsa = EquipoReal.builder().id(3L).sigla("QUI").build();
    private final EquipoReal boca = EquipoReal.builder().id(2L).sigla("BOC").build();
    private final Jornada jornada = Jornada.builder().id(1L).numero(4).estado(EstadoJornada.EN_JUEGO).build();

    private JornadaRepository jornadaRepo;
    private PartidoRepository partidoRepo;
    private EstadisticaPartidoRepository estadisticaRepo;
    private ScraperService scraperService;
    private PuntuacionService puntuacionService;
    private MvpJornadaService service;

    @BeforeEach
    void setUp() {
        jornadaRepo = mock(JornadaRepository.class);
        partidoRepo = mock(PartidoRepository.class);
        estadisticaRepo = mock(EstadisticaPartidoRepository.class);
        scraperService = mock(ScraperService.class);
        puntuacionService = mock(PuntuacionService.class);
        service = new MvpJornadaService(jornadaRepo, partidoRepo, estadisticaRepo, scraperService,
                puntuacionService, mock(CacheManager.class));

        when(jornadaRepo.findById(1L)).thenReturn(Optional.of(jornada));
    }

    // ── Elección del MVP ────────────────────────────────────────────────────

    @Test
    void elMvpEsLaValoracionMasAltaDeLaPlanilla() {
        assertEquals(Set.of(2L), MotorPuntuacion.mvpsDelPartido(List.of(dto(1L, 20), dto(2L, 31), dto(3L, -2))));
    }

    @Test
    void conEmpateSonMvpTodosLosEmpatados() {
        assertEquals(Set.of(1L, 3L), MotorPuntuacion.mvpsDelPartido(List.of(dto(1L, 25), dto(2L, 10), dto(3L, 25))));
    }

    @Test
    void sinValoracionesOConFilaDeTotalesNoHayMvp() {
        // La fila de totales (sin IdJugador) tiene la valoración del equipo: no cuenta
        assertEquals(Set.of(1L), MotorPuntuacion.mvpsDelPartido(List.of(dto(1L, 20), dto(null, 90))));
        assertTrue(MotorPuntuacion.mvpsDelPartido(List.of(dto(1L, null))).isEmpty());
    }

    @Test
    void elMvpSumaDiezPuntos() {
        JugadorStatsDto d = dto(1L, 20);
        d.setPuntos(12);
        assertEquals(MotorPuntuacion.calcular(d, false, false) + 10.0, MotorPuntuacion.calcular(d, false, true));
    }

    // ── Carga de partidos nuevos ────────────────────────────────────────────

    @Test
    void alCargarUnPartidoSeMarcaYSumaElMvp() {
        JugadorRealRepository jugadorRepo = mock(JugadorRealRepository.class);
        when(jugadorRepo.findByGesId(100L)).thenReturn(Optional.of(jugador(1L, 100L, quimsa)));
        when(jugadorRepo.findByGesId(200L)).thenReturn(Optional.of(jugador(2L, 200L, boca)));
        ScraperService scraper = new ScraperService(estadisticaRepo, jugadorRepo, jornadaRepo);

        scraper.persistirEstadisticas(List.of(dto(100L, 30), dto(200L, 12)), "hash",
                LocalDateTime.of(2026, 10, 25, 21, 0), quimsa.getId(), boca.getId(), true, jornada);

        ArgumentCaptor<EstadisticaPartido> guardadas = ArgumentCaptor.forClass(EstadisticaPartido.class);
        verify(estadisticaRepo, times(2)).save(guardadas.capture());
        EstadisticaPartido mvp = guardadas.getAllValues().get(0);
        EstadisticaPartido otro = guardadas.getAllValues().get(1);
        assertTrue(mvp.getFueMvp());
        assertFalse(otro.getFueMvp());
        // Mismas stats (10 pts) y el MVP además ganó: +3 de victoria +10 de MVP
        assertEquals(otro.getPuntajeFantasyCalculado() + 13.0, mvp.getPuntajeFantasyCalculado());
    }

    // ── Aplicación a una jornada en juego ───────────────────────────────────

    @Test
    void sumaElBonoALosEmpatadosYRecalculaElParcial() {
        Partido partido = partido("hash");
        EstadisticaPartido a = estadistica(100L, 20.0, false);
        EstadisticaPartido b = estadistica(200L, 15.5, false);
        EstadisticaPartido c = estadistica(300L, 8.0, false);
        when(partidoRepo.findByJornada_Id(1L)).thenReturn(List.of(partido));
        when(estadisticaRepo.findByGesPartidoId("hash")).thenReturn(List.of(a, b, c));
        when(scraperService.extraerEstadisticasDePartido(partido.getGesUrl()))
                .thenReturn(List.of(dto(100L, 28), dto(200L, 28), dto(300L, 5)));

        service.aplicarMvpJornada(1L);

        assertEquals(30.0, a.getPuntajeFantasyCalculado());
        assertEquals(25.5, b.getPuntajeFantasyCalculado());
        assertEquals(8.0, c.getPuntajeFantasyCalculado());
        assertTrue(a.getFueMvp() && b.getFueMvp());
        assertFalse(c.getFueMvp());
        verify(puntuacionService).calcularPuntajesDeJornada(1L, false);
    }

    @Test
    void unPartidoConMvpYaAplicadoNoSeVuelveASumar() {
        Partido partido = partido("hash");
        EstadisticaPartido a = estadistica(100L, 30.0, true);
        when(partidoRepo.findByJornada_Id(1L)).thenReturn(List.of(partido));
        when(estadisticaRepo.findByGesPartidoId("hash")).thenReturn(List.of(a));

        service.aplicarMvpJornada(1L);

        assertEquals(30.0, a.getPuntajeFantasyCalculado());
        verify(scraperService, never()).extraerEstadisticasDePartido(anyString());
        verify(estadisticaRepo, never()).save(any());
    }

    @Test
    void siGesNoDevuelveLaPlanillaNoTocaNada() {
        Partido partido = partido("hash");
        EstadisticaPartido a = estadistica(100L, 20.0, false);
        when(partidoRepo.findByJornada_Id(1L)).thenReturn(List.of(partido));
        when(estadisticaRepo.findByGesPartidoId("hash")).thenReturn(List.of(a));
        when(scraperService.extraerEstadisticasDePartido(anyString())).thenReturn(List.of());

        service.aplicarMvpJornada(1L);

        assertEquals(20.0, a.getPuntajeFantasyCalculado());
        verify(estadisticaRepo, never()).save(any());
    }

    @Test
    void soloSeAplicaAJornadasEnJuego() {
        jornada.setEstado(EstadoJornada.FINALIZADA);

        assertThrows(IllegalStateException.class, () -> service.aplicarMvpJornada(1L));
        verify(puntuacionService, never()).calcularPuntajesDeJornada(any(), any(Boolean.class));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Partido partido(String hash) {
        Partido p = new Partido();
        p.setGesHash(hash);
        p.setGesUrl("https://ges/partido/" + hash);
        p.setEstadisticasProcesadas(true);
        return p;
    }

    private EstadisticaPartido estadistica(Long gesId, double puntaje, boolean fueMvp) {
        return EstadisticaPartido.builder()
                .jugadorReal(jugador(gesId, gesId, quimsa))
                .gesPartidoId("hash")
                .puntajeFantasyCalculado(puntaje)
                .fueMvp(fueMvp)
                .build();
    }

    private JugadorReal jugador(Long id, Long gesId, EquipoReal equipo) {
        return JugadorReal.builder().id(id).gesId(gesId).equipoReal(equipo).build();
    }

    private JugadorStatsDto dto(Long idJugador, Integer valoracion) {
        JugadorStatsDto dto = new JugadorStatsDto();
        dto.setIdJugador(idJugador);
        dto.setNombre("Jugador " + idJugador);
        dto.setPuntos(10);
        dto.setValoracion(valoracion);
        return dto;
    }
}
