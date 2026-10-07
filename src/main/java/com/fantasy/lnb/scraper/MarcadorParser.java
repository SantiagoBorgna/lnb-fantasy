package com.fantasy.lnb.scraper;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.Optional;

@Slf4j
public class MarcadorParser {

        private static final int TIMEOUT = 15_000;

        /**
         * Descarga la página principal del partido y extrae los puntajes de
         * local y visitante.
         *
         * Desde fines de septiembre de 2026 la LNB embebe el marcador directo
         * en la página principal (#score-local / #score-visitante) y eliminó el
         * iframe /laliga/partido/marcador/ que usaba este parser antes.
         */
        public static Optional<ResultadoPartido> extraerMarcador(String urlPartido) {
                try {
                        Document doc = Jsoup.connect(urlPartido)
                                        .userAgent("Mozilla/5.0 (compatible; LNBFantasyBot/1.0)")
                                        .timeout(TIMEOUT)
                                        .get();

                        return parsearMarcador(doc, urlPartido);

                } catch (IOException e) {
                        log.error("[MARCADOR] Error de conexión en {}: {}", urlPartido, e.getMessage());
                        return Optional.empty();
                }
        }

        static Optional<ResultadoPartido> parsearMarcador(Document doc, String urlPartido) {
                String textoLocal = doc.select("#score-local").text().trim();
                String textoVisitante = doc.select("#score-visitante").text().trim();

                if (textoLocal.isBlank() || textoVisitante.isBlank()) {
                        log.warn("[MARCADOR] Selectores no encontraron puntajes en: {}", urlPartido);
                        return Optional.empty();
                }

                try {
                        int puntosLocal = Integer.parseInt(textoLocal);
                        int puntosVisitante = Integer.parseInt(textoVisitante);

                        log.info("[MARCADOR] {} - {} | {}",
                                        puntosLocal, puntosVisitante, urlPartido);

                        return Optional.of(new ResultadoPartido(puntosLocal, puntosVisitante));
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