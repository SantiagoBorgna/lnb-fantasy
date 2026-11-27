package com.fantasy.lnb.feature.seleccion;

import com.fantasy.lnb.feature.mercado.PosicionJugador;
import com.fantasy.lnb.feature.seleccion.dto.ResumenSeleccionDto;
import com.fantasy.lnb.feature.showdown.EstadoShowdown;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.fantasy.lnb.feature.seleccion.SeleccionTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SeleccionResumenTest {

    private final SeleccionEventoRepository eventoRepo = mock(SeleccionEventoRepository.class);
    private final SeleccionParticipanteRepository participanteRepo = mock(SeleccionParticipanteRepository.class);
    private final SeleccionEstadisticaRepository estadisticaRepo = mock(SeleccionEstadisticaRepository.class);
    private final SeleccionAdminService servicio = new SeleccionAdminService(mock(SeleccionEquipoRepository.class),
            mock(SeleccionJugadorRepository.class), eventoRepo, participanteRepo, estadisticaRepo);

    private final SeleccionJugador base1 = jugador(1L, ARG, PosicionJugador.BASE, 10);
    private final SeleccionJugador base2 = jugador(2L, CAN, PosicionJugador.BASE, 10);
    private final SeleccionJugador escolta = jugador(3L, ARG, PosicionJugador.ESCOLTA, 10);
    private final SeleccionJugador alero = jugador(4L, CAN, PosicionJugador.ALERO, 10);
    private final SeleccionJugador alaPivot = jugador(5L, ARG, PosicionJugador.ALA_PIVOT, 10);
    private final SeleccionJugador pivot = jugador(6L, CAN, PosicionJugador.PIVOT, 10);

    @Test
    void devuelveLosEmpatadosEnElPrimeroYElMejorDeCadaPosicion() {
        SeleccionEvento evento = evento(ahora().minusHours(3));
        evento.setEstado(EstadoShowdown.FINALIZADO);
        when(eventoRepo.findById(10L)).thenReturn(Optional.of(evento));
        when(estadisticaRepo.findAllByEventoId(10L)).thenReturn(List.of(
                stat(base1, 20), stat(base2, 30), stat(escolta, 8), stat(alero, 12), stat(alaPivot, 5), stat(pivot, 15)));
        when(participanteRepo.findAllByEventoIdOrderByPuntosTotalesDescIdAsc(10L)).thenReturn(List.of(
                participante(100L, 50.0, 1L), participante(101L, 50.0, 3L), participante(102L, 20.0, 1L)));

        ResumenSeleccionDto resumen = servicio.getResumen(10L);

        assertEquals(List.of(100L, 101L), resumen.ganadores().stream().map(ResumenSeleccionDto.Ganador::participanteId).toList());
        ResumenSeleccionDto.Jugador capitan = resumen.ganadores().get(0).jugadores().get(0);
        assertTrue(capitan.esCapitan());
        assertEquals(30.0, capitan.puntosFantasy(), 0.001); // 20 x1.5

        assertEquals(5, resumen.quintetoIdeal().size());
        assertEquals("Jugador 2", resumen.quintetoIdeal().get(0).nombre()); // el base de 30
    }

    @Test
    void sinPlanillaNoHayResumen() {
        when(eventoRepo.findById(10L)).thenReturn(Optional.of(evento(ahora().minusHours(1))));
        assertThrows(IllegalStateException.class, () -> servicio.getResumen(10L));
    }

    private static SeleccionEstadistica stat(SeleccionJugador j, double puntaje) {
        return SeleccionEstadistica.builder().jugador(j).puntajeFantasy(puntaje).valoracion(0).build();
    }

    private SeleccionParticipante participante(Long id, double puntos, Long capitanId) {
        return SeleccionParticipante.builder().id(id).nombre("N" + id).apellido("A").puntosTotales(puntos).capitanId(capitanId)
                .base(base1).escolta(escolta).alero(alero).alaPivot(alaPivot).pivot(pivot).build();
    }
}
