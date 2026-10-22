package com.fantasy.lnb.feature.admin;

import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartido;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.jornada.Partido;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.feature.showdown.ShowdownEventoRepository;
import com.fantasy.lnb.scraper.ScraperCronJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminPartidosEliminarTest {

    private PartidoRepository partidoRepo;
    private EstadisticaPartidoRepository estadisticaRepo;
    private ShowdownEventoRepository showdownRepo;
    private AdminPartidosService servicio;
    private Partido partido;

    @BeforeEach
    void setUp() {
        partidoRepo = mock(PartidoRepository.class);
        estadisticaRepo = mock(EstadisticaPartidoRepository.class);
        showdownRepo = mock(ShowdownEventoRepository.class);
        servicio = new AdminPartidosService(partidoRepo, mock(JornadaRepository.class), estadisticaRepo,
                mock(ScraperCronJob.class), showdownRepo);

        EquipoReal local = new EquipoReal();
        local.setNombre("LOCAL");
        EquipoReal visitante = new EquipoReal();
        visitante.setNombre("VISITANTE");
        Jornada jornada = new Jornada();
        jornada.setNumero(3);
        partido = Partido.builder().id(7L).gesHash("abc").jornada(jornada)
                .equipoLocal(local).equipoVisitante(visitante)
                .fechaHora(LocalDateTime.of(2026, 11, 10, 21, 0))
                .estadisticasProcesadas(false).build();
        when(partidoRepo.findById(7L)).thenReturn(Optional.of(partido));
        when(estadisticaRepo.findByGesPartidoId("abc")).thenReturn(List.of());
    }

    @Test
    void eliminaUnPartidoPostergadoSinDatos() {
        servicio.eliminarPartido(7L);

        verify(partidoRepo).delete(partido);
    }

    @Test
    void noEliminaSiYaTieneEstadisticas() {
        when(estadisticaRepo.findByGesPartidoId("abc")).thenReturn(List.of(new EstadisticaPartido()));

        assertThrows(IllegalStateException.class, () -> servicio.eliminarPartido(7L));
        verify(partidoRepo, never()).delete(any());
    }

    @Test
    void noEliminaSiTieneUnEventoDeModoEstadio() {
        when(showdownRepo.existsByPartido_Id(7L)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> servicio.eliminarPartido(7L));
        verify(partidoRepo, never()).delete(any());
    }
}
