package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.jornada.EstadoPartido;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.jornada.JornadaService;
import com.fantasy.lnb.feature.jornada.Partido;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.feature.notificaciones.AvisoAdminService;
import com.fantasy.lnb.feature.notificaciones.PushNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JornadaTransicionCierreIncompletoTest {

    private final EquipoReal ferro = EquipoReal.builder().id(1L).nombre("FERRO").sigla("FER").build();
    private final EquipoReal sanMartin = EquipoReal.builder().id(2L).nombre("SAN MARTIN (C)").sigla("SMA").build();

    // Terminó hace más que la espera máxima: se cierra aunque falten estadísticas
    private final Jornada jornada = Jornada.builder().id(5L).numero(5).estado(EstadoJornada.EN_JUEGO)
            .fechaFin(LocalDateTime.now().minusHours(4)).build();

    private PartidoRepository partidoRepo;
    private JornadaCierreService cierreService;
    private AvisoAdminService avisoAdmin;
    private JornadaTransicionCronJob cronJob;

    @BeforeEach
    void setUp() {
        JornadaRepository jornadaRepo = mock(JornadaRepository.class);
        partidoRepo = mock(PartidoRepository.class);
        cierreService = mock(JornadaCierreService.class);
        avisoAdmin = mock(AvisoAdminService.class);

        when(jornadaRepo.findByEstadoAndFechaFinLessThan(eq(EstadoJornada.EN_JUEGO), any()))
                .thenReturn(Optional.of(jornada));

        cronJob = new JornadaTransicionCronJob(jornadaRepo, partidoRepo, mock(ScraperCronJob.class),
                mock(JornadaService.class), cierreService, mock(PushNotificationService.class), avisoAdmin,
                mock(CacheManager.class));
    }

    @Test
    void cerrarConPartidosSinEstadisticasAvisaALosAdmins() {
        partidosDeLaJornada(partido(EstadoPartido.FINALIZADO));

        cronJob.evaluarTransiciones();

        verify(cierreService).cerrarJornadaCompleta(jornada);
        verify(avisoAdmin).avisar(eq("J5 cerró con 1 partido(s) sin estadísticas"), contains("FERRO vs SAN MARTIN (C)"));
    }

    @Test
    void cerrarConTodoProcesadoNoAvisa() {
        partidosDeLaJornada(partido(EstadoPartido.PROCESADO));

        cronJob.evaluarTransiciones();

        verify(cierreService).cerrarJornadaCompleta(jornada);
        verify(avisoAdmin, never()).avisar(anyString(), anyString());
    }

    @Test
    void unAvisoQueFallaNoFrenaElCicloDelCron() {
        partidosDeLaJornada(partido(EstadoPartido.FINALIZADO));
        doThrow(new RuntimeException("push caído")).when(avisoAdmin).avisar(anyString(), anyString());

        cronJob.evaluarTransiciones();

        verify(cierreService).cerrarJornadaCompleta(jornada);
    }

    private void partidosDeLaJornada(Partido... partidos) {
        when(partidoRepo.findByJornada_Id(5L)).thenReturn(List.of(partidos));
        when(partidoRepo.findConDetalleByJornadaIds(List.of(5L))).thenReturn(List.of(partidos));
    }

    private Partido partido(EstadoPartido estado) {
        return Partido.builder().jornada(jornada).equipoLocal(ferro).equipoVisitante(sanMartin).estado(estado).build();
    }
}
