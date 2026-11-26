package com.fantasy.lnb.feature.seleccion;

import com.fantasy.lnb.feature.mercado.PosicionJugador;
import com.fantasy.lnb.feature.showdown.EstadoShowdown;
import com.fantasy.lnb.feature.showdown.dto.ParticiparShowdownRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.fantasy.lnb.feature.seleccion.SeleccionTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class SeleccionInscripcionTest {

    private SeleccionEventoRepository eventoRepo;
    private SeleccionParticipanteRepository participanteRepo;
    private SeleccionService servicio;
    private SeleccionEvento evento;
    private final Map<Long, SeleccionJugador> jugadores = new HashMap<>();

    @BeforeEach
    void setUp() {
        eventoRepo = mock(SeleccionEventoRepository.class);
        participanteRepo = mock(SeleccionParticipanteRepository.class);
        SeleccionJugadorRepository jugadorRepo = mock(SeleccionJugadorRepository.class);
        servicio = new SeleccionService(eventoRepo, jugadorRepo, participanteRepo, mock(SeleccionEstadisticaRepository.class));

        evento = evento(ahora().plusHours(2));
        when(eventoRepo.findByCodigoInscripcion("arg-can")).thenReturn(Optional.of(evento));

        agregar(jugador(1L, ARG, PosicionJugador.BASE, 10));
        agregar(jugador(2L, ARG, PosicionJugador.ESCOLTA, 10));
        agregar(jugador(3L, CAN, PosicionJugador.ALERO, 10));
        agregar(jugador(4L, CAN, PosicionJugador.ALA_PIVOT, 10));
        agregar(jugador(5L, ARG, PosicionJugador.PIVOT, 10));
        agregar(jugador(6L, BAH, PosicionJugador.PIVOT, 5));     // no juega este partido
        agregar(jugador(7L, ARG, PosicionJugador.PIVOT, 10.5));  // se pasa del presupuesto
        when(jugadorRepo.findById(anyLong())).thenAnswer(inv -> Optional.ofNullable(jugadores.get((Long) inv.getArgument(0))));

        when(participanteRepo.findByEventoIdAndUuidDispositivo(any(), any())).thenReturn(Optional.empty());
        when(participanteRepo.findFirstByEventoIdAndEmailIgnoreCase(any(), any())).thenReturn(Optional.empty());
        when(participanteRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void agregar(SeleccionJugador j) {
        jugadores.put(j.getId(), j);
    }

    @Test
    void aceptaUnQuintetoValidoDeLosDosEquipos() {
        servicio.participar("arg-can", request(5L, 1L));

        verify(participanteRepo).save(argThat(p ->
                p.getEmail().equals("juan@mail.com") && p.getCapitanId() == 1L && p.getPivot().getId() == 5L));
    }

    @Test
    void rechazaUnJugadorDeOtraSeleccion() {
        var ex = assertThrows(IllegalArgumentException.class, () -> servicio.participar("arg-can", request(6L, 1L)));
        assertTrue(ex.getMessage().contains("no juega este partido"));
    }

    @Test
    void rechazaUnJugadorEnOtraPosicion() {
        ParticiparShowdownRequest r = request(5L, 1L);
        r.setBaseId(2L); // escolta en el lugar del base (y repetido)
        assertThrows(IllegalArgumentException.class, () -> servicio.participar("arg-can", r));
    }

    @Test
    void rechazaSiSePasaDelPresupuesto() {
        var ex = assertThrows(IllegalArgumentException.class, () -> servicio.participar("arg-can", request(7L, 1L)));
        assertTrue(ex.getMessage().contains("presupuesto"));
    }

    @Test
    void rechazaUnCapitanFueraDelQuinteto() {
        assertThrows(IllegalArgumentException.class, () -> servicio.participar("arg-can", request(5L, 7L)));
    }

    @Test
    void cierraLaInscripcionCuandoEmpiezaElPartido() {
        evento.setFechaHora(ahora().minusMinutes(1));
        assertThrows(IllegalStateException.class, () -> servicio.participar("arg-can", request(5L, 1L)));
        verify(participanteRepo, never()).save(any());
    }

    @Test
    void elEventoActivoEsElProximoAbierto() {
        SeleccionEvento jugado = evento(ahora().minusHours(3));
        jugado.setCodigoInscripcion("arg-bah");
        SeleccionEvento finalizado = evento(ahora().plusHours(1));
        finalizado.setEstado(EstadoShowdown.FINALIZADO);
        when(eventoRepo.findAllByOrderByFechaHoraAsc()).thenReturn(java.util.List.of(jugado, finalizado, evento));

        assertEquals("arg-can", servicio.getEventoActivo().orElseThrow().getCodigoInscripcion());
    }

    @Test
    void verificaElEmailAntesDeArmarElEquipo() {
        SeleccionParticipante otro = SeleccionParticipante.builder().uuidDispositivo("cel-otro").build();
        when(participanteRepo.findFirstByEventoIdAndEmailIgnoreCase(evento.getId(), "juan@mail.com")).thenReturn(Optional.of(otro));

        var ex = assertThrows(IllegalStateException.class,
                () -> servicio.verificarInscripcion("arg-can", " Juan@Mail.com ", "cel-1"));
        assertTrue(ex.getMessage().contains("email"));
        // El mismo dispositivo puede volver a entrar con su email
        assertDoesNotThrow(() -> servicio.verificarInscripcion("arg-can", "juan@mail.com", "cel-otro"));
    }

    @Test
    void laVerificacionTambienRechazaSiElPartidoEmpezo() {
        evento.setFechaHora(ahora().minusMinutes(1));
        assertThrows(IllegalStateException.class, () -> servicio.verificarInscripcion("arg-can", "nuevo@mail.com", "cel-1"));
    }

    @Test
    void elBannerSigueConElPartidoJugadoHastaQueSeApague() {
        SeleccionEvento jugado = evento(ahora().minusHours(3));
        jugado.setCodigoInscripcion("arg-bah");
        SeleccionEvento masViejo = evento(ahora().minusDays(2));
        masViejo.setCodigoInscripcion("viejo");
        when(eventoRepo.findAllByOrderByFechaHoraAsc()).thenReturn(java.util.List.of(masViejo, jugado));

        // Sin uno abierto, el último que ya empezó
        assertEquals("arg-bah", servicio.getEventoActivo().orElseThrow().getCodigoInscripcion());

        // Apagado a mano, pasa al anterior; con todos apagados no hay banner
        jugado.setMostrarBanner(false);
        assertEquals("viejo", servicio.getEventoActivo().orElseThrow().getCodigoInscripcion());
        masViejo.setMostrarBanner(false);
        assertTrue(servicio.getEventoActivo().isEmpty());
    }

    @Test
    void elBannerPrefiereElPartidoConLaInscripcionAbierta() {
        SeleccionEvento jugado = evento(ahora().minusHours(3));
        jugado.setCodigoInscripcion("arg-bah");
        when(eventoRepo.findAllByOrderByFechaHoraAsc()).thenReturn(java.util.List.of(jugado, evento));

        assertEquals("arg-can", servicio.getEventoActivo().orElseThrow().getCodigoInscripcion());
    }

    private static ParticiparShowdownRequest request(Long pivotId, Long capitanId) {
        ParticiparShowdownRequest r = new ParticiparShowdownRequest();
        r.setNombre("Juan");
        r.setApellido("Pérez");
        r.setEmail(" Juan@Mail.com ");
        r.setUuidDispositivo("cel-1");
        r.setBaseId(1L);
        r.setEscoltaId(2L);
        r.setAleroId(3L);
        r.setAlapivotId(4L);
        r.setPivotId(pivotId);
        r.setCapitanId(capitanId);
        return r;
    }
}
