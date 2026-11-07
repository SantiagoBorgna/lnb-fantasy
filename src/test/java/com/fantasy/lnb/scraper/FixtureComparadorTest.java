package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.jornada.EstadoPartido;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.Partido;
import com.fantasy.lnb.scraper.FixtureComparador.DiferenciaFixture;
import com.fantasy.lnb.scraper.FixtureComparador.TipoDiferencia;
import com.fantasy.lnb.scraper.FixtureCrawlerService.PartidoPublicado;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixtureComparadorTest {

    private static final LocalDateTime DIA_6 = LocalDateTime.of(2026, 11, 6, 21, 35);
    private static final LocalDateTime DIA_10 = LocalDateTime.of(2026, 11, 10, 22, 5);

    private final EquipoReal boca = equipo(1L, "BOCA");
    private final EquipoReal ferro = equipo(2L, "FERRO");
    private final EquipoReal sanMartin = equipo(3L, "SAN MARTIN (C)");

    private final Jornada j5 = jornada(5L, 5, "2026-10-31T11:00:00", "2026-11-06T23:59:00", EstadoJornada.EN_JUEGO);
    private final Jornada j6 = jornada(6L, 6, "2026-11-08T19:00:00", "2026-11-13T23:59:00", EstadoJornada.ABIERTA_A_CAMBIOS);
    private final Set<Long> controladas = Set.of(5L, 6L);

    @Test
    void elCasoDeLaJornada5AvisaElPartidoNuevoYElQueYaNoFigura() {
        // Ferro vs San Martín pasó al 10/11, donde ya estaba cargado en la J6,
        // y en su lugar del 06/11 se jugó Boca vs San Martín.
        Partido viejo = partido(96L, ferro, sanMartin, DIA_6, EstadoPartido.PROGRAMADO, j5);
        Partido enJ6 = partido(128L, ferro, sanMartin, DIA_10, EstadoPartido.PROGRAMADO, j6);

        List<DiferenciaFixture> r = comparar(
                List.of(publicado(boca, sanMartin, DIA_6), publicado(ferro, sanMartin, DIA_10)),
                List.of(viejo, enJ6));

        assertEquals(2, r.size());

        DiferenciaFixture nuevo = r.get(0);
        assertEquals(TipoDiferencia.NUEVO, nuevo.tipo());
        assertEquals("BOCA", nuevo.local());
        assertEquals(5, nuevo.jornada());
        assertEquals(DIA_6, nuevo.fechaLiga());

        DiferenciaFixture noFigura = r.get(1);
        assertEquals(TipoDiferencia.NO_PUBLICADO, noFigura.tipo());
        assertEquals("FERRO", noFigura.local());
        assertEquals(96L, noFigura.partidoId());
    }

    @Test
    void unPartidoIgualNoEsUnaDiferencia() {
        Partido partido = partido(1L, boca, ferro, DIA_6, EstadoPartido.PROGRAMADO, j5);

        assertTrue(comparar(List.of(publicado(boca, ferro, DIA_6)), List.of(partido)).isEmpty());
    }

    @Test
    void unPartidoCargadoQueCambioDeDiaEsUnaSolaDiferencia() {
        Partido partido = partido(1L, boca, ferro, DIA_6, EstadoPartido.PROGRAMADO, j5);

        List<DiferenciaFixture> r = comparar(List.of(publicado(boca, ferro, DIA_10)), List.of(partido));

        assertEquals(1, r.size());
        assertEquals(TipoDiferencia.REPROGRAMADO, r.get(0).tipo());
        assertEquals(DIA_6, r.get(0).fechaBase());
        assertEquals(DIA_10, r.get(0).fechaLiga());
    }

    @Test
    void unCambioDeHoraElMismoDiaSeAvisa() {
        Partido partido = partido(1L, boca, ferro, DIA_6, EstadoPartido.PROGRAMADO, j5);

        List<DiferenciaFixture> r = comparar(List.of(publicado(boca, ferro, DIA_6.withHour(19))), List.of(partido));

        assertEquals(1, r.size());
        assertEquals(TipoDiferencia.HORARIO, r.get(0).tipo());
    }

    @Test
    void unHorarioConfirmadoSobreUnPartidoEn0000SeAvisa() {
        Partido partido = partido(1L, boca, ferro, DIA_6.withHour(0).withMinute(0), EstadoPartido.PROGRAMADO, j5);

        List<DiferenciaFixture> r = comparar(List.of(publicado(boca, ferro, DIA_6)), List.of(partido));

        assertEquals(1, r.size());
        assertEquals(TipoDiferencia.HORARIO, r.get(0).tipo());
    }

    @Test
    void laHoraPendienteDeLaLigaNoEsUnCambio() {
        Partido partido = partido(1L, boca, ferro, DIA_6, EstadoPartido.PROGRAMADO, j5);

        assertTrue(comparar(List.of(publicado(boca, ferro, DIA_6.withHour(0).withMinute(0))), List.of(partido)).isEmpty());
    }

    @Test
    void unPartidoProcesadoNoGeneraAvisos() {
        Partido conOtraHora = partido(1L, boca, ferro, DIA_6, EstadoPartido.PROCESADO, j5);
        Partido queYaNoFigura = partido(2L, ferro, sanMartin, DIA_6, EstadoPartido.PROCESADO, j5);

        assertTrue(comparar(List.of(publicado(boca, ferro, DIA_6.withHour(19))), List.of(conOtraHora, queYaNoFigura)).isEmpty());
    }

    @Test
    void unaJornadaSinPartidosCargadosNoGeneraAvisos() {
        Jornada j7 = jornada(7L, 7, "2026-11-14T11:00:00", "2026-11-21T23:59:00", EstadoJornada.ABIERTA_A_CAMBIOS);
        LocalDateTime enJ7 = LocalDateTime.of(2026, 11, 15, 21, 0);

        List<DiferenciaFixture> r = FixtureComparador.comparar(
                List.of(publicado(boca, ferro, enJ7)), List.of(), List.of(j5, j6, j7), controladas);

        assertTrue(r.isEmpty());
    }

    @Test
    void unPartidoFueraDeTodaJornadaSeAvisaSinJornada() {
        LocalDateTime entreJornadas = LocalDateTime.of(2026, 11, 7, 21, 0);

        List<DiferenciaFixture> r = comparar(List.of(publicado(boca, ferro, entreJornadas)), List.of());

        assertEquals(1, r.size());
        assertEquals(TipoDiferencia.NUEVO, r.get(0).tipo());
        assertNull(r.get(0).jornada());
    }

    @Test
    void unaFilaIlegibleSeAvisaConLosNombresQueTrae() {
        PartidoPublicado ilegible = new PartidoPublicado(null, ferro, "EQUIPO FANTASMA", "FERRO", DIA_6);

        List<DiferenciaFixture> r = comparar(List.of(ilegible), List.of());

        assertEquals(1, r.size());
        assertEquals(TipoDiferencia.NO_RECONOCIDO, r.get(0).tipo());
        assertEquals("EQUIPO FANTASMA", r.get(0).local());
    }

    @Test
    void unPartidoCargadoEnOtraJornadaNoEsNuevo() {
        Partido enOtraJornada = partido(1L, boca, ferro, DIA_6, EstadoPartido.PROGRAMADO, j6);

        assertTrue(comparar(List.of(publicado(boca, ferro, DIA_6)), List.of(enOtraJornada)).isEmpty());
    }

    private List<DiferenciaFixture> comparar(List<PartidoPublicado> publicados, List<Partido> partidos) {
        return FixtureComparador.comparar(publicados, partidos, List.of(j5, j6), controladas);
    }

    private static PartidoPublicado publicado(EquipoReal local, EquipoReal visitante, LocalDateTime fecha) {
        return new PartidoPublicado(local, visitante, local.getNombre(), visitante.getNombre(), fecha);
    }

    private static Partido partido(Long id, EquipoReal local, EquipoReal visitante, LocalDateTime fecha,
            EstadoPartido estado, Jornada jornada) {
        return Partido.builder().id(id).equipoLocal(local).equipoVisitante(visitante)
                .fechaHora(fecha).estado(estado).jornada(jornada).build();
    }

    private static EquipoReal equipo(Long id, String nombre) {
        return EquipoReal.builder().id(id).nombre(nombre).sigla(nombre.substring(0, 3)).build();
    }

    private static Jornada jornada(Long id, int numero, String inicio, String fin, EstadoJornada estado) {
        return Jornada.builder().id(id).numero(numero).estado(estado)
                .fechaInicio(LocalDateTime.parse(inicio)).fechaFin(LocalDateTime.parse(fin)).build();
    }
}
