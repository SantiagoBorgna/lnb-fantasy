package com.fantasy.lnb.feature.plantel;

import com.fantasy.lnb.feature.estadisticas.EstadisticaPartido;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Aplica los multiplicadores de rol del PRD sobre los puntajes
 * ya calculados por MotorPuntuacion (Módulo 1).
 *
 * Responsabilidad única: dado un JugadorPlantel y una Jornada,
 * buscar su EstadisticaPartido y aplicar el multiplicador correcto.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MotorPuntuacionPlantel {

    private final EstadisticaPartidoRepository estadisticaRepo;

    /**
     * Calcula el puntaje Fantasy con multiplicador aplicado
     * para un jugador en su rol dentro del plantel.
     *
     * Si el jugador no jugó en la jornada (lesionado, no convocado),
     * su aporte es 0.0 — no penaliza al equipo Fantasy.
     *
     * @param jugadorPlantel Slot del jugador con su rol asignado
     * @param jornadaId      ID de la jornada a calcular
     * @return puntaje con multiplicador aplicado
     */
    public double calcularPuntajeConMultiplicador(
            JugadorPlantel jugadorPlantel,
            Long jornadaId) {

        Long jugadorRealId = jugadorPlantel.getJugadorReal().getId();

        Optional<EstadisticaPartido> estadistica = estadisticaRepo
                .findByJugadorReal_IdAndJornada_Id(jugadorRealId, jornadaId);

        if (estadistica.isEmpty()) {
            log.debug("[MOTOR-PLANTEL] {} no tiene estadísticas en jornada {}. Suma 0.",
                    jugadorPlantel.getJugadorReal().getNombreCompleto(), jornadaId);
            return 0.0;
        }

        double puntajeBruto = estadistica.get().getPuntajeFantasyCalculado();
        double multiplicador = jugadorPlantel.getMultiplicador();
        double puntajeConRol = puntajeBruto * multiplicador;
        double puntajeRedondeado = Math.round(puntajeConRol * 100.0) / 100.0;

        log.debug("[MOTOR-PLANTEL] {} | Rol: {} | Bruto: {} × {} = {}",
                jugadorPlantel.getJugadorReal().getNombreCompleto(),
                jugadorPlantel.getRol(),
                puntajeBruto,
                multiplicador,
                puntajeRedondeado);

        return puntajeRedondeado;
    }

    /**
     * Calcula el puntaje del DT según la diferencia de resultado real.
     * Escala del PRD:
     * GANA por 1-5pts → +5 | PIERDE por 1-5pts → -2.5
     * GANA por 6-10pts → +7.5 | PIERDE por 6-10pts → -5
     * GANA por 11-20pts → +10 | PIERDE por 11-20pts → -7.5
     * GANA por 21-30pts → +15 | PIERDE por 21-30pts → -10
     * GANA por +30pts → +20 | PIERDE por +30pts → -15
     *
     * @param puntosEquipoDt    Puntos anotados por el equipo del DT
     * @param puntosEquipoRival Puntos anotados por el rival
     * @return puntaje Fantasy del DT
     */
    public double calcularPuntajeDt(int puntosEquipoDt, int puntosEquipoRival) {
        int diferencia = puntosEquipoDt - puntosEquipoRival;

        if (diferencia > 0) {
            // Victoria
            if (diferencia <= 5)
                return 5.0;
            if (diferencia <= 10)
                return 7.5;
            if (diferencia <= 20)
                return 10.0;
            if (diferencia <= 30)
                return 15.0;
            return 20.0;
        } else {
            // Derrota (diferencia es negativa, usamos Math.abs)
            int margen = Math.abs(diferencia);
            if (margen <= 5)
                return -2.5;
            if (margen <= 10)
                return -5.0;
            if (margen <= 20)
                return -7.5;
            if (margen <= 30)
                return -10.0;
            return -15.0;
        }
        // Empate técnicamente imposible en básquet, pero si ocurre: 0
    }
}