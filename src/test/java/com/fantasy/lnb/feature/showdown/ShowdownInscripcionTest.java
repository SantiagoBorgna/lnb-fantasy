package com.fantasy.lnb.feature.showdown;

import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository;
import com.fantasy.lnb.feature.jornada.Partido;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.feature.mercado.JugadorReal;
import com.fantasy.lnb.feature.mercado.JugadorRealRepository;
import com.fantasy.lnb.feature.showdown.dto.ParticiparShowdownRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class ShowdownInscripcionTest {

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final String CODIGO = "abc123";

    private ShowdownEventoRepository eventoRepo;
    private ShowdownParticipanteRepository participanteRepo;
    private ShowdownService servicio;
    private ShowdownEvento evento;

    @BeforeEach
    void setUp() {
        eventoRepo = mock(ShowdownEventoRepository.class);
        participanteRepo = mock(ShowdownParticipanteRepository.class);
        JugadorRealRepository jugadorRepo = mock(JugadorRealRepository.class);
        servicio = new ShowdownService(eventoRepo, participanteRepo, jugadorRepo,
                mock(EstadisticaPartidoRepository.class), mock(PartidoRepository.class));

        evento = eventoConInicio(ahoraEnArgentina().plusHours(1));
        when(eventoRepo.findByCodigoInscripcion(CODIGO)).thenReturn(Optional.of(evento));
        when(jugadorRepo.findById(anyLong())).thenAnswer(inv -> {
            JugadorReal jugador = new JugadorReal();
            jugador.setId(inv.getArgument(0));
            jugador.setValorMercadoActual(10.0);
            return Optional.of(jugador);
        });
        when(participanteRepo.findByEventoIdAndUuidDispositivo(any(), any())).thenReturn(Optional.empty());
        when(participanteRepo.findFirstByEventoIdAndEmailIgnoreCase(any(), any())).thenReturn(Optional.empty());
        when(participanteRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void aceptaInscripcionAntesDelInicioYGuardaElEmailNormalizado() {
        servicio.participar(CODIGO, request(" Juan@Mail.com ", "cel-1"));

        verify(participanteRepo).save(argThat(p -> p.getEmail().equals("juan@mail.com")));
    }

    @Test
    void rechazaInscripcionSiElPartidoEmpezoAunqueElCronNoHayaCorrido() {
        evento.setPartido(partidoConInicio(ahoraEnArgentina().minusMinutes(1)));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> servicio.participar(CODIGO, request("juan@mail.com", "cel-1")));

        assertTrue(error.getMessage().contains("empezó"));
        verify(participanteRepo, never()).save(any());
    }

    @Test
    void getEventoMuestraElCierreAunqueElCronNoHayaCorrido() {
        evento.setPartido(partidoConInicio(ahoraEnArgentina().minusMinutes(1)));

        assertEquals(EstadoShowdown.EN_CURSO, servicio.getEvento(CODIGO).getEstado());
        assertEquals(EstadoShowdown.ABIERTO, evento.getEstado());
    }

    @Test
    void rechazaUnEmailQueYaParticipoDesdeOtroDispositivo() {
        when(participanteRepo.findFirstByEventoIdAndEmailIgnoreCase(evento.getId(), "juan@mail.com"))
                .thenReturn(Optional.of(participanteDe("cel-1")));

        assertThrows(IllegalStateException.class,
                () -> servicio.participar(CODIGO, request("JUAN@mail.com", "cel-2")));

        verify(participanteRepo, never()).save(any());
    }

    @Test
    void elMismoDispositivoNoChocaConSuPropioEmail() {
        when(participanteRepo.findFirstByEventoIdAndEmailIgnoreCase(evento.getId(), "juan@mail.com"))
                .thenReturn(Optional.of(participanteDe("cel-1")));

        servicio.participar(CODIGO, request("juan@mail.com", "cel-1"));

        verify(participanteRepo).save(any());
    }

    @Test
    void unHorarioSinConfirmarNoCierraElEventoDuranteEseDia() {
        LocalDate hoy = ahoraEnArgentina().toLocalDate();

        assertFalse(eventoConInicio(hoy.atStartOfDay()).partidoEmpezo());
        assertTrue(eventoConInicio(hoy.minusDays(1).atStartOfDay()).partidoEmpezo());
    }

    @Test
    void elCronPasaAEnCursoSoloLosEventosConElPartidoEmpezado() {
        ShowdownEvento empezado = eventoConInicio(ahoraEnArgentina().minusMinutes(1));
        when(eventoRepo.findAll()).thenReturn(List.of(evento, empezado));
        Partido partidoEmpezado = empezado.getPartido();
        partidoEmpezado.setEstado(com.fantasy.lnb.feature.jornada.EstadoPartido.PROGRAMADO);

        new ShowdownPuntuacionService(eventoRepo, participanteRepo, mock(EstadisticaPartidoRepository.class))
                .procesarPuntajesEventosEnCurso();

        assertEquals(EstadoShowdown.ABIERTO, evento.getEstado());
        assertEquals(EstadoShowdown.EN_CURSO, empezado.getEstado());
    }

    private static LocalDateTime ahoraEnArgentina() {
        return LocalDateTime.now(ARGENTINA);
    }

    private static ShowdownEvento eventoConInicio(LocalDateTime inicio) {
        return ShowdownEvento.builder()
                .id(1L)
                .codigoInscripcion(CODIGO)
                .estado(EstadoShowdown.ABIERTO)
                .partido(partidoConInicio(inicio))
                .build();
    }

    private static Partido partidoConInicio(LocalDateTime inicio) {
        return Partido.builder()
                .equipoLocal(new EquipoReal())
                .equipoVisitante(new EquipoReal())
                .fechaHora(inicio)
                .build();
    }

    private static ShowdownParticipante participanteDe(String uuidDispositivo) {
        return ShowdownParticipante.builder().uuidDispositivo(uuidDispositivo).build();
    }

    private static ParticiparShowdownRequest request(String email, String uuidDispositivo) {
        ParticiparShowdownRequest request = new ParticiparShowdownRequest();
        request.setNombre("Juan");
        request.setApellido("Perez");
        request.setEmail(email);
        request.setUuidDispositivo(uuidDispositivo);
        request.setCapitanId(1L);
        request.setBaseId(1L);
        request.setEscoltaId(2L);
        request.setAleroId(3L);
        request.setAlapivotId(4L);
        request.setPivotId(5L);
        return request;
    }
}
