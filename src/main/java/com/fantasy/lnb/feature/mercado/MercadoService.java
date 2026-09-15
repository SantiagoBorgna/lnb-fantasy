package com.fantasy.lnb.feature.mercado;

import com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository;
import com.fantasy.lnb.feature.mercado.dto.JugadorMercadoDto;
import com.fantasy.lnb.feature.mercado.dto.JugadorStatsResumenDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MercadoService {

    private final JugadorRealRepository jugadorRepo;
    private final EstadisticaPartidoRepository estadisticaRepo;
    private final com.fantasy.lnb.feature.plantel.PlantelDraftService plantelDraftService;

    private static final double PRECIO_MINIMO = 4.0;

    // Cuántas jornadas anteriores se usan como "promedio habitual" del jugador
    // para juzgar si la última jornada fue una sobre/bajo-performance.
    private static final int VENTANA_HISTORICA = 5;

    // Tope de variación de precio por jornada (créditos), en cualquier sentido.
    private static final double MAX_CAMBIO_POR_JORNADA = 0.3;

    // Cuántos créditos de cambio por cada punto fantasy de diferencia contra
    // el promedio propio. Con 0.02, una diferencia de 15 puntos ya alcanza el
    // tope de ±0.3.
    private static final double FACTOR_ESCALA = 0.02;

    // ── Consultas del Mercado ───────────────────────────────────────────────

    @Cacheable(value = "mercado", key = "'todos_' + #orden")
    @Transactional(readOnly = true)
    public List<JugadorMercadoDto> listarTodos(String orden) {
        Sort sort = crearSort(orden);
        // Traemos todos los que NO sean DESCONOCIDO, aplicando el orden dinámico
        return jugadorRepo.findByPosicionNot(PosicionJugador.DESCONOCIDO, sort).stream()
                .filter(j -> j.getEstado() != EstadoJugador.BAJA)
                .map(this::toDto)
                .toList();
    }

    @Cacheable(value = "mercado", key = "'posicion_' + #posicion + '_' + #orden")
    @Transactional(readOnly = true)
    public List<JugadorMercadoDto> listarPorPosicion(PosicionJugador posicion, String orden) {
        Sort sort = crearSort(orden);
        return jugadorRepo
                .findByPosicion(posicion, sort)
                .stream()
                .filter(j -> j.getEstado() != EstadoJugador.BAJA)
                .map(this::toDto)
                .toList();
    }

    @Cacheable(value = "mercado", key = "'busqueda_' + #nombre + '_' + #posicion + '_' + #orden", unless = "#nombre == null")
    @Transactional(readOnly = true)
    public List<JugadorMercadoDto> buscarPorNombre(String nombre, PosicionJugador posicion, String orden) {
        Sort sort = crearSort(orden);
        if (posicion == null) {
            // Búsqueda general sin filtro de posición ("Todos")
            return jugadorRepo
                    .buscarPorJugadorOEquipo(nombre, PosicionJugador.DESCONOCIDO, sort)
                    .stream()
                    .filter(j -> j.getEstado() != EstadoJugador.BAJA)
                    .map(this::toDto)
                    .toList();
        } else {
            // Búsqueda inteligente + Filtro de posición estricto ("Bases", "Aleros", etc.)
            return jugadorRepo
                    .buscarPorJugadorOEquipoYPosicion(nombre, posicion, sort)
                    .stream()
                    .filter(j -> j.getEstado() != EstadoJugador.BAJA)
                    .map(this::toDto)
                    .toList();
        }
    }

    @Transactional(readOnly = true)
    public Optional<JugadorMercadoDto> buscarPorId(Long id) {
        return jugadorRepo.findById(id)
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public JugadorStatsResumenDto obtenerStatsResumen(Long jugadorId) {
        // Query directa para promedios por categoría
        return estadisticaRepo
                .findPromediosByJugadorId(jugadorId)
                .orElse(JugadorStatsResumenDto.builder()
                        .jugadorRealId(jugadorId)
                        .partidosJugados(0)
                        .build());
    }

    // ── Algoritmo de variación dinámica de precios ─────────────────────────

    public void actualizarPreciosTodos() {
        List<JugadorReal> jugadores = jugadorRepo.findAll();
        int actualizados = 0;

        for (JugadorReal jugador : jugadores) {
            // Traemos la última jornada + las VENTANA_HISTORICA anteriores para
            // usarlas como "promedio habitual" de comparación.
            List<Double> ultimos = jugadorRepo.findUltimosPuntajes(jugador.getId(), VENTANA_HISTORICA + 1);

            if (ultimos.isEmpty()) {
                log.debug("[PRECIOS] {} sin historial, precio sin cambios.",
                        jugador.getNombreCompleto());
                continue;
            }

            jugador.setPromedioFantasy(calcularPromedio(ultimos));

            double puntajeUltimaJornada = ultimos.get(0);
            List<Double> anteriores = ultimos.subList(1, ultimos.size());

            if (anteriores.isEmpty()) {
                // Primera jornada con estadísticas: todavía no hay promedio
                // propio contra el cual comparar, el precio no se mueve.
                jugadorRepo.save(jugador);
                log.debug("[PRECIOS] {} en su primera jornada con stats, precio sin cambios.",
                        jugador.getNombreCompleto());
                continue;
            }

            double promedioAnterior = calcularPromedio(anteriores);
            double diferencia = puntajeUltimaJornada - promedioAnterior;

            double deltaPrecio = diferencia * FACTOR_ESCALA;
            deltaPrecio = Math.max(-MAX_CAMBIO_POR_JORNADA, Math.min(MAX_CAMBIO_POR_JORNADA, deltaPrecio));

            double precioActual = jugador.getValorMercadoActual();
            double nuevoPrecio = precioActual + deltaPrecio;
            nuevoPrecio = Math.max(nuevoPrecio, PRECIO_MINIMO);
            nuevoPrecio = Math.round(nuevoPrecio * 100.0) / 100.0;

            jugador.setValorMercadoActual(nuevoPrecio);
            jugadorRepo.save(jugador);
            actualizados++;

            log.info("[PRECIOS] {} | Últ. jornada: {} | Promedio anterior: {} | Δ: {} | {} a {}",
                    jugador.getNombreCompleto(),
                    String.format("%.2f", puntajeUltimaJornada),
                    String.format("%.2f", promedioAnterior),
                    String.format("%.2f", deltaPrecio),
                    precioActual,
                    nuevoPrecio);
        }

        log.info("[PRECIOS] Actualización completada. Jugadores actualizados: {}", actualizados);
    }

    public List<JugadorMercadoDto> listarLibresTorneo(Long torneoId, PosicionJugador posicion, String nombre, String orden) {
        List<JugadorMercadoDto> todos = listarJugadoresFiltrados(posicion, nombre, orden);
        
        java.util.Set<Long> ocupados = plantelDraftService.obtenerJugadoresOcupadosEnTorneo(torneoId);
        
        return todos.stream()
            .filter(j -> !ocupados.contains(j.getId()))
            .toList();
    }

    private List<JugadorMercadoDto> listarJugadoresFiltrados(PosicionJugador posicion, String nombre, String orden) {
        if (nombre != null && !nombre.isBlank()) {
            return buscarPorNombre(nombre, posicion, orden);
        }
        if (posicion != null) {
            return listarPorPosicion(posicion, orden);
        }
        return listarTodos(orden);
    }

    // ── Helpers privados ────────────────────────────────────────────────────

    private double calcularPromedio(List<Double> valores) {
        return valores.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private JugadorMercadoDto toDto(JugadorReal j) {
        return JugadorMercadoDto.builder()
                .id(j.getId())
                .gesId(j.getGesId())
                .nombreCompleto(j.getNombreCompleto())
                .numeroCamiseta(j.getNumeroCamiseta())
                .equipoId(j.getEquipoReal().getId())
                .equipoNombre(j.getEquipoReal().getNombre())
                .equipoSigla(j.getEquipoReal().getSigla())
                .colorPrincipal(j.getEquipoReal().getColorPrincipal())
                .colorSecundario(j.getEquipoReal().getColorSecundario())
                .modeloCamiseta(j.getEquipoReal().getModeloCamiseta())
                .posicion(j.getPosicion())
                .estado(j.getEstado())
                .valorMercadoActual(j.getValorMercadoActual())
                .promedioPuntosUltimas3(j.getPromedioFantasy() != null ? j.getPromedioFantasy() : 0.0)
                .build();
    }

    private Sort crearSort(String orden) {
        if (orden == null)
            return Sort.by(Sort.Direction.DESC, "valorMercadoActual"); // Default

        return switch (orden) {
            case "precio_asc" -> Sort.by(Sort.Direction.ASC, "valorMercadoActual");
            case "promedio_desc" -> Sort.by(Sort.Direction.DESC, "promedioFantasy");
            case "promedio_asc" -> Sort.by(Sort.Direction.ASC, "promedioFantasy");
            case "nombre_asc" -> Sort.by(Sort.Direction.ASC, "nombreCompleto");
            case "nombre_desc" -> Sort.by(Sort.Direction.DESC, "nombreCompleto");
            default -> Sort.by(Sort.Direction.DESC, "valorMercadoActual"); // precio_desc
        };
    }
}