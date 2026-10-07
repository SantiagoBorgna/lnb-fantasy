package com.fantasy.lnb.scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarcadorParserTest {

    private static final String URL = "https://www.laliganacional.com.ar/laliga/partido/ID/platense-vs-la-union-fsa";

    // Recorte del HTML real de la página principal del partido (06/10/2026).
    private static final String HTML_PARTIDO = """
            <div class="contenedor-equipo-no-comenzado">
                <div class="nombre"><strong>PLATENSE</strong></div>
                <div id="score-local" class="marcador-box-movil  ">
                    86
                </div>
            </div>
            <div id="status-partido" class="banner-status-partido finalizado"><span>Finalizado</span></div>
            <div class="contenedor-equipo-no-comenzado visitante">
                <div class="nombre"><strong>LA UNION FSA</strong></div>
                <div id="score-visitante" class="marcador-box-movil ganador ">
                    90
                </div>
            </div>
            """;

    @Test
    void extraeElMarcadorDeLaPaginaPrincipal() {
        Document doc = Jsoup.parse(HTML_PARTIDO);

        Optional<MarcadorParser.ResultadoPartido> resultado = MarcadorParser.parsearMarcador(doc, URL);

        assertTrue(resultado.isPresent());
        assertEquals(86, resultado.get().puntosLocal());
        assertEquals(90, resultado.get().puntosVisitante());
        assertFalse(resultado.get().localGano());
    }

    @Test
    void sinElementosDeMarcadorDevuelveVacio() {
        Document doc = Jsoup.parse("<div class=\"otra-cosa\">sin marcador</div>");

        assertTrue(MarcadorParser.parsearMarcador(doc, URL).isEmpty());
    }

    @Test
    void puntajeNoNumericoDevuelveVacio() {
        Document doc = Jsoup.parse("""
                <div id="score-local">--</div>
                <div id="score-visitante">90</div>
                """);

        assertTrue(MarcadorParser.parsearMarcador(doc, URL).isEmpty());
    }
}
