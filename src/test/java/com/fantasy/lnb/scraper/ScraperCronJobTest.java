package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.equipo.EquipoRealRepository;
import com.fantasy.lnb.feature.jornada.EstadoPartido;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.jornada.Partido;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.feature.plantel.MotorPuntuacionPlantel;
import com.fantasy.lnb.feature.plantel.PlantelJornadaRepository;
import com.fantasy.lnb.feature.plantel.PuntuacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScraperCronJobTest {

    private final Jornada jornadaVieja = Jornada.builder().id(5L).numero(5).build();
    private final Jornada jornadaActual = Jornada.builder().id(6L).numero(6).build();

    private PartidoRepository partidoRepo;
    private PuntuacionService puntuacionService;
    private ScraperCronJob cronJob;

    @BeforeEach
    void setUp() {
        partidoRepo = mock(PartidoRepository.class);
        puntuacionService = mock(PuntuacionService.class);

        // procesarPartido sale a leer GES: acá solo importa qué jornadas se recalculan
        cronJob = spy(new ScraperCronJob(mock(ScraperService.class), mock(JornadaRepository.class),
                mock(EquipoRealRepository.class), partidoRepo, mock(PlantelJornadaRepository.class),
                mock(MotorPuntuacionPlantel.class), puntuacionService));
        doNothing().when(cronJob).procesarPartido(any());
    }

    @Test
    void unPartidoTrabadoDeUnaJornadaViejaNoFrenaLosPuntajesDeLaActual() {
        when(partidoRepo.findByEstadoAndEstadisticasProcesadasFalseOrderByFechaHoraAsc(EstadoPartido.FINALIZADO))
                .thenReturn(List.of(partido(jornadaVieja), partido(jornadaActual)));

        cronJob.procesarPartidosDeJornadaActiva();

        verify(puntuacionService).calcularPuntajesDeJornada(5L, false);
        verify(puntuacionService).calcularPuntajesDeJornada(6L, false);
    }

    @Test
    void variosPendientesDeUnaJornadaLaRecalculanUnaSolaVez() {
        when(partidoRepo.findByEstadoAndEstadisticasProcesadasFalseOrderByFechaHoraAsc(EstadoPartido.FINALIZADO))
                .thenReturn(List.of(partido(jornadaActual), partido(jornadaActual)));

        cronJob.procesarPartidosDeJornadaActiva();

        verify(puntuacionService, times(1)).calcularPuntajesDeJornada(6L, false);
    }

    private Partido partido(Jornada jornada) {
        return Partido.builder().jornada(jornada).estado(EstadoPartido.FINALIZADO).build();
    }
}
