package com.fantasy.lnb.feature.plantel;

import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.torneo.TorneoEquipoRepository;
import com.fantasy.lnb.feature.usuario.EquipoVirtual;
import com.fantasy.lnb.feature.usuario.EquipoVirtualRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlantelServiceAccesoEquipoAjenoTest {

    private static final Long USUARIO_ID = 10L;
    private static final Long MI_EQUIPO_ID = 100L;
    private static final Long EQUIPO_RIVAL_ID = 200L;
    private static final Long TORNEO_ID = 5L;
    private static final Long JORNADA_ID = 7L;

    @Mock
    private EquipoVirtualRepository equipoVirtualRepo;
    @Mock
    private TorneoEquipoRepository torneoEquipoRepo;
    @Mock
    private JornadaRepository jornadaRepo;

    @InjectMocks
    private PlantelService service;

    @Test
    void quienNoParticipaDelTorneoNoPuedeVerElEquipoDeOtro() {
        when(equipoVirtualRepo.findByUsuario_Id(USUARIO_ID))
                .thenReturn(Optional.of(EquipoVirtual.builder().id(MI_EQUIPO_ID).build()));
        when(torneoEquipoRepo.existsByTorneo_IdAndEquipoVirtual_Id(TORNEO_ID, MI_EQUIPO_ID)).thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> service.obtenerPlantelAjeno(USUARIO_ID, EQUIPO_RIVAL_ID, JORNADA_ID, TORNEO_ID));
        assertThrows(IllegalStateException.class,
                () -> service.obtenerEstadisticasAjenas(USUARIO_ID, EQUIPO_RIVAL_ID, JORNADA_ID, TORNEO_ID));
        assertThrows(IllegalStateException.class,
                () -> service.obtenerPlantelAjenoActual(USUARIO_ID, EQUIPO_RIVAL_ID, TORNEO_ID));
        assertThrows(IllegalStateException.class,
                () -> service.obtenerEstadisticasAjenasActual(USUARIO_ID, EQUIPO_RIVAL_ID, TORNEO_ID));

        verifyNoInteractions(jornadaRepo);
    }

    @Test
    void sinTorneoNoSePuedeVerElEquipoDeOtro() {
        when(equipoVirtualRepo.findByUsuario_Id(USUARIO_ID))
                .thenReturn(Optional.of(EquipoVirtual.builder().id(MI_EQUIPO_ID).build()));

        assertThrows(IllegalStateException.class,
                () -> service.obtenerPlantelAjenoActual(USUARIO_ID, EQUIPO_RIVAL_ID, null));

        verifyNoInteractions(torneoEquipoRepo);
    }

    @Test
    void quienParticipaDelTorneoPasaElChequeo() {
        when(equipoVirtualRepo.findByUsuario_Id(USUARIO_ID))
                .thenReturn(Optional.of(EquipoVirtual.builder().id(MI_EQUIPO_ID).build()));
        when(torneoEquipoRepo.existsByTorneo_IdAndEquipoVirtual_Id(TORNEO_ID, MI_EQUIPO_ID)).thenReturn(true);
        when(jornadaRepo.findById(JORNADA_ID)).thenReturn(Optional.empty());

        // Pasa el chequeo de acceso y recién falla más adelante, al no encontrar la jornada
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.obtenerPlantelAjeno(USUARIO_ID, EQUIPO_RIVAL_ID, JORNADA_ID, TORNEO_ID));
        assertEquals("Jornada no encontrada", e.getMessage());
    }

    @Test
    void elEquipoPropioSiempreSePuedeConsultar() {
        when(equipoVirtualRepo.findByUsuario_Id(USUARIO_ID))
                .thenReturn(Optional.of(EquipoVirtual.builder().id(MI_EQUIPO_ID).build()));
        when(jornadaRepo.findById(JORNADA_ID)).thenReturn(Optional.empty());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.obtenerPlantelAjeno(USUARIO_ID, MI_EQUIPO_ID, JORNADA_ID, null));
        assertEquals("Jornada no encontrada", e.getMessage());

        verifyNoInteractions(torneoEquipoRepo);
    }
}
