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

    // ── Consultas del Mercado ───────────────────────────────────────────────

    @Cacheable(value = "mercado", key = "'todos_' + #orden")
    @Transactional(readOnly = true)
    public List<JugadorMercadoDto> listarTodos(String orden) {
        Sort sort = crearSort(orden);
        // Traemos todos los que NO sean DESCONOCIDO, aplicando el orden dinámico
        return jugadorRepo.findByPosicionNot(PosicionJugador.DESCONOCIDO, sort).stream()
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
                    .map(this::toDto)
                    .toList();
        } else {
            // Búsqueda inteligente + Filtro de posición estricto ("Bases", "Aleros", etc.)
            return jugadorRepo
                    .buscarPorJugadorOEquipoYPosicion(nombre, posicion, sort)
                    .stream()
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
            List<Double> ultimos3 = jugadorRepo.findUltimosPuntajes(jugador.getId(), 3);

            if (ultimos3.isEmpty()) {
                log.debug("[PRECIOS] {} sin historial, precio sin cambios.",
                        jugador.getNombreCompleto());
                continue;
            }

            double promedio = calcularPromedio(ultimos3);
            jugador.setPromedioFantasy(promedio); // Guardamos el promedio en la BD

            double valorBase = jugador.getValorBase();
            double precioActual = jugador.getValorMercadoActual();

            double factorRendimiento = (valorBase > 0) ? promedio / valorBase : 1.0;
            double delta = (factorRendimiento - 1.0) * 0.1;
            double nuevoPrecio = precioActual * (1.0 + delta);

            double techo = precioActual * 1.30;
            double piso = precioActual * 0.70;

            nuevoPrecio = Math.min(nuevoPrecio, techo);
            nuevoPrecio = Math.max(nuevoPrecio, piso);
            nuevoPrecio = Math.max(nuevoPrecio, 1.0);
            nuevoPrecio = Math.round(nuevoPrecio * 100.0) / 100.0;

            jugador.setValorMercadoActual(nuevoPrecio);
            jugadorRepo.save(jugador);
            actualizados++;

            log.info("[PRECIOS] {} | Base: {} | Promedio(3): {} | Factor: {} | {} a {}",
                    jugador.getNombreCompleto(),
                    valorBase,
                    String.format("%.2f", promedio),
                    String.format("%.2f", factorRendimiento),
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