package com.fantasy.lnb.feature.admin.mes;

import com.fantasy.lnb.feature.admin.mes.dto.MesResumenDto;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartido;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository;
import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.mercado.JugadorReal;
import com.fantasy.lnb.feature.mercado.PosicionJugador;
import com.fantasy.lnb.feature.plantel.JugadorPlantel;
import com.fantasy.lnb.feature.plantel.PlantelJornada;
import com.fantasy.lnb.feature.plantel.PlantelJornadaRepository;
import com.fantasy.lnb.feature.plantel.RolPlantel;
import com.fantasy.lnb.feature.usuario.EquipoVirtual;
import com.fantasy.lnb.feature.usuario.EquipoVirtualRepository;
import com.fantasy.lnb.feature.usuario.Usuario;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminMesServiceTest {

    private final MesFantasyRepository mesRepo = mock(MesFantasyRepository.class);
    private final JornadaRepository jornadaRepo = mock(JornadaRepository.class);
    private final PlantelJornadaRepository plantelRepo = mock(PlantelJornadaRepository.class);
    private final EstadisticaPartidoRepository estadisticaRepo = mock(EstadisticaPartidoRepository.class);
    private final EquipoVirtualRepository equipoVirtualRepo = mock(EquipoVirtualRepository.class);
    private final AdminMesService service =
            new AdminMesService(mesRepo, jornadaRepo, plantelRepo, estadisticaRepo, equipoVirtualRepo);

    private final Jornada j1 = jornada(1L, 5, EstadoJornada.FINALIZADA);
    private final Jornada j2 = jornada(2L, 6, EstadoJornada.EN_JUEGO);
    private final Jornada j3 = jornada(3L, 7, EstadoJornada.ABIERTA_A_CAMBIOS);

    private final Usuario ana = Usuario.builder().id(10L).nombreDisplay("Ana").build();
    private final Usuario beto = Usuario.builder().id(20L).nombreDisplay("Beto").build();

    private final JugadorReal base = jugador(100L, "PEREZ, JUAN", PosicionJugador.BASE);
    private final JugadorReal otroBase = jugador(101L, "GOMEZ, LUIS", PosicionJugador.BASE);
    private final JugadorReal pivot = jugador(200L, "DIAZ, PABLO", PosicionJugador.PIVOT);

    @Test
    void armaElResumenSoloConLasJornadasJugadas() {
        MesFantasy mes = MesFantasy.builder().id(1L).nombre("noviembre")
                .jornadaIds(new HashSet<>(Set.of(1L, 2L, 3L))).build();
        when(mesRepo.findById(1L)).thenReturn(Optional.of(mes));
        when(jornadaRepo.findAllById(any())).thenReturn(List.of(j3, j1, j2));

        // Ana: 100 + 80 = 180 (mejor fecha 100). Beto: 90 + 90 = 180 (mejor fecha 90) → gana Ana por desempate
        PlantelJornada anaJ1 = plantel(ana, j1, 100.0);
        PlantelJornada anaJ2 = plantel(ana, j2, 80.0,
                titular(base, RolPlantel.CAPITAN), titular(pivot, RolPlantel.TITULAR));
        PlantelJornada betoJ1 = plantel(beto, j1, 90.0);
        PlantelJornada betoJ2 = plantel(beto, j2, 90.0);
        when(plantelRepo.findByJornada_IdInAndTorneoIsNull(List.of(1L, 2L)))
                .thenReturn(List.of(anaJ1, anaJ2, betoJ1, betoJ2));
        when(equipoVirtualRepo.findByUsuario_IdIn(any())).thenReturn(List.of(
                EquipoVirtual.builder().nombre("Los Ana").usuario(ana).build(),
                EquipoVirtual.builder().nombre("Beto FC").usuario(beto).build()));

        when(estadisticaRepo.findByJornada_IdIn(List.of(1L, 2L))).thenReturn(List.of(
                stat(base, j1, 20.0), stat(base, j2, 30.0),     // promedio 25
                stat(otroBase, j2, 40.0),                        // 40 pero 1 partido (mínimo 1 de 2 jornadas → entra)
                stat(pivot, j1, 10.0), stat(pivot, j2, 12.0)));  // promedio 11

        MesResumenDto r = service.resumen(1L);

        assertEquals(3, r.getJornadas().size());
        assertEquals(List.of("Los Ana", "Beto FC"), r.getTabla().stream().map(MesResumenDto.PosicionMesDto::getNombreEquipo).toList());
        assertEquals(180.0, r.getTabla().get(0).getPuntos());
        assertEquals(2, r.getTabla().get(1).getPosicion());

        // Puntero: titulares de Ana en la jornada 6 (la última jugada), con su promedio del mes
        assertEquals(6, r.getPuntero().getJornadaNumero());
        assertEquals(180.0, r.getPuntero().getPuntajeMes());
        assertEquals(25.0, r.getPuntero().getJugadores().stream()
                .filter(j -> j.getEsCapitan()).findFirst().orElseThrow().getPuntosFantasy());
        assertEquals(11.0, r.getPuntero().getJugadores().stream()
                .filter(j -> !j.getEsCapitan()).findFirst().orElseThrow().getPuntosFantasy());

        // Promedios: mínimo = ceil(2/2) = 1
        assertEquals(1, r.getMinimoPartidos());
        assertEquals(List.of(101L, 100L, 200L), r.getMvp().stream().map(m -> m.getJugador().getId()).toList());
        assertEquals(25.0, r.getMvp().get(1).getPromedio());
        // Quinteto ideal: el mejor promedio de cada posición
        assertEquals(List.of(101L, 200L), r.getQuintetoIdeal().stream().map(m -> m.getJugador().getId()).toList());

        assertEquals(2, r.getGanadoresJornada().size());
        assertEquals("Los Ana", r.getGanadoresJornada().get(0).getNombreEquipo());
        assertEquals("Beto FC", r.getGanadoresJornada().get(1).getNombreEquipo());
    }

    @Test
    void conTresJornadasElMinimoEsDosPartidos() {
        Jornada j4 = jornada(4L, 8, EstadoJornada.FINALIZADA);
        MesFantasy mes = MesFantasy.builder().id(1L).nombre("dic").jornadaIds(new HashSet<>(Set.of(1L, 2L, 4L))).build();
        when(mesRepo.findById(1L)).thenReturn(Optional.of(mes));
        when(jornadaRepo.findAllById(any())).thenReturn(List.of(j1, j2, j4));
        when(plantelRepo.findByJornada_IdInAndTorneoIsNull(any())).thenReturn(List.of());
        when(estadisticaRepo.findByJornada_IdIn(any())).thenReturn(List.of(
                stat(base, j1, 20.0), stat(base, j2, 20.0), stat(pivot, j4, 50.0)));

        MesResumenDto r = service.resumen(1L);

        assertEquals(2, r.getMinimoPartidos());
        assertEquals(List.of(100L), r.getMvp().stream().map(m -> m.getJugador().getId()).toList());
        assertTrue(r.getTabla().isEmpty());
        assertNull(r.getPuntero());
    }

    private static Jornada jornada(Long id, int numero, EstadoJornada estado) {
        return Jornada.builder().id(id).numero(numero).estado(estado).build();
    }

    private static JugadorReal jugador(Long id, String nombre, PosicionJugador pos) {
        return JugadorReal.builder().id(id).nombreCompleto(nombre).posicion(pos).build();
    }

    private static EstadisticaPartido stat(JugadorReal jr, Jornada j, double puntaje) {
        return EstadisticaPartido.builder().jugadorReal(jr).jornada(j).puntajeFantasyCalculado(puntaje).build();
    }

    private static JugadorPlantel titular(JugadorReal jr, RolPlantel rol) {
        return JugadorPlantel.builder().jugadorReal(jr).rol(rol).build();
    }

    private static PlantelJornada plantel(Usuario u, Jornada j, double puntaje, JugadorPlantel... jugadores) {
        return PlantelJornada.builder().usuario(u).jornada(j).puntajeObtenidoFecha(puntaje)
                .jugadores(new ArrayList<>(List.of(jugadores))).build();
    }
}
