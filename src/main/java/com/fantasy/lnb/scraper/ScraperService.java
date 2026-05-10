package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.estadisticas.EstadisticaPartido;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository;
import com.fantasy.lnb.feature.estadisticas.MotorPuntuacion;
import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.feature.mercado.JugadorReal;
import com.fantasy.lnb.feature.mercado.JugadorRealRepository;
import com.fantasy.lnb.scraper.dto.JugadorStatsDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// ATENCIÓN: Vas a necesitar importar las clases nuevas (Entidades y Repositorios).
// Hacé clic en las palabras subrayadas en rojo y usá "Ctrl + ." para importarlas.

@Slf4j
@Service
@RequiredArgsConstructor
public class ScraperService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Nuevas dependencias inyectadas por Spring Boot ──
    private final EstadisticaPartidoRepository estadisticaRepo;
    private final JugadorRealRepository jugadorRealRepo;
    private final JornadaRepository jornadaRepo;

    // Captura el primer argumento JSON de EstadisticasComponente(...)
    private static final Pattern STATS_PATTERN = Pattern.compile("EstadisticasComponente\\((\\{.*?\\}),\\s*'");

    /**
     * Descarga el HTML de la URL del partido, extrae los
     * <tr>
     * con stats
     * y los mapea a una lista de DTOs.
     */
    public List<JugadorStatsDto> extraerEstadisticasDePartido(String urlPartido) {

        // --- NUEVO: INTERCEPTOR DE IFRAMES ---
        // Si la URL es la "cáscara" general del partido, la transformamos para apuntar
        // directo a la tabla de estadísticas
        if (urlPartido.contains("/partido/") && !urlPartido.contains("/partido/estadisticas/")) {
            try {
                // Separamos la URL. Ejemplo: https://.../partido/ID_DEL_PARTIDO/nombres-equipos
                String[] partes = urlPartido.split("/partido/");
                String idYSong = partes[1]; // Queda: "bHi-NtwpQM_q0vQEtnKdLQ==/regatas-c-vs-ferro"
                String gesId = idYSong.split("/")[0]; // Queda: "bHi-NtwpQM_q0vQEtnKdLQ=="

                // Armamos la URL secreta del Iframe
                urlPartido = "https://www.laliganacional.com.ar/laliga/partido/estadisticas/" + gesId;
                log.info("[SCRAPER] URL detectada como cáscara. Re-escribiendo a la URL del iframe: {}", urlPartido);
            } catch (Exception e) {
                log.warn("[SCRAPER] Falló la re-escritura de URL, usando la original: {}", urlPartido);
            }
        }
        // --- FIN INTERCEPTOR ---

        List<JugadorStatsDto> resultado = new ArrayList<>();
        Document doc;

        try {
            doc = Jsoup.connect(urlPartido)
                    // Camuflaje de Chrome
                    .userAgent(
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
                    .header("Accept-Language", "es-ES,es;q=0.9")
                    .timeout(15_000)
                    .maxBodySize(0)
                    .get();
        } catch (IOException e) {
            log.error("[SCRAPER] Error al conectar con GES Deportiva. URL: {} | Error: {}", urlPartido, e.getMessage());
            return resultado;
        }

        Elements filas = doc.select("tr[onclick]");
        log.debug("[SCRAPER] Filas con onclick encontradas: {}", filas.size());

        for (Element fila : filas) {
            String onclickRaw = fila.attr("onclick");

            if (!onclickRaw.contains("EstadisticasComponente")) {
                continue;
            }

            Matcher matcher = STATS_PATTERN.matcher(onclickRaw);
            if (!matcher.find()) {
                log.warn("[SCRAPER] Fila con EstadisticasComponente pero sin JSON parseable: {}", onclickRaw);
                continue;
            }

            String jsonRaw = matcher.group(1).replace("&quot;", "\"");

            try {
                JugadorStatsDto dto = objectMapper.readValue(jsonRaw, JugadorStatsDto.class);
                // Log temporal
                log.info("¡Encontrado! {} - {} pts, {} ast", dto.getNombre(), dto.getPuntos(), dto.getAsistencias());
                resultado.add(dto);
            } catch (Exception e) {
                log.error("[SCRAPER] Error al parsear. JSON limpio: {} | Error: {}", jsonRaw, e.getMessage());
            }
        }

        log.info("[SCRAPER] Extracción completada. Jugadores parseados: {} | URL: {}", resultado.size(), urlPartido);
        return resultado;
    }

    /**
     * Versión final del método de persistencia.
     * Ya no recibe jornadaId hardcodeado — lo resuelve buscando
     * la jornada EN_JUEGO activa en el momento de la ejecución.
     *
     * @param dtos              Lista de stats scrapeadas del partido
     * @param gesPartidoId      ID del partido en GES Deportiva
     * @param fechaPartido      Timestamp real del partido
     * @param equipoLocalId     ID en nuestra BD del equipo local
     * @param equipoVisitanteId ID en nuestra BD del equipo visitante
     * @param equipoLocalGano   true si el local ganó
     */
    public void persistirEstadisticas(
            List<JugadorStatsDto> dtos,
            String gesPartidoId,
            LocalDateTime fechaPartido,
            Long equipoLocalId,
            Long equipoVisitanteId,
            boolean equipoLocalGano,
            Jornada jornada) {

        // ── Paso 1: Resolver la jornada activa ──────────────────────────────
        Jornada jornadaActiva = jornadaRepo
                .findByEstado(EstadoJornada.EN_JUEGO)
                .orElse(null);

        if (jornadaActiva == null) {
            log.warn("[SCRAPER] No hay jornada EN_JUEGO. " +
                    "Estadísticas del partido {} no serán persistidas.", gesPartidoId);
            return;
        }

        // ── Paso 2: Regla del fixture asimétrico ────────────────────────────
        // Verificamos AMBOS equipos antes de procesar un solo jugador.
        // Si cualquiera de los dos equipos ya tiene estadísticas en esta
        // jornada, el partido entero se descarta (es el segundo partido).
        boolean localYaProcesado = estadisticaRepo
                .existsByEquipoRealIdAndJornadaId(equipoLocalId, jornadaActiva.getId());
        boolean visitanteYaProcesado = estadisticaRepo
                .existsByEquipoRealIdAndJornadaId(equipoVisitanteId, jornadaActiva.getId());

        if (localYaProcesado || visitanteYaProcesado) {
            log.info("[FIXTURE] Partido {} ignorado — alguno de los equipos " +
                    "ya tiene estadísticas en jornada {}. Segundo partido descartado.",
                    gesPartidoId, jornadaActiva.getNumero());
            return;
        }

        // ── Paso 3: Procesar jugadores ──────────────────────────────────────
        int guardados = 0;
        int omitidos = 0;

        for (JugadorStatsDto dto : dtos) {

            // Filtro: descartar filas de totales (ID nulo = fila de equipo)
            if (dto.getIdJugador() == null) {
                omitidos++;
                continue;
            }

            // Buscar el jugador en nuestra BD por su ID de GES
            JugadorReal jugadorReal = jugadorRealRepo
                    .findByGesId(dto.getIdJugador())
                    .orElse(null);

            if (jugadorReal == null) {
                log.warn("[SCRAPER] GES ID {} ({}) no está en BD. Omitido.",
                        dto.getIdJugador(), dto.getNombre());
                omitidos++;
                continue;
            }

            // Doble chequeo a nivel jugador individual (salvaguarda de integridad)
            if (estadisticaRepo.existsByJugadorReal_IdAndJornada_Id(
                    jugadorReal.getId(), jornadaActiva.getId())) {
                log.warn("[SCRAPER] Jugador {} ya tiene stats en jornada {}. Omitido.",
                        dto.getNombre(), jornadaActiva.getNumero());
                omitidos++;
                continue;
            }

            // Determinar si el equipo del jugador ganó
            boolean esLocal = jugadorReal.getEquipoReal().getId().equals(equipoLocalId);
            boolean esteJugadorGano = esLocal ? equipoLocalGano : !equipoLocalGano;

            // Calcular puntaje Fantasy
            double puntaje = MotorPuntuacion.calcular(dto, esteJugadorGano);

            // Calcular tiros fallados para persistir
            int tirosCampoFallados = calcularTirosCampoFallados(dto);
            int tirosLibresFallados = safe(dto.getTirosLibres() != null
                    ? dto.getTirosLibres().getFallados()
                    : 0);

            // Construir y persistir
            EstadisticaPartido entidad = EstadisticaPartido.builder()
                    .jugadorReal(jugadorReal)
                    .jornada(jornadaActiva)
                    .gesPartidoId(gesPartidoId)
                    .fechaPartido(fechaPartido)
                    .puntos(safe(dto.getPuntos()))
                    .rebotesDefensivos(safe(dto.getReboteDefensivo()))
                    .rebotesOfensivos(safe(dto.getReboteOfensivo()))
                    .asistencias(safe(dto.getAsistencias()))
                    .recuperaciones(safe(dto.getRecuperaciones()))
                    .perdidas(safe(dto.getPerdidas()))
                    .faltasCometidas(safe(dto.getFaltaCometida()))
                    .faltasRecibidas(safe(dto.getFaltaRecibida()))
                    .taponesRealizados(safe(dto.getTaponesRealizados()))
                    .taponesRecibidos(safe(dto.getTaponesRecibidos()))
                    .tirosCampoFallados(tirosCampoFallados)
                    .tirosLibresFallados(tirosLibresFallados)
                    .fueExpulsadoPorFaltas(safe(dto.getFaltaCometida()) >= 5)
                    .tieneFaltaTecnica(false)
                    .fueDescalificado(false)
                    .fueTitularEnPartidoReal(Boolean.TRUE.equals(dto.getCincoInicial()))
                    .equipoGano(esteJugadorGano)
                    .puntajeFantasyCalculado(puntaje)
                    .creadoEn(LocalDateTime.now())
                    .build();

            estadisticaRepo.save(entidad);
            guardados++;

            log.info("[SCRAPER] ✓ {} | Jornada {} | Puntaje: {}",
                    dto.getNombre(), jornadaActiva.getNumero(), puntaje);
        }

        log.info("[SCRAPER] Partido {} procesado → Guardados: {} | Omitidos: {}",
                gesPartidoId, guardados, omitidos);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /**
     * Suma los fallados de TirosDos y TirosTres para obtener
     * el total de tiros de campo fallados (excluye libres).
     */
    private int calcularTirosCampoFallados(JugadorStatsDto dto) {
        int dosFallados = dto.getTirosDos() != null ? safe(dto.getTirosDos().getFallados()) : 0;
        int tresFallados = dto.getTirosTres() != null ? safe(dto.getTirosTres().getFallados()) : 0;
        return dosFallados + tresFallados;
    }

    private int safe(Integer v) {
        return v != null ? v : 0;
    }
}