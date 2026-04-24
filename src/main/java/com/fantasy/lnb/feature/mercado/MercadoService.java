package com.fantasy.lnb.feature.mercado;

import com.fantasy.lnb.feature.mercado.dto.JugadorMercadoDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MercadoService {

    private final JugadorRealRepository jugadorRepo;

    // ── Consultas del Mercado ───────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<JugadorMercadoDto> listarTodos() {
        return jugadorRepo.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JugadorMercadoDto> listarPorPosicion(PosicionJugador posicion) {
        return jugadorRepo
                .findByPosicionOrderByValorMercadoActualDesc(posicion)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<JugadorMercadoDto> buscarPorNombre(String nombre) {
        return jugadorRepo
                .findByNombreCompletoContainingIgnoreCase(nombre)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<JugadorMercadoDto> buscarPorId(Long id) {
        return jugadorRepo.findById(id)
                .map(this::toDto);
    }

    // ── Algoritmo de variación dinámica de precios ─────────────────────────

    /**
     * Se llama desde el CronJob de actualización de precios,
     * DESPUÉS de que el scraper ya persistió las estadísticas de la jornada.
     *
     * Lógica del PRD:
     * 1. Calcula el promedio de puntos Fantasy de las últimas 3 jornadas.
     * 2. Lo compara contra el valorBase del jugador (nunca cambia).
     * 3. Ajusta el valorMercadoActual proporcionalmente.
     * 4. Aplica un techo (+30%) y un piso (-30%) para evitar valores absurdos.
     * 5. Nunca baja de 1.0 crédito (precio mínimo absoluto).
     */
    public void actualizarPreciosTodos() {
        List<JugadorReal> jugadores = jugadorRepo.findAll();
        int actualizados = 0;

        for (JugadorReal jugador : jugadores) {
            List<Double> ultimos3 = jugadorRepo.findUltimosPuntajes(jugador.getId(), 3);

            // Sin historial suficiente: el precio no cambia esta jornada
            if (ultimos3.isEmpty()) {
                log.debug("[PRECIOS] {} sin historial, precio sin cambios.",
                        jugador.getNombreCompleto());
                continue;
            }

            double promedio = calcularPromedio(ultimos3);
            double valorBase = jugador.getValorBase();
            double precioActual = jugador.getValorMercadoActual();

            // Factor de rendimiento: cuánto rindió vs lo esperado
            // Ejemplo: promedio=18, valorBase=10 → factor=1.8 (rindió 80% más)
            double factorRendimiento = (valorBase > 0) ? promedio / valorBase : 1.0;

            // Ajuste proporcional suavizado (factor - 1 da el delta, * 0.1 lo suaviza)
            // Ejemplo: factorRendimiento=1.8 → delta=+0.08 → sube 8% del precio actual
            double delta = (factorRendimiento - 1.0) * 0.1;
            double nuevoPrecio = precioActual * (1.0 + delta);

            // Techo: no puede subir más del 30% del precio actual en una jornada
            double techo = precioActual * 1.30;
            double piso = precioActual * 0.70;

            nuevoPrecio = Math.min(nuevoPrecio, techo);
            nuevoPrecio = Math.max(nuevoPrecio, piso);

            // Precio mínimo absoluto del PRD: 1.0 crédito
            nuevoPrecio = Math.max(nuevoPrecio, 1.0);

            // Redondeo a 2 decimales
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

    // ── Helpers privados ────────────────────────────────────────────────────

    private double calcularPromedio(List<Double> valores) {
        return valores.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private double calcularPromedioUltimas3(Long jugadorId) {
        List<Double> ultimos = jugadorRepo.findUltimosPuntajes(jugadorId, 3);
        return calcularPromedio(ultimos);
    }

    /**
     * Mapper entidad → DTO.
     * Incluye el promedio de puntos para mostrarlo en el Mercado.
     */
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
                .posicion(j.getPosicion())
                .estado(j.getEstado())
                .valorMercadoActual(j.getValorMercadoActual())
                .promedioPuntosUltimas3(calcularPromedioUltimas3(j.getId()))
                .build();
    }
}