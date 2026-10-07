package com.fantasy.lnb.feature.dt;

import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.jornada.EstadoPartido;
import com.fantasy.lnb.feature.jornada.Partido;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.feature.plantel.MotorPuntuacionPlantel;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DirectorTecnicoServiceTest {

    private final EquipoReal platense = EquipoReal.builder().id(1L).sigla("PLA").build();
    private final EquipoReal laUnion = EquipoReal.builder().id(2L).sigla("LAU").build();
    private final EquipoReal otro = EquipoReal.builder().id(3L).sigla("OTR").build();

    @Test
    void promedioIncluyeLosPartidosProcesadosConMarcador() {
        DirectorTecnico dtLaUnion = DirectorTecnico.builder().equipoReal(laUnion).build();
        DirectorTecnico dtPlatense = DirectorTecnico.builder().equipoReal(platense).build();

        List<Partido> todos = List.of(
                // La Unión (visitante) gana por 4 → +5 ; Platense pierde por 4 → -2.5
                partido(platense, laUnion, EstadoPartido.PROCESADO, 86, 90),
                // La Unión (local) gana por 12 → +10
                partido(laUnion, otro, EstadoPartido.PROCESADO, 92, 80),
                // Finalizado pero sin marcador todavía: no cuenta
                partido(laUnion, otro, EstadoPartido.FINALIZADO, null, null),
                // Todavía no se jugó: ni siquiera se consulta
                partido(laUnion, otro, EstadoPartido.PROGRAMADO, null, null));

        DirectorTecnicoRepository dtRepo = mock(DirectorTecnicoRepository.class);
        when(dtRepo.findAll()).thenReturn(List.of(dtLaUnion, dtPlatense));

        PartidoRepository partidoRepo = mock(PartidoRepository.class);
        when(partidoRepo.findByEstadoIn(any())).thenAnswer(inv -> {
            Collection<EstadoPartido> estados = inv.getArgument(0);
            return todos.stream().filter(p -> estados.contains(p.getEstado())).toList();
        });

        new DirectorTecnicoService(dtRepo, partidoRepo, new MotorPuntuacionPlantel(null))
                .actualizarPromediosDts();

        assertEquals(7.5, dtLaUnion.getPromedioFantasy()); // (5 + 10) / 2
        assertEquals(-2.5, dtPlatense.getPromedioFantasy());
    }

    private Partido partido(EquipoReal local, EquipoReal visitante, EstadoPartido estado,
            Integer puntosLocal, Integer puntosVisitante) {
        return Partido.builder()
                .equipoLocal(local)
                .equipoVisitante(visitante)
                .estado(estado)
                .puntosLocal(puntosLocal)
                .puntosVisitante(puntosVisitante)
                .build();
    }
}
