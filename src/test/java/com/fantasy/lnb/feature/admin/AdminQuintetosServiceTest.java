package com.fantasy.lnb.feature.admin;

import com.fantasy.lnb.feature.admin.dto.AdminQuintetosResponseDto;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.plantel.PlantelJornada;
import com.fantasy.lnb.feature.plantel.PlantelJornadaRepository;
import com.fantasy.lnb.feature.usuario.EquipoVirtual;
import com.fantasy.lnb.feature.usuario.EquipoVirtualRepository;
import com.fantasy.lnb.feature.usuario.Usuario;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminQuintetosServiceTest {

    private final PlantelJornadaRepository plantelRepo = mock(PlantelJornadaRepository.class);
    private final EstadisticaPartidoRepository estadisticaRepo = mock(EstadisticaPartidoRepository.class);
    private final EquipoVirtualRepository equipoVirtualRepo = mock(EquipoVirtualRepository.class);
    private final AdminQuintetosService service =
            new AdminQuintetosService(plantelRepo, estadisticaRepo, equipoVirtualRepo);

    @Test
    void incluyeLosDatosDeContactoDelUsuarioGanador() {
        Usuario usuario = Usuario.builder().id(42L).email("ganador@mail.com").nombreDisplay("Emiliano Rosell").build();
        PlantelJornada plantel = PlantelJornada.builder()
                .usuario(usuario)
                .jornada(Jornada.builder().id(1L).build())
                .puntajeObtenidoFecha(165.63)
                .build();
        when(plantelRepo.findFirstByJornada_IdAndTorneoIsNullOrderByPuntajeObtenidoFechaDesc(1L))
                .thenReturn(Optional.of(plantel));
        when(equipoVirtualRepo.findByUsuario_Id(42L))
                .thenReturn(Optional.of(EquipoVirtual.builder().nombre("Iroquai").build()));
        when(estadisticaRepo.findByJornada_Id(1L)).thenReturn(List.of());

        AdminQuintetosResponseDto respuesta = service.getQuintetosPorJornada(1L);

        assertEquals("Iroquai", respuesta.getNombreUsuarioGanador());
        assertEquals(42L, respuesta.getUsuarioIdGanador());
        assertEquals("ganador@mail.com", respuesta.getEmailUsuarioGanador());
        assertEquals("Emiliano Rosell", respuesta.getNombreDisplayUsuarioGanador());
        assertEquals(165.63, respuesta.getPuntajeUsuarioGanador());
    }

    @Test
    void sinPlantelesNoHayDatosDeGanador() {
        when(plantelRepo.findFirstByJornada_IdAndTorneoIsNullOrderByPuntajeObtenidoFechaDesc(1L))
                .thenReturn(Optional.empty());
        when(estadisticaRepo.findByJornada_Id(1L)).thenReturn(List.of());

        AdminQuintetosResponseDto respuesta = service.getQuintetosPorJornada(1L);

        assertEquals("N/A", respuesta.getNombreUsuarioGanador());
        assertNull(respuesta.getUsuarioIdGanador());
        assertNull(respuesta.getEmailUsuarioGanador());
    }
}
