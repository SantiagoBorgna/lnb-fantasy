package com.fantasy.lnb.scraper;

import com.fantasy.lnb.feature.estadisticas.EstadisticaPartido;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository;
import com.fantasy.lnb.feature.estadisticas.MotorPuntuacion;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
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
     * Convierte una lista de DTOs scrapeados en entidades persistidas.
     * Aplica el motor de puntuación y respeta la regla de unicidad.
     */
    public void persistirEstadisticas(
            List<JugadorStatsDto> dtos,
            String gesPartidoId,
            LocalDateTime fechaPartido,
            boolean equipoLocalGano,
            Long jornadaId) {

        Jornada jornada = jornadaRepo.findById(jornadaId)
                .orElseThrow(() -> new IllegalArgumentException("Jornada no encontrada: " + jornadaId));

        int guardados = 0;
        int omitidos = 0;

        for (JugadorStatsDto dto : dtos) {
            // ── Filtro 1: descartar filas de totales (ID nulo = fila de equipo) ──
            if (dto.getIdJugador() == null) {
                log.debug("[SCRAPER] Fila de totales omitida (IdJugador null)");
                omitidos++;
                continue;
            }

            // ── Filtro 2: buscar el JugadorReal en nuestra BD por su ID de GES ──
            JugadorReal jugadorReal = jugadorRealRepo.findByGesId(dto.getIdJugador()).orElse(null);

            if (jugadorReal == null) {
                log.warn("[SCRAPER] Jugador GES ID {} ({}) no encontrado en BD. Omitido.", dto.getIdJugador(),
                        dto.getNombre());
                omitidos++;
                continue;
            }

            // ── Filtro 3: regla del primer partido cronológico ──
            if (estadisticaRepo.existsByJugadorReal_IdAndJornada_Id(jugadorReal.getId(), jornadaId)) {
                log.info("[SCRAPER] Jugador {} ya tiene stats en jornada {}. Segundo partido ignorado.",
                        dto.getNombre(), jornadaId);
                omitidos++;
                continue;
            }

            boolean esteJugadorGano = equipoLocalGano;

            // ── Extracción segura de tiros fallados ──
            int tiros2Fallados = dto.getTirosDos() != null ? safe(dto.getTirosDos().getFallados()) : 0;
            int tiros3Fallados = dto.getTirosTres() != null ? safe(dto.getTirosTres().getFallados()) : 0;
            int tirosLibresFallados = dto.getTirosLibres() != null ? safe(dto.getTirosLibres().getFallados()) : 0;
            int tirosCampoFallados = tiros2Fallados + tiros3Fallados;

            // ── Calcular puntaje Fantasy ──
            double puntaje = MotorPuntuacion.calcular(dto, esteJugadorGano);

            // ── Construir y persistir la entidad ──
            EstadisticaPartido entidad = EstadisticaPartido.builder()
                    .jugadorReal(jugadorReal)
                    .jornada(jornada)
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
                    .tirosCampoFallados(tirosCampoFallados)
                    .tirosLibresFallados(tirosLibresFallados)
                    .taponesRealizados(safe(dto.getTaponesRealizados()))
                    .taponesRecibidos(safe(dto.getTaponesRecibidos()))
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

            log.info("[SCRAPER] ✓ Guardado: {} | Puntaje Fantasy: {}", dto.getNombre(), puntaje);
        }

        log.info("[SCRAPER] Persistencia completada. Guardados: {} | Omitidos: {}", guardados, omitidos);
    }

    // Helper local para evitar NullPointerExceptions
    private int safe(Integer v) {
        return v != null ? v : 0;
    }
}