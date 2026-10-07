package com.fantasy.lnb.feature.plantel;

import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.jornada.EstadoPartido;
import com.fantasy.lnb.feature.jornada.Partido;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MotorPuntuacionPlantelDtTest {

    private static final LocalDateTime MARTES = LocalDateTime.of(2026, 10, 6, 20, 30);
    private static final LocalDateTime SABADO = MARTES.plusDays(4);

    private final MotorPuntuacionPlantel motor = new MotorPuntuacionPlantel(null);

    private final EquipoReal platense = EquipoReal.builder().id(1L).sigla("PLA").build();
    private final EquipoReal laUnion = EquipoReal.builder().id(2L).sigla("LAU").build();
    private final EquipoReal otro = EquipoReal.builder().id(3L).sigla("OTR").build();

    @Test
    void usaElMarcadorDelPartidoComoLocalOVisitante() {
        List<Partido> partidos = List.of(partido(platense, laUnion, MARTES, EstadoPartido.PROCESADO, 86, 90));

        assertEquals(Optional.of(5.0), motor.calcularPuntajeDtEnJornada(laUnion.getId(), partidos)); // gana por 4
        assertEquals(Optional.of(-2.5), motor.calcularPuntajeDtEnJornada(platense.getId(), partidos)); // pierde por 4
    }

    @Test
    void siJuegaDosVecesSoloCuentaElPrimerPartidoPorFecha() {
        // El del sábado viene primero en la lista, como si se hubiera cargado antes
        List<Partido> partidos = List.of(
                partido(laUnion, otro, SABADO, EstadoPartido.PROCESADO, 100, 70), // gana por 30 → +15
                partido(laUnion, platense, MARTES, EstadoPartido.PROCESADO, 90, 86)); // gana por 4 → +5

        assertEquals(Optional.of(5.0), motor.calcularPuntajeDtEnJornada(laUnion.getId(), partidos));
    }

    @Test
    void sinMarcadorElDtNiSumaNiRestaYNoSeSaltaAlSegundoPartido() {
        List<Partido> partidos = List.of(
                partido(laUnion, otro, MARTES, EstadoPartido.FINALIZADO, null, null),
                partido(laUnion, platense, SABADO, EstadoPartido.PROCESADO, 90, 86));

        assertTrue(motor.calcularPuntajeDtEnJornada(laUnion.getId(), partidos).isEmpty());
    }

    @Test
    void partidoProgramadoOAjenoNoCuenta() {
        List<Partido> partidos = List.of(
                partido(laUnion, otro, SABADO, EstadoPartido.PROGRAMADO, null, null),
                partido(platense, otro, MARTES, EstadoPartido.PROCESADO, 80, 70));

        assertTrue(motor.calcularPuntajeDtEnJornada(laUnion.getId(), partidos).isEmpty());
    }

    private Partido partido(EquipoReal local, EquipoReal visitante, LocalDateTime fecha, EstadoPartido estado,
            Integer puntosLocal, Integer puntosVisitante) {
        return Partido.builder()
                .equipoLocal(local)
                .equipoVisitante(visitante)
                .fechaHora(fecha)
                .estado(estado)
                .puntosLocal(puntosLocal)
                .puntosVisitante(puntosVisitante)
                .build();
    }
}
