package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.dt.DirectorTecnicoService;
import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository;
import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.jornada.EstadoPartido;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.jornada.Partido;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.feature.mercado.MercadoService;
import com.fantasy.lnb.feature.plantel.PlantelJornadaRepository;
import com.fantasy.lnb.feature.plantel.PuntuacionService;
import com.fantasy.lnb.feature.torneo.Torneo;
import com.fantasy.lnb.feature.torneo.TipoPuntuacion;
import com.fantasy.lnb.feature.torneo.TorneoEquipo;
import com.fantasy.lnb.feature.torneo.TorneoEquipoRepository;
import com.fantasy.lnb.feature.torneo.TorneoH2HService;
import com.fantasy.lnb.feature.usuario.EquipoVirtual;
import com.fantasy.lnb.feature.usuario.EquipoVirtualRepository;
import com.fantasy.lnb.feature.usuario.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JornadaRecalculoServiceTest {

    private final Jornada jornada = Jornada.builder().id(1L).numero(1).estado(EstadoJornada.FINALIZADA).build();

    private final EquipoReal quimsa = EquipoReal.builder().id(3L).sigla("QUI").build();
    private final EquipoReal boca = EquipoReal.builder().id(2L).sigla("BOC").build();
    private final EquipoReal obera = EquipoReal.builder().id(4L).sigla("OBE").build();

    private JornadaRepository jornadaRepo;
    private PartidoRepository partidoRepo;
    private EstadisticaPartidoRepository estadisticaRepo;
    private ScraperCronJob scraperCronJob;
    private PlantelJornadaRepository plantelRepo;
    private EquipoVirtualRepository equipoVirtualRepo;
    private TorneoEquipoRepository torneoEquipoRepo;
    private JornadaRecalculoService service;

    @BeforeEach
    void setUp() {
        jornadaRepo = mock(JornadaRepository.class);
        partidoRepo = mock(PartidoRepository.class);
        estadisticaRepo = mock(EstadisticaPartidoRepository.class);
        scraperCronJob = mock(ScraperCronJob.class);
        plantelRepo = mock(PlantelJornadaRepository.class);
        equipoVirtualRepo = mock(EquipoVirtualRepository.class);
        torneoEquipoRepo = mock(TorneoEquipoRepository.class);

        service = new JornadaRecalculoService(jornadaRepo, partidoRepo, estadisticaRepo, scraperCronJob,
                mock(PuntuacionService.class), plantelRepo, equipoVirtualRepo, torneoEquipoRepo,
                mock(TorneoH2HService.class), mock(DirectorTecnicoService.class), mock(MercadoService.class),
                mock(CacheManager.class));
    }

    // ── Validación ──────────────────────────────────────────────────────────

    @Test
    void soloSePuedeRecalcularUnaJornadaFinalizada() {
        jornada.setEstado(EstadoJornada.EN_JUEGO);
        when(jornadaRepo.findById(1L)).thenReturn(Optional.of(jornada));

        assertThrows(IllegalStateException.class, () -> service.validarRecalculable(1L));
    }

    @Test
    void rechazaUnaJornadaInexistente() {
        when(jornadaRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.validarRecalculable(99L));
    }

    // ── Estadísticas faltantes ──────────────────────────────────────────────

    @Test
    void reprocesaSoloLosPartidosDondeAlgunEquipoNoTieneStats() {
        // Boca y Oberá ya tienen stats; Quimsa no (su partido contra Boca se había descartado)
        when(estadisticaRepo.existsByEquipoRealIdAndJornadaId(eq(boca.getId()), any())).thenReturn(true);
        when(estadisticaRepo.existsByEquipoRealIdAndJornadaId(eq(obera.getId()), any())).thenReturn(true);

        Partido completo = partido(boca, obera, EstadoPartido.PROCESADO, 7);
        Partido incompleto = partido(quimsa, boca, EstadoPartido.PROCESADO, 10);
        Partido programado = partido(quimsa, obera, EstadoPartido.PROGRAMADO, 12);
        when(partidoRepo.findByJornada_Id(1L)).thenReturn(List.of(incompleto, programado, completo));

        int completados = service.completarEstadisticasFaltantes(jornada);

        assertEquals(1, completados);
        verify(scraperCronJob).procesarPartido(incompleto);
        verify(scraperCronJob, never()).procesarPartido(completo);
        verify(scraperCronJob, never()).procesarPartido(programado);
    }

    @Test
    void siFallaUnPartidoSigueConLosDemas() {
        Partido primero = partido(quimsa, boca, EstadoPartido.PROCESADO, 8);
        Partido segundo = partido(boca, obera, EstadoPartido.PROCESADO, 9);
        when(partidoRepo.findByJornada_Id(1L)).thenReturn(List.of(primero, segundo));
        doThrow(new IllegalStateException("GES sin acta")).when(scraperCronJob)
                .procesarPartido(primero);

        int completados = service.completarEstadisticasFaltantes(jornada);

        assertEquals(1, completados);
        verify(scraperCronJob).procesarPartido(segundo);
    }

    // ── Ranking ─────────────────────────────────────────────────────────────

    @Test
    void elRankingGlobalSeReconstruyeDesdeLosPuntajesDeLosPlanteles() {
        EquipoVirtual conPuntos = equipoVirtual(1L, 99.0);   // quedó con un valor viejo
        EquipoVirtual sinPlantel = equipoVirtual(2L, 15.0);  // no tiene planteles en jornadas cerradas
        when(equipoVirtualRepo.findAll()).thenReturn(List.of(conPuntos, sinPlantel));
        when(plantelRepo.sumarPuntajesGlobalesPorUsuario(EstadoJornada.FINALIZADA))
                .thenReturn(List.<Object[]>of(new Object[] { 1L, 120.456 }));

        service.reconstruirRankings();

        assertEquals(120.46, conPuntos.getPuntajeGlobal());
        assertEquals(0.0, sinPlantel.getPuntajeGlobal());
    }

    @Test
    void elRankingNoSeToca_siYaEstaBien() {
        EquipoVirtual bien = equipoVirtual(1L, 120.5);
        when(equipoVirtualRepo.findAll()).thenReturn(List.of(bien));
        when(plantelRepo.sumarPuntajesGlobalesPorUsuario(EstadoJornada.FINALIZADA))
                .thenReturn(List.<Object[]>of(new Object[] { 1L, 120.5 }));

        service.reconstruirRankings();

        verify(equipoVirtualRepo, never()).save(any());
    }

    @Test
    void losTorneosGeneralesSeReconstruyenYLosH2HSeDejanParaSuPropioRecalculo() {
        EquipoVirtual ev = equipoVirtual(1L, 0.0);
        when(equipoVirtualRepo.findAll()).thenReturn(List.of(ev));

        TorneoEquipo general = torneoEquipo(10L, TipoPuntuacion.GENERAL, ev, 50.0);
        TorneoEquipo h2h = torneoEquipo(20L, TipoPuntuacion.H2H, ev, 6.0); // puntos de tabla, no de fantasy
        when(torneoEquipoRepo.findAll()).thenReturn(List.of(general, h2h));
        when(plantelRepo.sumarPuntajesDeTorneosPorUsuario(EstadoJornada.FINALIZADA))
                .thenReturn(List.<Object[]>of(
                        new Object[] { 10L, 1L, 80.0 },
                        new Object[] { 20L, 1L, 300.0 }));

        service.reconstruirRankings();

        assertEquals(80.0, general.getPuntajeGlobal());
        assertEquals(6.0, h2h.getPuntajeGlobal());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private Partido partido(EquipoReal local, EquipoReal visitante, EstadoPartido estado, int dia) {
        return Partido.builder()
                .jornada(jornada)
                .equipoLocal(local)
                .equipoVisitante(visitante)
                .estado(estado)
                .fechaHora(LocalDateTime.of(2026, 10, dia, 20, 30))
                .build();
    }

    private EquipoVirtual equipoVirtual(Long usuarioId, double puntajeGlobal) {
        return EquipoVirtual.builder()
                .id(usuarioId)
                .usuario(Usuario.builder().id(usuarioId).build())
                .puntajeGlobal(puntajeGlobal)
                .build();
    }

    private TorneoEquipo torneoEquipo(Long torneoId, TipoPuntuacion tipo, EquipoVirtual ev, double puntajeGlobal) {
        return TorneoEquipo.builder()
                .torneo(Torneo.builder().id(torneoId).tipoPuntuacion(tipo).build())
                .equipoVirtual(ev)
                .puntajeGlobal(puntajeGlobal)
                .build();
    }
}
