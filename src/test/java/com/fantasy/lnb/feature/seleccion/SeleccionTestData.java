package com.fantasy.lnb.feature.seleccion;

import com.fantasy.lnb.feature.mercado.PosicionJugador;
import com.fantasy.lnb.feature.showdown.EstadoShowdown;

import java.time.LocalDateTime;
import java.time.ZoneId;

final class SeleccionTestData {

    static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    static final SeleccionEquipo ARG = equipo(1L, "Argentina", "ARG");
    static final SeleccionEquipo CAN = equipo(2L, "Canadá", "CAN");
    static final SeleccionEquipo BAH = equipo(3L, "Bahamas", "BAH");

    private SeleccionTestData() {
    }

    static SeleccionEquipo equipo(Long id, String nombre, String sigla) {
        return SeleccionEquipo.builder().id(id).nombre(nombre).sigla(sigla)
                .colorPrincipal("#75AADB").colorSecundario("#FFFFFF").modeloCamiseta(1).build();
    }

    static SeleccionJugador jugador(Long id, SeleccionEquipo equipo, PosicionJugador posicion, double valor) {
        return SeleccionJugador.builder().id(id).equipo(equipo).nombreCompleto("Jugador " + id)
                .numeroCamiseta(id.intValue()).posicion(posicion).valor(valor).build();
    }

    static SeleccionEvento evento(LocalDateTime inicio) {
        return SeleccionEvento.builder().id(10L).codigoInscripcion("arg-can").local(ARG).visitante(CAN)
                .fechaHora(inicio).estado(EstadoShowdown.ABIERTO).build();
    }

    static LocalDateTime ahora() {
        return LocalDateTime.now(ARGENTINA);
    }
}
