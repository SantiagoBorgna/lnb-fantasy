package com.fantasy.lnb.feature.ranking;

import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.ranking.dto.PosicionGlobalDto;
import com.fantasy.lnb.feature.usuario.EquipoVirtual;
import com.fantasy.lnb.feature.usuario.EquipoVirtualRepository;
import com.fantasy.lnb.feature.usuario.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingServiceMiPosicionTest {

    private static final Long USUARIO_ID = 10L;

    @Mock
    private EquipoVirtualRepository equipoVirtualRepo;

    @InjectMocks
    private RankingService service;

    @Test
    void devuelveLaPosicionAunqueEsteFueraDelTop500() {
        EquipoReal club = new EquipoReal();
        club.setSigla("IND");
        club.setColorPrincipal("#C8102E");
        Usuario yo = Usuario.builder().id(USUARIO_ID).nombreDisplay("Santi").equipoFavorito(club).build();
        EquipoVirtual equipo = EquipoVirtual.builder().id(99L).nombre("Los del Barrio").puntajeGlobal(150.0).usuario(yo).build();

        when(equipoVirtualRepo.findByUsuario_Id(USUARIO_ID)).thenReturn(Optional.of(equipo));
        when(equipoVirtualRepo.countByPuntajeGlobalGreaterThan(150.0)).thenReturn(741L);

        PosicionGlobalDto p = service.obtenerMiPosicion(USUARIO_ID).orElseThrow();

        assertEquals(742, p.getPosicion());
        assertEquals("Los del Barrio", p.getNombreEquipo());
        assertEquals("Santi", p.getNombreUsuario());
        assertEquals("IND", p.getEquipoFavoritoSigla());
        assertEquals(150.0, p.getPuntajeGlobal());
        assertEquals(99L, p.getEquipoVirtualId());
    }

    @Test
    void sinEquipoNoDevuelvePosicion() {
        when(equipoVirtualRepo.findByUsuario_Id(USUARIO_ID)).thenReturn(Optional.empty());

        assertTrue(service.obtenerMiPosicion(USUARIO_ID).isEmpty());
    }
}
