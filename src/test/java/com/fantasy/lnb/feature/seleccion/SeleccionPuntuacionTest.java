package com.fantasy.lnb.feature.seleccion;

import com.fantasy.lnb.feature.mercado.PosicionJugador;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.fantasy.lnb.feature.seleccion.SeleccionTestData.*;
import static org.junit.jupiter.api.Assertions.*;

class SeleccionPuntuacionTest {

    @Test
    void valoracionClasica() {
        SeleccionEstadistica e = SeleccionEstadistica.builder()
                .puntos(20).rebotesOfensivos(2).rebotesDefensivos(5).asistencias(4).recuperaciones(1)
                .taponesRealizados(1).faltasRecibidas(3)
                .tirosCampoFallados(6).tirosLibresFallados(1).perdidas(2).taponesRecibidos(1).faltasCometidas(2)
                .build();
        // 20+2+5+4+1+1+3 - 6-1-2-1-2
        assertEquals(24, SeleccionPuntuacion.valoracion(e));
    }

    @Test
    void marcaGanadorYMvpYUsaElMotorDeLaLiga() {
        SeleccionEvento evento = evento(ahora().minusHours(2));
        evento.setPuntosLocal(80);
        evento.setPuntosVisitante(75);

        SeleccionEstadistica arg = SeleccionEstadistica.builder()
                .jugador(jugador(1L, ARG, PosicionJugador.BASE, 10)).puntos(10).build();
        SeleccionEstadistica can = SeleccionEstadistica.builder()
                .jugador(jugador(2L, CAN, PosicionJugador.BASE, 10)).puntos(4).build();

        SeleccionPuntuacion.calcular(evento, List.of(arg, can));

        assertTrue(arg.getEquipoGano());
        assertFalse(can.getEquipoGano());
        assertTrue(arg.getFueMvp());
        assertFalse(can.getFueMvp());
        // Con victoria y MVP suma más que sus 10 puntos
        assertTrue(arg.getPuntajeFantasy() > 10 + 10);
        assertEquals(4.0, can.getPuntajeFantasy(), 0.001);
    }

    @Test
    void elCapitanSumaUnoPuntoCincoYQuienNoJugoSumaCero() {
        SeleccionParticipante p = SeleccionParticipante.builder()
                .base(jugador(1L, ARG, PosicionJugador.BASE, 10))
                .escolta(jugador(2L, ARG, PosicionJugador.ESCOLTA, 10))
                .alero(jugador(3L, CAN, PosicionJugador.ALERO, 10))
                .alaPivot(jugador(4L, CAN, PosicionJugador.ALA_PIVOT, 10))
                .pivot(jugador(5L, ARG, PosicionJugador.PIVOT, 10))
                .capitanId(1L)
                .build();

        double total = SeleccionPuntuacion.puntosDe(p, Map.of(1L, 20.0, 2L, 10.0, 3L, 5.5, 4L, -2.0));

        assertEquals(30 + 10 + 5.5 - 2, total, 0.001);
    }
}
