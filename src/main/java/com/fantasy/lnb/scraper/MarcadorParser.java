package com.fantasy.lnb.scraper;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.Optional;

@Slf4j
public class MarcadorParser {

        private static final String BASE_URL = "https://www.laliganacional.com.ar";
        private static final int TIMEOUT = 15_000;

        /**
         * Dado el HTML de la página principal del partido,
         * encuentra el iframe del marcador, hace un segundo request
         * y extrae los puntajes de local y visitante.
         */
        public static Optional<ResultadoPartido> extraerMarcador(String urlPartido) {
                try {
                        // Request 1: página principal del partido
                        Document docPrincipal = Jsoup.connect(urlPartido)
                                        .userAgent("Mozilla/5.0 (compatible; LNBFantasyBot/1.0)")
                                        .timeout(TIMEOUT)
                                        .get();

                        // Extraer el src del iframe del marcador
                        Element iframe = docPrincipal
                                        .select("iframe[src*='/laliga/partido/marcador/']")
                                        .first();

                        if (iframe == null) {
                                log.warn("[MARCADOR] No se encontró iframe en: {}", urlPartido);
                                return Optional.empty();
                        }

                        String srcIframe = iframe.attr("src");
                        String urlMarcador = srcIframe.startsWith("http")
                                        ? srcIframe
                                        : "https://www.laliganacional.com.ar" + srcIframe;

                        // Request 2: HTML estático del iframe — sin AJAX
                        Document docMarcador = Jsoup.connect(urlMarcador)
                                        .userAgent("Mozilla/5.0 (compatible; LNBFantasyBot/1.0)")
                                        .timeout(TIMEOUT)
                                        .get();

                        // Selectores confirmados con el HTML real
                        String textoLocal = docMarcador
                                        .select("div.marcadorLocal strong.puntos")
                                        .text().trim();

                        String textoVisitante = docMarcador
                                        .select("div.marcadorVisitante strong.puntos")
                                        .text().trim();

                        if (textoLocal.isBlank() || textoVisitante.isBlank()) {
                                log.warn("[MARCADOR] Selectores no encontraron puntajes. " +
                                                "URL iframe: {}", urlMarcador);
                                return Optional.empty();
                        }

                        int puntosLocal = Integer.parseInt(textoLocal);
                        int puntosVisitante = Integer.parseInt(textoVisitante);

                        log.info("[MARCADOR] {} - {} | {}",
                                        puntosLocal, puntosVisitante, urlPartido);

                        return Optional.of(new ResultadoPartido(puntosLocal, puntosVisitante));

                } catch (IOException e) {
                        log.error("[MARCADOR] Error de conexión en {}: {}", urlPartido, e.getMessage());
                        return Optional.empty();
                } catch (NumberFormatException e) {
                        log.error("[MARCADOR] Puntaje no numérico en {}: {}", urlPartido, e.getMessage());
                        return Optional.empty();
                }
        }

        public record ResultadoPartido(int puntosLocal, int puntosVisitante) {
                public boolean localGano() {
                        return puntosLocal > puntosVisitante;
                }

                public int diferencia() {
                        return Math.abs(puntosLocal - puntosVisitante);
                }
        }

        private MarcadorParser() {
        }
}