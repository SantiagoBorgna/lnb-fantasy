package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.equipo.EquipoReal;
import com.fantasy.lnb.feature.equipo.EquipoRealRepository;
import com.fantasy.lnb.feature.mercado.EstadoJugador;
import com.fantasy.lnb.feature.mercado.JugadorReal;
import com.fantasy.lnb.feature.mercado.JugadorRealRepository;
import com.fantasy.lnb.feature.mercado.PosicionJugador;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class JugadorCrawlerService {

    private final JugadorRealRepository jugadorRepo;
    private final EquipoRealRepository equipoRepo;

    private static final String BASE_URL = "https://www.laliganacional.com.ar";
    private static final String URL_FIXTURE = BASE_URL + "/laliga/fixture";
    private static final int TIMEOUT_MS = 15_000;

    // 1. User-Agent realista para evadir el bloqueo de bots (Error 404/Empty)
    private static final String REAL_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    /**
     * Flujo completo:
     * 1. Página de fixture → URLs de planteles (/roster)
     * 2. Cada plantel → URLs de perfiles de jugadores
     * 3. Cada perfil → datos del jugador → persistir
     *
     * @return resumen de la sincronización
     */
    public SincronizacionResultado sincronizarJugadores() {
        log.info("[CRAWLER] Iniciando sincronización de jugadores...");

        Set<String> urlsEquipos = extraerUrlsEquipos();
        log.info("[CRAWLER] Equipos encontrados: {}", urlsEquipos.size());

        int nuevos = 0;
        int actualizados = 0;
        int errores = 0;

        for (String urlEquipo : urlsEquipos) {
            try {
                List<JugadorExtraido> jugadores = extraerJugadoresDeEquipo(urlEquipo);
                for (JugadorExtraido jugador : jugadores) {
                    try {
                        boolean esNuevo = persistirJugador(jugador);
                        if (esNuevo)
                            nuevos++;
                        else
                            actualizados++;
                    } catch (Exception e) {
                        log.error("[CRAWLER] Error persistiendo jugador {}: {}",
                                jugador.nombre, e.getMessage());
                        errores++;
                    }
                }
                // Pausa entre requests para no sobrecargar GES
                Thread.sleep(500);
            } catch (Exception e) {
                log.error("[CRAWLER] Error procesando equipo {}: {}",
                        urlEquipo, e.getMessage());
                errores++;
            }
        }

        log.info("[CRAWLER] Sincronización completada. Nuevos: {} | Actualizados: {} | Errores: {}",
                nuevos, actualizados, errores);

        return new SincronizacionResultado(nuevos, actualizados, errores);
    }

    // ── Paso 1: extraer URLs de equipos ─────────────────────────────────────
    private Set<String> extraerUrlsEquipos() {
        Set<String> urls = new LinkedHashSet<>(); // LinkedHashSet mantiene orden de inserción
        try {
            Document doc = Jsoup.connect(URL_FIXTURE)
                    .userAgent(REAL_USER_AGENT) // Usamos el User-Agent realista
                    .timeout(TIMEOUT_MS)
                    .get();

            // 2. Selector más robusto por atributo en lugar de clases css
            Elements links = doc.select("a[href*='/laliga/equipo/']");

            for (Element link : links) {
                String href = link.attr("href").trim();
                if (href.isBlank())
                    continue;

                // 3. Transformar la URL para ir a la lista de jugadores y no a la home del club
                href = href.replace("/inicio", "/roster");

                String url = href.startsWith("http") ? href : BASE_URL + href;
                urls.add(url); // Set elimina duplicados automáticamente
            }

            log.info("[CRAWLER] URLs únicas de equipos extraídas del fixture: {}", urls.size());

        } catch (IOException e) {
            log.error("[CRAWLER] Error cargando fixture para extraer equipos: {}", e.getMessage());
        }
        return urls;
    }

    // ── Paso 2: extraer jugadores de la página del equipo ───────────────────
    private List<JugadorExtraido> extraerJugadoresDeEquipo(String urlEquipo)
            throws IOException, InterruptedException {

        List<JugadorExtraido> resultado = new ArrayList<>();

        Document docEquipo = Jsoup.connect(urlEquipo)
                .userAgent(REAL_USER_AGENT) // Usamos el User-Agent realista
                .timeout(TIMEOUT_MS)
                .get();

        // Extraer nombre del equipo del título de la página
        String nombreEquipo = docEquipo.title().split("\\|")[0].trim();

        // Buscar links a perfiles de jugadores
        // Formato esperado: /laliga/jugador/{idLiga}/{idEquipo}/{idJugador}/{slug}
        Elements linksJugadores = docEquipo.select("a[href*='/laliga/jugador/']");

        for (Element link : linksJugadores) {
            String href = link.attr("href");
            String url = href.startsWith("http") ? href : BASE_URL + href;

            // Extraer gesId del path: /laliga/jugador/XXXX/YYYY/GESID/slug
            try {
                String[] partes = href.split("/");
                // Índice 5 es el gesId según el patrón de URL que describiste
                Long gesId = Long.parseLong(partes[5]);

                JugadorExtraido jugador = extraerPerfilJugador(url, gesId, nombreEquipo);
                if (jugador != null)
                    resultado.add(jugador);

                Thread.sleep(300); // Pausa entre perfiles
            } catch (Exception e) {
                log.warn("[CRAWLER] No se pudo parsear URL de jugador: {}", href);
            }
        }

        log.info("[CRAWLER] {} → {} jugadores encontrados", nombreEquipo, resultado.size());
        return resultado;
    }

    // ── Paso 3: extraer datos del perfil individual del jugador ─────────────
    private JugadorExtraido extraerPerfilJugador(String url, Long gesId, String nombreEquipo) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(REAL_USER_AGENT) // Usamos el User-Agent realista
                    .timeout(TIMEOUT_MS)
                    .get();

            // Selector confirmado con el HTML real de GES
            String nombre = doc.select("div.bloque-detalle-jugador h4 strong")
                    .text()
                    .toUpperCase()
                    .trim();

            if (nombre.isBlank()) {
                log.warn("[CRAWLER] Nombre vacío para gesId {} en URL: {}", gesId, url);
                nombre = "JUGADOR_" + gesId; // Fallback para no violar NOT NULL
            }

            return new JugadorExtraido(
                    gesId,
                    nombre,
                    0, // Número: sin datos en GES
                    PosicionJugador.DESCONOCIDO, // Posición: sin datos en GES
                    nombreEquipo,
                    url,
                    null, // Foto: usamos SVG
                    null // Fecha nacimiento: sin datos
            );

        } catch (IOException e) {
            log.error("[CRAWLER] Error cargando perfil gesId={} url={}: {}", gesId, url, e.getMessage());
            return null;
        }
    }

    // ── Persistencia ─────────────────────────────────────────────────────────
    // ── Persistencia ─────────────────────────────────────────────────────────
    private boolean persistirJugador(JugadorExtraido datos) {
        // Buscar o crear el EquipoReal automáticamente
        EquipoReal equipo = equipoRepo
                .findByNombreIgnoreCase(datos.nombreEquipo)
                .orElseGet(() -> {
                    log.info("[CRAWLER] Equipo '{}' no encontrado. Creándolo automáticamente...", datos.nombreEquipo);

                    EquipoReal nuevoEquipo = new EquipoReal();
                    nuevoEquipo.setNombre(datos.nombreEquipo);

                    // Armamos una sigla temporal con las primeras 3 letras
                    String siglaTemporal = datos.nombreEquipo.replaceAll("[^a-zA-Z]", "");
                    if (siglaTemporal.length() > 3)
                        siglaTemporal = siglaTemporal.substring(0, 3);
                    nuevoEquipo.setSigla(siglaTemporal.toUpperCase());

                    // 👇 ACÁ ESTÁ LA MAGIA SALVADORA 👇
                    nuevoEquipo.setColorPrincipal("#000000"); // Negro por defecto
                    nuevoEquipo.setColorSecundario("#FFFFFF"); // Blanco por defecto

                    return equipoRepo.save(nuevoEquipo);
                });

        if (equipo == null)
            return false;

        // Upsert: actualizar si existe, crear si no
        return jugadorRepo.findByGesId(datos.gesId)
                .map(existente -> {
                    // Actualizar datos que pueden cambiar
                    existente.setNombreCompleto(datos.nombre);
                    existente.setNumeroCamiseta(datos.numero);
                    existente.setEquipoReal(equipo);
                    existente.setPosicion(datos.posicion);
                    existente.setGesPerfilUrl(datos.perfilUrl);
                    existente.setFotoUrl(datos.fotoUrl);
                    if (datos.fechaNacimiento != null) {
                        existente.setFechaNacimiento(datos.fechaNacimiento);
                    }
                    jugadorRepo.save(existente);
                    return false; // No es nuevo
                })
                .orElseGet(() -> {
                    JugadorReal nuevo = JugadorReal.builder()
                            .gesId(datos.gesId)
                            .nombreCompleto(datos.nombre)
                            .numeroCamiseta(datos.numero)
                            .equipoReal(equipo)
                            .posicion(datos.posicion)
                            .estado(EstadoJugador.DISPONIBLE)
                            .valorMercadoActual(5.0)
                            .valorBase(5.0)
                            .gesPerfilUrl(datos.perfilUrl)
                            .fotoUrl(datos.fotoUrl)
                            .fechaNacimiento(datos.fechaNacimiento)
                            .build();
                    jugadorRepo.save(nuevo);
                    return true; // Es nuevo
                });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private PosicionJugador mapearPosicion(String texto) {
        // Ajustar según los valores reales que use GES
        if (texto.contains("BASE"))
            return PosicionJugador.BASE;
        if (texto.contains("ESCOLTA"))
            return PosicionJugador.ESCOLTA;
        if (texto.contains("ALERO") || texto.contains("ALA"))
            return PosicionJugador.ALERO;
        if (texto.contains("PIVOT") || texto.contains("PÍVOT"))
            return PosicionJugador.PIVOT;
        if (texto.contains("ALA-PÍVOT") || texto.contains("ALA PIVOT"))
            return PosicionJugador.ALA_PIVOT;
        log.warn("[CRAWLER] Posición no reconocida: '{}'. Asignando BASE por defecto.", texto);
        return PosicionJugador.BASE;
    }

    private LocalDate parsearFecha(String texto) {
        if (texto == null || texto.isBlank())
            return null;
        try {
            // Formato típico argentino: dd/MM/yyyy
            return LocalDate.parse(texto.trim(), DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) {
            return null;
        }
    }

    // ── Records internos ──────────────────────────────────────────────────────
    record JugadorExtraido(
            Long gesId,
            String nombre,
            Integer numero,
            PosicionJugador posicion,
            String nombreEquipo,
            String perfilUrl,
            String fotoUrl,
            LocalDate fechaNacimiento) {
    }

    public record SincronizacionResultado(
            int nuevos,
            int actualizados,
            int errores) {
    }
}