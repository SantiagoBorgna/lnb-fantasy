package com.fantasy.lnb.feature.lideres;

import com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository;
import com.fantasy.lnb.feature.lideres.dto.CategoriaLideresDto;
import com.fantasy.lnb.feature.lideres.dto.LiderDto;
import com.fantasy.lnb.feature.mercado.JugadorReal;
import com.fantasy.lnb.feature.mercado.JugadorRealRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LideresService {

        private final EstadisticaPartidoRepository estadisticaRepo;
        private final JugadorRealRepository jugadorRepo;

        // Top 5 por categoría según el PRD
        private static final int TOP_N = 5;

        /**
         * Devuelve el resumen de todas las categorías con el líder de cada una.
         * Se usa para la pantalla principal de Líderes donde se ven las tarjetas.
         */
        @Transactional(readOnly = true)
        public List<CategoriaLideresDto> obtenerResumenLideres() {
                List<CategoriaLideresDto> resumen = new ArrayList<>();

                obtenerTop("Puntos Fantasy", "trophy",
                                estadisticaRepo.findLideresPuntajeFantasy(PageRequest.of(0, 1)))
                                .ifPresent(resumen::add);

                obtenerTop("Puntos", "basketball",
                                estadisticaRepo.findLideresPuntos(PageRequest.of(0, 1)))
                                .ifPresent(resumen::add);

                obtenerTop("Rebotes", "arrow-up",
                                estadisticaRepo.findLideresRebotes(PageRequest.of(0, 1)))
                                .ifPresent(resumen::add);

                obtenerTop("Asistencias", "hands-helping",
                                estadisticaRepo.findLideresAsistencias(PageRequest.of(0, 1)))
                                .ifPresent(resumen::add);

                obtenerTop("Robos", "shield",
                                estadisticaRepo.findLideresRobos(PageRequest.of(0, 1)))
                                .ifPresent(resumen::add);

                obtenerTop("Tapones", "hand-paper",
                                estadisticaRepo.findLideresTapones(PageRequest.of(0, 1)))
                                .ifPresent(resumen::add);

                return resumen;
        }

        /**
         * Devuelve el Top 5 de una categoría específica.
         * Se usa cuando el usuario toca una tarjeta en el frontend
         * para ver el ranking completo de esa categoría.
         */
        @Transactional(readOnly = true)
        public List<LiderDto> obtenerTopPorCategoria(String categoria) {
                List<Object[]> rows = switch (categoria.toLowerCase()) {
                        case "puntos" -> estadisticaRepo
                                        .findLideresPuntos(PageRequest.of(0, TOP_N));
                        case "rebotes" -> estadisticaRepo
                                        .findLideresRebotes(PageRequest.of(0, TOP_N));
                        case "asistencias" -> estadisticaRepo
                                        .findLideresAsistencias(PageRequest.of(0, TOP_N));
                        case "robos" -> estadisticaRepo
                                        .findLideresRobos(PageRequest.of(0, TOP_N));
                        case "tapones" -> estadisticaRepo
                                        .findLideresTapones(PageRequest.of(0, TOP_N));
                        case "fantasy" -> estadisticaRepo
                                        .findLideresPuntajeFantasy(PageRequest.of(0, TOP_N));
                        default -> throw new IllegalArgumentException(
                                        "Categoría no reconocida: " + categoria);
                };

                return mapearLideres(rows);
        }

        // ── Helpers privados ────────────────────────────────────────────────────

        private Optional<CategoriaLideresDto> obtenerTop(
                        String categoria,
                        String icono,
                        List<Object[]> rows) {

                if (rows.isEmpty())
                        return Optional.empty();

                List<LiderDto> lideres = mapearLideres(rows);
                if (lideres.isEmpty())
                        return Optional.empty();

                return Optional.of(CategoriaLideresDto.builder()
                                .categoria(categoria)
                                .icono(icono)
                                .lider(lideres.get(0))
                                .build());
        }

        /**
         * Convierte las proyecciones Object[] en LiderDto.
         * Hace una sola query a jugador_real para todos los IDs en batch
         * en lugar de N queries individuales.
         */
        private List<LiderDto> mapearLideres(List<Object[]> rows) {
                if (rows.isEmpty())
                        return List.of();

                // Extraer IDs únicos para query en batch
                List<Long> ids = rows.stream()
                                .map(row -> (Long) row[0])
                                .toList();

                // Una sola query para todos los jugadores
                Map<Long, JugadorReal> jugadoresMap = jugadorRepo.findAllById(ids)
                                .stream()
                                .collect(Collectors.toMap(JugadorReal::getId,
                                                Function.identity()));

                return rows.stream()
                                .map(row -> {
                                        Long jugadorId = (Long) row[0];
                                        Double promedio = (Double) row[1];
                                        Long partidosLong = (Long) row[2];
                                        int partidos = partidosLong.intValue();

                                        JugadorReal jugador = jugadoresMap.get(jugadorId);
                                        if (jugador == null)
                                                return null;

                                        return LiderDto.builder()
                                                        .jugadorRealId(jugador.getId())
                                                        .nombreCompleto(jugador.getNombreCompleto())
                                                        .equipoSigla(jugador.getEquipoReal().getSigla())
                                                        .colorPrincipal(jugador.getEquipoReal()
                                                                        .getColorPrincipal())
                                                        .colorSecundario(jugador.getEquipoReal()
                                                                        .getColorSecundario())
                                                        .modeloCamiseta(jugador.getEquipoReal().getModeloCamiseta())
                                                        .numeroCamiseta(jugador.getNumeroCamiseta())
                                                        .promedio(Math.round(promedio * 100.0) / 100.0)
                                                        .partidosJugados(partidos)
                                                        .build();
                                })
                                .filter(dto -> dto != null)
                                .toList();
        }
}