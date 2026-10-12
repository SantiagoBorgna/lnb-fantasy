package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.equipo.EquipoRealRepository;
import com.fantasy.lnb.feature.jornada.EstadoPartido;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.jornada.Partido;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.scraper.FixtureCrawlerService.FixtureResultado;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Volver a correr el sync del fixture no debe duplicar partidos: los que ya
 * están cargados (mismos equipos, mismo día) solo se actualizan. El caso que
 * motivó esto es la LNB confirmando una hora que antes publicaba como 00:00.
 */
class FixtureCrawlerServiceTest {

    private static final LocalDateTime HORA_PENDIENTE = LocalDateTime.of(2026, 10, 23, 0, 0);
    private static final LocalDateTime HORA_CONFIRMADA = LocalDateTime.of(2026, 10, 23, 21, 0);

    private final EquipoReal racing = EquipoReal.builder().id(1L).nombre("RACING (CH)").sigla("RAC").build();
    private final EquipoReal boca = EquipoReal.builder().id(2L).nombre("BOCA").sigla("BOC").build();
    private final Jornada jornada = Jornada.builder().id(1L).numero(1).build();

    // "Base de datos" en memoria: lo que hay guardado entre una corrida y otra
    private final List<Partido> enBase = new ArrayList<>();
    private long proximoId = 1;
    private int cargasDeLaPagina = 0;

    private PartidoRepository partidoRepo;
    private FixtureCrawlerService crawler;

    @BeforeEach
    void setUp() {
        partidoRepo = mock(PartidoRepository.class);
        EquipoRealRepository equipoRepo = mock(EquipoRealRepository.class);
        when(equipoRepo.findByNombreIgnoreCase("RACING (CH)")).thenReturn(java.util.Optional.of(racing));
        when(equipoRepo.findByNombreIgnoreCase("BOCA")).thenReturn(java.util.Optional.of(boca));

        when(partidoRepo.findByEquiposYDia(anyLong(), anyLong(), any(), any())).thenAnswer(inv -> {
            Long local = inv.getArgument(0);
            Long visitante = inv.getArgument(1);
            LocalDateTime desde = inv.getArgument(2);
            LocalDateTime hasta = inv.getArgument(3);
            return enBase.stream()
                    .filter(p -> p.getEquipoLocal().getId().equals(local)
                            && p.getEquipoVisitante().getId().equals(visitante)
                            && !p.getFechaHora().isBefore(desde)
                            && p.getFechaHora().isBefore(hasta))
                    .sorted(Comparator.comparing(Partido::getId))
                    .toList();
        });
        when(partidoRepo.save(any(Partido.class))).thenAnswer(inv -> {
            Partido p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(proximoId++);
                enBase.add(p);
            }
            return p;
        });

        crawler = new FixtureCrawlerService(partidoRepo, mock(JornadaRepository.class), equipoRepo);
    }

    @Test
    void unPartidoQueNoEstabaSeCrea() {
        FixtureResultado r = crawler.procesarFilas(
                filas(fila("RACING (CH)", "BOCA", "23/10/2026 21:00")), jornada);

        assertEquals(new FixtureResultado(1, 0, 0, 0), r);
        assertEquals(1, enBase.size());
        Partido creado = enBase.get(0);
        assertEquals(HORA_CONFIRMADA, creado.getFechaHora());
        assertEquals(EstadoPartido.PROGRAMADO, creado.getEstado());
        assertEquals(jornada, creado.getJornada());
    }

    @Test
    void volverACorrerConLaHoraConfirmadaActualizaElPartidoYNoCreaOtro() {
        // 1ª carga: la LNB todavía no confirmó el horario y lo publica en 00:00
        assertEquals(new FixtureResultado(1, 0, 0, 0), crawler.procesarFilas(
                filas(fila("RACING (CH)", "BOCA", "23/10/2026 00:00")), jornada));
        assertEquals(1, enBase.size());
        Partido original = enBase.get(0);

        // 2ª carga: horario confirmado (y, como la LNB regenera el hash en cada
        // carga de la página, la fila trae un hash distinto al guardado)
        assertEquals(new FixtureResultado(0, 1, 0, 0), crawler.procesarFilas(
                filas(fila("RACING (CH)", "BOCA", "23/10/2026 21:00")), jornada));

        assertEquals(1, enBase.size(), "no debe haberse creado un partido nuevo");
        assertSame(original, enBase.get(0));
        assertEquals(HORA_CONFIRMADA, original.getFechaHora());

        // 3ª carga idéntica: no hay nada para hacer
        assertEquals(new FixtureResultado(0, 0, 1, 0), crawler.procesarFilas(
                filas(fila("RACING (CH)", "BOCA", "23/10/2026 21:00")), jornada));
        assertEquals(1, enBase.size());
        verify(partidoRepo, times(2)).save(any()); // creación + corrección de hora, nada más
    }

    @Test
    void alActualizarNoSePisanElGesHashNiLaUrl() {
        // Las estadísticas y Showdown buscan por el gesHash guardado en el partido
        Partido existente = partidoEnBase(HORA_PENDIENTE, EstadoPartido.PROGRAMADO);

        crawler.procesarFilas(filas(fila("RACING (CH)", "BOCA", "23/10/2026 21:00")), jornada);

        assertEquals("HASH-GUARDADO", existente.getGesHash());
        assertEquals("https://www.laliganacional.com.ar/laliga/partido/HASH-GUARDADO/racing-ch-vs-boca",
                existente.getGesUrl());
        assertEquals(HORA_CONFIRMADA, existente.getFechaHora());
    }

    @Test
    void unPartidoProcesadoNoSeToca() {
        Partido procesado = partidoEnBase(HORA_PENDIENTE, EstadoPartido.PROCESADO);

        FixtureResultado r = crawler.procesarFilas(
                filas(fila("RACING (CH)", "BOCA", "23/10/2026 21:00")), jornada);

        assertEquals(new FixtureResultado(0, 0, 1, 0), r);
        assertEquals(HORA_PENDIENTE, procesado.getFechaHora());
        verify(partidoRepo, never()).save(any());
    }

    @Test
    void unPartidoFinalizadoSinProcesarSiRecibeLaHoraCorrecta() {
        // La hora pendiente (00:00) lo pasó a FINALIZADO esa misma madrugada,
        // antes de que se jugara; el scraper lo va a procesar cuando haya acta
        Partido finalizado = partidoEnBase(HORA_PENDIENTE, EstadoPartido.FINALIZADO);

        FixtureResultado r = crawler.procesarFilas(
                filas(fila("RACING (CH)", "BOCA", "23/10/2026 21:00")), jornada);

        assertEquals(new FixtureResultado(0, 1, 0, 0), r);
        assertEquals(HORA_CONFIRMADA, finalizado.getFechaHora());
        assertEquals(EstadoPartido.FINALIZADO, finalizado.getEstado());
    }

    @Test
    void siYaHayDuplicadosActualizaElMasViejoYNoFallaLaSincronizacion() {
        // Duplicados que dejaron las corridas anteriores a identificar el partido por equipos + día
        Partido masViejo = partidoEnBase(HORA_PENDIENTE, EstadoPartido.PROGRAMADO);
        Partido duplicado = partidoEnBase(HORA_PENDIENTE, EstadoPartido.PROGRAMADO);

        FixtureResultado r = crawler.procesarFilas(
                filas(fila("RACING (CH)", "BOCA", "23/10/2026 21:00")), jornada);

        assertEquals(new FixtureResultado(0, 1, 0, 0), r);
        assertEquals(HORA_CONFIRMADA, masViejo.getFechaHora());
        assertEquals(HORA_PENDIENTE, duplicado.getFechaHora());
        assertEquals(2, enBase.size(), "los duplicados no se borran solos");
    }

    @Test
    void otroDiaEntreLosMismosEquiposEsOtroPartido() {
        // Series de playoffs: mismos equipos, otra fecha
        partidoEnBase(HORA_PENDIENTE, EstadoPartido.PROGRAMADO);

        FixtureResultado r = crawler.procesarFilas(
                filas(fila("RACING (CH)", "BOCA", "25/10/2026 21:00")), jornada);

        assertEquals(new FixtureResultado(1, 0, 0, 0), r);
        assertEquals(2, enBase.size());
    }

    @Test
    void unaFechaQueNoSePuedeLeerNoCreaUnPartidoConLaFechaDeHoy() {
        FixtureResultado r = crawler.procesarFilas(
                filas(fila("RACING (CH)", "BOCA", "23/10/2026 A CONFIRMAR")), jornada);

        assertEquals(new FixtureResultado(0, 0, 0, 1), r);
        assertEquals(0, enBase.size());
        verify(partidoRepo, never()).save(any());
    }

    @Test
    void unEquipoQueNoSeReconoceCuentaComoErrorYSigueConLasDemasFilas() {
        FixtureResultado r = crawler.procesarFilas(
                filas(fila("EQUIPO FANTASMA", "BOCA", "23/10/2026 21:00"),
                        fila("RACING (CH)", "BOCA", "23/10/2026 21:00")),
                jornada);

        assertEquals(new FixtureResultado(1, 0, 0, 1), r);
        assertEquals(1, enBase.size());
    }

    @Test
    void laFilaDeEncabezadoSeIgnora() {
        Elements conEncabezado = filas(
                "<tr><th colspan=\"2\">Local</th><th>Fecha/Hora - Campo</th><th>Estadísticas</th></tr>");

        assertEquals(new FixtureResultado(0, 0, 0, 0), crawler.procesarFilas(conEncabezado, jornada));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Partido partidoEnBase(LocalDateTime fechaHora, EstadoPartido estado) {
        Partido p = Partido.builder()
                .id(proximoId++)
                .jornada(jornada)
                .equipoLocal(racing)
                .equipoVisitante(boca)
                .gesHash("HASH-GUARDADO")
                .gesUrl("https://www.laliganacional.com.ar/laliga/partido/HASH-GUARDADO/racing-ch-vs-boca")
                .fechaHora(fechaHora)
                .estado(estado)
                .estadisticasProcesadas(estado == EstadoPartido.PROCESADO)
                .build();
        enBase.add(p);
        return p;
    }

    private static Elements filas(String... filas) {
        return Jsoup.parse("<table>" + String.join("", filas) + "</table>").select("tr");
    }

    /**
     * Fila con la misma estructura que devuelve el handler ProximosPartidos de
     * la LNB. El hash del link cambia en cada llamada, como en la página real.
     */
    private String fila(String local, String visitante, String fechaHora) {
        return """
                <tr class="fila-tabla-calendarios datos-estadisticos">
                  <td class="text-start celda-nombre"><a class="equipo" href="/laliga/equipo/1/1/x/inicio"><strong class="nombre-equipo">%s</strong></a></td>
                  <td class="resultados"><strong>0</strong></td>
                  <td class="resultados"><strong>0</strong></td>
                  <td class="text-end celda-nombre"><a class="equipo" href="/laliga/equipo/2/2/y/inicio"><strong class="nombre-equipo">%s</strong></a></td>
                  <td class="fecha-campo text-start"><strong>%s</strong><small>GRILON ARENA</small></td>
                  <td class="celda-estadisticas"><a class="btn-estadisticas" href="/laliga/partido/HASH-DE-LA-CARGA-%d==/x-vs-y" title="Ver estadísticas">stats</a></td>
                </tr>
                """.formatted(local, visitante, fechaHora, ++cargasDeLaPagina);
    }
}
