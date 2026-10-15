package com.fantasy.lnb.scraper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScraperServiceUrlEstadisticasTest {

    @Test
    void laUrlDelPartidoDeLaLigaNacionalVaAlIframeDeEstadisticas() {
        assertEquals("https://www.laliganacional.com.ar/laliga/partido/estadisticas/bHi-NtwpQM_q0vQEtnKdLQ==",
                ScraperService.urlDeEstadisticas(
                        "https://www.laliganacional.com.ar/laliga/partido/bHi-NtwpQM_q0vQEtnKdLQ==/regatas-c-vs-ferro"));
    }

    @Test
    void enLaLigaArgentinaMantieneSuPrefijo() {
        assertEquals("https://www.laliganacional.com.ar/laligaargentina/partido/estadisticas/v2q9gIsi_eapElO8zvNO_84Utkc3IdXI9Ej6vtVwrdtVjpteBrZag13A==",
                ScraperService.urlDeEstadisticas(
                        "https://www.laliganacional.com.ar/laligaargentina/partido/v2q9gIsi_eapElO8zvNO_84Utkc3IdXI9Ej6vtVwrdtVjpteBrZag13A==/quilmes-mdp-vs-river"));
    }

    @Test
    void siYaEsLaDeEstadisticasNoLaToca() {
        String url = "https://www.laliganacional.com.ar/laligaargentina/partido/estadisticas/ABC==";
        assertEquals(url, ScraperService.urlDeEstadisticas(url));
    }
}
