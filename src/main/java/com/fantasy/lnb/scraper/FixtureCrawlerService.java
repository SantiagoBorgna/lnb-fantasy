package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.equipo.EquipoRealRepository;
import com.fantasy.lnb.feature.jornada.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FixtureCrawlerService {

        private final PartidoRepository partidoRepo;
        private final JornadaRepository jornadaRepo;
        private final EquipoRealRepository equipoRepo;

        private static final String BASE_URL = "https://www.laliganacional.com.ar";
        private static final int TIMEOUT = 15_000;

        // El User-Agent realista para evadir el bloqueo de bots
        private static final String REAL_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

        // Regex para extraer el hash de URLs como:
        // /laliga/partido/3VWwSYr-VWW2MsbRAzCHJVg==/la-union-fsa-vs-independiente-o
        private static final Pattern HASH_PATTERN = Pattern.compile(
                        "/laliga/partido/([^/]+)/[^/]+");

        /**
         * Escanea la URL del fixture/calendario de la LNB,
         * extrae todos los partidos y los persiste en la tabla `partido`.
         *
         * Se puede correr las veces que haga falta sobre el mismo rango de fechas:
         * un partido que ya está cargado no se duplica, se le corrige la hora si
         * cambió (ver {@link #procesarFilas}).
         *
         * @param jornadaId  ID de la jornada Fantasy a la que asignar los partidos nuevos
         * @param urlFixture URL de la página del fixture/calendario en GES
         */
        @Transactional
        public FixtureResultado sincronizarFixture(Long jornadaId, String urlFixture) {
                log.info("[FIXTURE] Escaneando jornada {}: {}", jornadaId, urlFixture);

                Jornada jornada = jornadaRepo.findById(jornadaId)
                                .orElseThrow(() -> new IllegalArgumentException("Jornada no encontrada: " + jornadaId));

                // 1. MAGIA PURA: Armamos la URL de la API oculta que descubriste en la consola
                // Network
                // Buscamos desde 2 días atrás hasta 2 meses en el futuro para abarcar todo
                java.time.LocalDate hoy = java.time.LocalDate.now();
                String fechaInicio = hoy.minusDays(2).toString();
                String fechaFin = hoy.plusMonths(2).toString();

                // Si la url base no tiene el handler, se lo inyectamos
                String apiUrl = urlFixture.contains("handler=") ? urlFixture
                                : urlFixture + "?handler=ProximosPartidos&fechaInicio=" + fechaInicio + "&fechaFin="
                                                + fechaFin;

                log.info("[FIXTURE] Interceptando API oculta: {}", apiUrl);

                Document doc;
                try {
                        doc = Jsoup.connect(apiUrl)
                                        .userAgent(REAL_USER_AGENT)
                                        .timeout(TIMEOUT)
                                        .get();
                } catch (IOException e) {
                        log.error("[FIXTURE] Error cargando API: {}", e.getMessage());
                        return new FixtureResultado(0, 0, 0, 1);
                }

                // 2. Como le pegamos a la API directa, la respuesta probablemente sean solo las
                // filas (<tr>)
                Elements filas = doc.select("tr");

                log.info("[FIXTURE] Filas totales detectadas en la API: {}", filas.size());

                FixtureResultado resultado = procesarFilas(filas, jornada);

                log.info("[FIXTURE] Sincronización terminada: {}", resultado);
                return resultado;
        }

        /**
         * Persiste las filas del fixture. Una fila es "el mismo partido" que uno ya
         * cargado si coinciden equipo local, equipo visitante y día (ver
         * {@link PartidoRepository#findByEquiposYDia}): el gesHash no sirve porque
         * la LNB lo regenera en cada carga, y la hora tampoco porque publica "00:00"
         * hasta que la confirma.
         *
         * Sobre un partido que ya existe solo se corrige la hora. El gesHash y la
         * gesUrl no se tocan: las estadísticas (y Showdown) se buscan por el gesHash
         * guardado en el partido, así que pisarlo dejaría huérfanas las ya cargadas.
         * Los partidos PROCESADOS no se modifican en absoluto.
         */
        FixtureResultado procesarFilas(Elements filas, Jornada jornada) {
                int nuevos = 0;
                int actualizados = 0;
                int sinCambios = 0;
                int errores = 0;

                for (Element fila : filas) {
                        try {
                                // 3. SELECTOR INFALIBLE: 'nombre-equipo' identifica sin ambigüedad
                                // el <strong> del nombre — a diferencia de 'text-start'/'text-end',
                                // que la celda de fecha comparte con la celda del equipo local y
                                // hacía que el nombre local quedara pegado a la fecha del partido
                                // (ej: "PEÑAROL (MDP) 30/09/2026 20:30").
                                Elements nombresEquipos = fila.select("strong.nombre-equipo");
                                String nombreLocal = nombresEquipos.size() > 0 ? nombresEquipos.get(0).text().trim() : "";
                                String nombreVisitante = nombresEquipos.size() > 1 ? nombresEquipos.get(1).text().trim() : "";

                                // Fallback por si la API cambió las clases
                                if (nombreLocal.isEmpty() || nombreVisitante.isEmpty()) {
                                        Elements celdasNombres = fila.select("td:has(strong)");
                                        if (celdasNombres.size() >= 2) {
                                                nombreLocal = celdasNombres.get(0).text().trim();
                                                nombreVisitante = celdasNombres.get(1).text().trim();
                                        } else {
                                                continue; // No es una fila de partido
                                        }
                                }

                                // Fecha
                                String fechaTexto = fila.select("td:contains(/)").text().trim();
                                if (fechaTexto.isEmpty()) {
                                        fechaTexto = fila.select("td[class*='fecha-campo']").text().trim();
                                }

                                String hrefPartido = fila.select("a[href*='/laliga/partido/']").attr("href");

                                if (hrefPartido.isBlank())
                                        continue;

                                Matcher m = HASH_PATTERN.matcher(hrefPartido);
                                if (!m.find())
                                        continue;

                                String hash = m.group(1);
                                String urlFull = hrefPartido.startsWith("http") ? hrefPartido : BASE_URL + hrefPartido;

                                if (fechaTexto.length() > 16) {
                                        fechaTexto = fechaTexto.substring(0, 16);
                                }

                                LocalDateTime fechaHora;
                                try {
                                        fechaHora = LocalDateTime.parse(fechaTexto,
                                                        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
                                } catch (Exception e) {
                                        // Sin fecha no hay forma de reconocer un partido ya cargado, y
                                        // guardarlo con la hora de la corrida lo dejaría con una fecha
                                        // falsa que después no coincide con la real.
                                        log.warn("[FIXTURE] Fecha no parseada: '{}' ({} vs {}). Se omite el partido.",
                                                        fechaTexto, nombreLocal, nombreVisitante);
                                        errores++;
                                        continue;
                                }

                                EquipoReal local = buscarEquipoPorNombre(nombreLocal);
                                EquipoReal visitante = buscarEquipoPorNombre(nombreVisitante);

                                if (local == null || visitante == null) {
                                        log.warn("[FIXTURE] Equipos no resueltos: '{}' vs '{}'", nombreLocal,
                                                        nombreVisitante);
                                        errores++;
                                        continue;
                                }

                                List<Partido> existentes = partidoRepo.findByEquiposYDia(
                                                local.getId(), visitante.getId(),
                                                fechaHora.toLocalDate().atStartOfDay(),
                                                fechaHora.toLocalDate().plusDays(1).atStartOfDay());

                                if (existentes.isEmpty()) {
                                        partidoRepo.save(Partido.builder()
                                                        .jornada(jornada)
                                                        .equipoLocal(local)
                                                        .equipoVisitante(visitante)
                                                        .gesHash(hash)
                                                        .gesUrl(urlFull)
                                                        .fechaHora(fechaHora)
                                                        .estado(EstadoPartido.PROGRAMADO)
                                                        .estadisticasProcesadas(false)
                                                        .build());
                                        log.info("[FIXTURE] Nuevo partido: {} vs {} ({})", local.getNombre(),
                                                        visitante.getNombre(), fechaHora);
                                        nuevos++;
                                        continue;
                                }

                                // Duplicados de corridas viejas (antes de identificar el partido por
                                // equipos + día): se actualiza el más antiguo y se avisa de los demás.
                                // Borrarlos a mano es decisión del admin: pueden tener estadísticas.
                                if (existentes.size() > 1) {
                                        log.warn("[FIXTURE] {} vs {} del {} está duplicado en la base (ids {}). "
                                                        + "Se actualiza solo el id {}; borrá los demás desde el admin.",
                                                        local.getNombre(), visitante.getNombre(), fechaHora.toLocalDate(),
                                                        existentes.stream().map(Partido::getId).toList(),
                                                        existentes.get(0).getId());
                                }

                                Partido existente = existentes.get(0);
                                LocalDateTime horaAnterior = existente.getFechaHora();

                                if (actualizarHora(existente, fechaHora)) {
                                        log.info("[FIXTURE] Hora actualizada: {} vs {} ({} -> {})", local.getNombre(),
                                                        visitante.getNombre(), horaAnterior, fechaHora);
                                        actualizados++;
                                } else {
                                        sinCambios++;
                                }

                        } catch (Exception e) {
                                log.error("[FIXTURE] Error en fila: {}", e.getMessage());
                                errores++;
                        }
                }

                return new FixtureResultado(nuevos, actualizados, sinCambios, errores);
        }

        /**
         * Corrige la hora de un partido ya cargado.
         *
         * @return true si el partido cambió (y se guardó)
         */
        private boolean actualizarHora(Partido existente, LocalDateTime fechaHora) {
                if (existente.getEstado() == EstadoPartido.PROCESADO
                                || fechaHora.equals(existente.getFechaHora())) {
                        return false;
                }

                existente.setFechaHora(fechaHora);
                partidoRepo.save(existente);
                return true;
        }

        // ── Extrae datos del partido desde el elemento del link ──────────────────
        private Partido extraerDatosPartido(
                        Element link, String hash, Jornada jornada) {

                String href = link.attr("href");
                String urlFull = href.startsWith("http") ? href : BASE_URL + href;

                // El slug del partido tiene formato: equipo-local-vs-equipo-visitante
                // Ejemplo: /la-union-fsa-vs-independiente-o
                // Intentamos identificar equipos desde el texto del link o el slug
                String textoLink = link.text();
                String[] partes = textoLink.split(" vs | VS ");

                EquipoReal local = null;
                EquipoReal visitante = null;

                if (partes.length == 2) {
                        local = buscarEquipoPorNombre(partes[0].trim());
                        visitante = buscarEquipoPorNombre(partes[1].trim());
                }

                // Si no se pudieron resolver los equipos desde el texto,
                // loguear para revisión manual
                if (local == null || visitante == null) {
                        log.warn("[FIXTURE] No se pudieron resolver los equipos para hash: {}. " +
                                        "Texto del link: '{}'. Revisar manualmente.",
                                        hash, textoLink);
                        return null;
                }

                // Fecha del partido — buscar en el contexto del elemento
                // (padre, hermano, etc. — depende del HTML real de GES)
                LocalDateTime fechaHora = extraerFechaPartido(link);

                return Partido.builder()
                                .jornada(jornada)
                                .equipoLocal(local)
                                .equipoVisitante(visitante)
                                .gesHash(hash)
                                .gesUrl(urlFull)
                                .fechaHora(fechaHora != null ? fechaHora : LocalDateTime.now())
                                .estado(EstadoPartido.PROGRAMADO)
                                .estadisticasProcesadas(false)
                                .build();
        }

        private EquipoReal buscarEquipoPorNombre(String nombre) {
                return equipoRepo.findByNombreIgnoreCase(nombre)
                                .orElseGet(() -> {
                                        // Buscar por sigla como fallback
                                        return equipoRepo.findAll().stream()
                                                        .filter(e -> nombre.toUpperCase()
                                                                        .contains(e.getSigla()))
                                                        .findFirst()
                                                        .orElse(null);
                                });
        }

        private LocalDateTime extraerFechaPartido(Element link) {
                // Intentar extraer la fecha del contexto del elemento
                // Ajustar el selector según el HTML real de GES
                try {
                        Element padre = link.parent();
                        String textoFecha = padre != null
                                        ? padre.select(".fechaPartido, .fecha").text()
                                        : "";

                        if (!textoFecha.isBlank()) {
                                return LocalDateTime.parse(textoFecha.trim(),
                                                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
                        }
                } catch (Exception e) {
                        log.debug("[FIXTURE] No se pudo parsear fecha: {}", e.getMessage());
                }
                return null;
        }

        /**
         * @param nuevos       partidos que no estaban en la base y se crearon
         * @param actualizados partidos ya cargados a los que se les corrigió la hora
         * @param sinCambios   partidos ya cargados que no necesitaban nada
         * @param errores      filas que no se pudieron procesar (equipo o fecha no resueltos)
         */
        public record FixtureResultado(int nuevos, int actualizados, int sinCambios, int errores) {
        }
}