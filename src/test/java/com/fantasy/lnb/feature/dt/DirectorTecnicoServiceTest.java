package com.fantasy.lnb.feature.dt;

import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.jornada.EstadoPartido;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.Partido;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.feature.plantel.MotorPuntuacionPlantel;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
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
    private final EquipoReal boca = EquipoReal.builder().id(4L).sigla("BOC").build();
    private final EquipoReal obera = EquipoReal.builder().id(5L).sigla("OBE").build();
    private final EquipoReal quimsa = EquipoReal.builder().id(6L).sigla("QUI").build();

    private final Jornada j1 = Jornada.builder().id(1L).numero(1).build();
    private final Jornada j2 = Jornada.builder().id(2L).numero(2).build();
    private final Jornada j3 = Jornada.builder().id(3L).numero(3).build();

    @Test
    void promedioIncluyeLosPartidosProcesadosConMarcador() {
        DirectorTecnico dtLaUnion = DirectorTecnico.builder().equipoReal(laUnion).build();
        DirectorTecnico dtPlatense = DirectorTecnico.builder().equipoReal(platense).build();

        List<Partido> todos = List.of(
                // La Unión (visitante) gana por 4 → +5 ; Platense pierde por 4 → -2.5
                partido(j1, platense, laUnion, EstadoPartido.PROCESADO, 86, 90, 6),
                // La Unión (local) gana por 12 → +10
                partido(j2, laUnion, otro, EstadoPartido.PROCESADO, 92, 80, 13),
                // Finalizado pero sin marcador todavía: no cuenta
                partido(j3, laUnion, otro, EstadoPartido.FINALIZADO, null, null, 20),
                // Todavía no se jugó: ni siquiera se consulta
                partido(j3, laUnion, otro, EstadoPartido.PROGRAMADO, null, null, 21));

        actualizarPromedios(todos, dtLaUnion, dtPlatense);

        assertEquals(7.5, dtLaUnion.getPromedioFantasy()); // (5 + 10) / 2
        assertEquals(-2.5, dtPlatense.getPromedioFantasy());
    }

    @Test
    void siElEquipoJuegaDosVecesEnLaJornadaSoloCuentaElPrimerPartido() {
        DirectorTecnico dtBoca = DirectorTecnico.builder().equipoReal(boca).build();
        DirectorTecnico dtObera = DirectorTecnico.builder().equipoReal(obera).build();

        List<Partido> todos = List.of(
                // Boca gana por 23 → +15 ; Oberá pierde por 23 → -10 (primer partido de ambos)
                partido(j1, boca, obera, EstadoPartido.PROCESADO, 96, 73, 7),
                // Segundo partido de Boca en la jornada: pierde por 9 → -5, no cuenta
                partido(j1, quimsa, boca, EstadoPartido.PROCESADO, 105, 96, 10),
                // Segundo partido de Oberá: pierde por 9 → -5, no cuenta
                partido(j1, otro, obera, EstadoPartido.PROCESADO, 67, 58, 9));

        actualizarPromedios(todos, dtBoca, dtObera);

        assertEquals(15.0, dtBoca.getPromedioFantasy());   // no (15 - 5) / 2
        assertEquals(-10.0, dtObera.getPromedioFantasy()); // no (-10 - 5) / 2
    }

    @Test
    void elPromedioEsPorJornadaAunqueElEquipoJuegueDosVecesEnUnaDeEllas() {
        DirectorTecnico dtBoca = DirectorTecnico.builder().equipoReal(boca).build();

        List<Partido> todos = List.of(
                // J1: cuenta solo el primero (+15); el segundo (-5) se ignora
                partido(j1, boca, obera, EstadoPartido.PROCESADO, 96, 73, 7),
                partido(j1, quimsa, boca, EstadoPartido.PROCESADO, 105, 96, 10),
                // J2: único partido, gana por 3 → +5
                partido(j2, boca, otro, EstadoPartido.PROCESADO, 80, 77, 14));

        actualizarPromedios(todos, dtBoca);

        assertEquals(10.0, dtBoca.getPromedioFantasy()); // (15 + 5) / 2
    }

    @Test
    void sinPartidosConMarcadorElPromedioEsCero() {
        DirectorTecnico dtBoca = DirectorTecnico.builder().equipoReal(boca).promedioFantasy(9.0).build();

        actualizarPromedios(List.of(partido(j1, boca, obera, EstadoPartido.FINALIZADO, null, null, 7)), dtBoca);

        assertEquals(0.0, dtBoca.getPromedioFantasy());
    }

    private void actualizarPromedios(List<Partido> todos, DirectorTecnico... dts) {
        DirectorTecnicoRepository dtRepo = mock(DirectorTecnicoRepository.class);
        when(dtRepo.findAll()).thenReturn(List.of(dts));

        PartidoRepository partidoRepo = mock(PartidoRepository.class);
        when(partidoRepo.findByEstadoIn(any())).thenAnswer(inv -> {
            Collection<EstadoPartido> estados = inv.getArgument(0);
            return todos.stream().filter(p -> estados.contains(p.getEstado())).toList();
        });

        new DirectorTecnicoService(dtRepo, partidoRepo, new MotorPuntuacionPlantel(null))
                .actualizarPromediosDts();
    }

    private Partido partido(Jornada jornada, EquipoReal local, EquipoReal visitante, EstadoPartido estado,
            Integer puntosLocal, Integer puntosVisitante, int diaDeOctubre) {
        return Partido.builder()
                .jornada(jornada)
                .equipoLocal(local)
                .equipoVisitante(visitante)
                .estado(estado)
                .fechaHora(LocalDateTime.of(2026, 10, diaDeOctubre, 20, 30))
                .puntosLocal(puntosLocal)
                .puntosVisitante(puntosVisitante)
                .build();
    }
}
