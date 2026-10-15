package com.fantasy.lnb.feature.ranking;

import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.plantel.PlantelJornada;
import com.fantasy.lnb.feature.plantel.PlantelJornadaRepository;
import com.fantasy.lnb.feature.ranking.dto.MiResumenJornadaDto;
import com.fantasy.lnb.feature.usuario.EquipoVirtual;
import com.fantasy.lnb.feature.usuario.EquipoVirtualRepository;
import com.fantasy.lnb.feature.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingServiceMiResumenJornadaTest {

    private static final Long USUARIO_ID = 10L;
    private static final Long JORNADA_ID = 7L;

    @Mock
    private EquipoVirtualRepository equipoVirtualRepo;
    @Mock
    private PlantelJornadaRepository plantelJornadaRepo;

    @InjectMocks
    private RankingService service;

    private PlantelJornada plantel(Usuario usuario, Jornada jornada, Double puntaje) {
        return PlantelJornada.builder().usuario(usuario).jornada(jornada).puntajeObtenidoFecha(puntaje).build();
    }

    @Test
    void calculaPuestoEnLaFechaYGeneralSinTopeDeRanking() {
        Jornada jornada = Jornada.builder().id(JORNADA_ID).numero(5).estado(EstadoJornada.FINALIZADA).build();
        Usuario yo = Usuario.builder().id(USUARIO_ID).build();
        PlantelJornada miPlantel = plantel(yo, jornada, 80.0);

        when(plantelJornadaRepo.findByUsuario_IdAndJornada_IdAndTorneoIsNull(USUARIO_ID, JORNADA_ID))
                .thenReturn(Optional.of(miPlantel));
        // Dos sacaron más, uno empató, uno menos y uno sin puntaje cargado
        when(plantelJornadaRepo.findByJornada_IdAndTorneoIsNull(JORNADA_ID)).thenReturn(List.of(
                plantel(new Usuario(), jornada, 120.0),
                plantel(new Usuario(), jornada, 95.5),
                miPlantel,
                plantel(new Usuario(), jornada, 80.0),
                plantel(new Usuario(), jornada, 40.0),
                plantel(new Usuario(), jornada, null)));
        when(equipoVirtualRepo.findByUsuario_Id(USUARIO_ID))
                .thenReturn(Optional.of(EquipoVirtual.builder().nombre("Los del Barrio").puntajeGlobal(300.0).build()));
        when(equipoVirtualRepo.countByPuntajeGlobalGreaterThan(300.0)).thenReturn(41L);

        MiResumenJornadaDto r = service.obtenerMiResumenJornada(USUARIO_ID, JORNADA_ID).orElseThrow();

        assertEquals(5, r.getJornadaNumero());
        assertEquals("Los del Barrio", r.getNombreEquipo());
        assertEquals(80.0, r.getPuntajeFecha());
        assertEquals(3, r.getPosicionFecha());
        assertEquals(6, r.getTotalParticipantesFecha());
        assertEquals(42, r.getPosicionGlobal());
    }

    @Test
    void jornadaNoFinalizadaNoDevuelveResumen() {
        Jornada jornada = Jornada.builder().id(JORNADA_ID).numero(5).estado(EstadoJornada.EN_JUEGO).build();
        when(plantelJornadaRepo.findByUsuario_IdAndJornada_IdAndTorneoIsNull(USUARIO_ID, JORNADA_ID))
                .thenReturn(Optional.of(plantel(Usuario.builder().id(USUARIO_ID).build(), jornada, 50.0)));

        assertTrue(service.obtenerMiResumenJornada(USUARIO_ID, JORNADA_ID).isEmpty());
    }

    @Test
    void sinPlantelEnLaJornadaNoDevuelveResumen() {
        when(plantelJornadaRepo.findByUsuario_IdAndJornada_IdAndTorneoIsNull(USUARIO_ID, JORNADA_ID))
                .thenReturn(Optional.empty());

        assertTrue(service.obtenerMiResumenJornada(USUARIO_ID, JORNADA_ID).isEmpty());
    }
}
