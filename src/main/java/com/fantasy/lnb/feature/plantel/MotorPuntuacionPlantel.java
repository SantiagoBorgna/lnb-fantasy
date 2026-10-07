package com.fantasy.lnb.feature.plantel;

import com.fantasy.lnb.feature.estadisticas.EstadisticaPartido;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository;
import com.fantasy.lnb.feature.jornada.EstadoPartido;
import com.fantasy.lnb.feature.jornada.Partido;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
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
     * Puntaje del DT en una jornada, a partir de los partidos de esa jornada.
     *
     * REGLA: si el equipo del DT juega más de una vez en la jornada, solo cuenta
     * el PRIMER partido (por fecha), igual que con los jugadores, a los que se les
     * ignora el segundo partido (regla del fixture asimétrico). Es el único lugar
     * que elige el partido, así lo que se guarda en el ranking y lo que se muestra
     * en la cancha siempre salen del mismo.
     *
     * @return vacío si el equipo todavía no tiene un partido finalizado, o si a ese
     *         partido le falta el marcador: el DT ni suma ni resta
     */
    public Optional<Double> calcularPuntajeDtEnJornada(Long equipoDtId, Collection<Partido> partidosJornada) {
        return partidosJornada.stream()
                .filter(p -> p.getEstado() == EstadoPartido.FINALIZADO
                        || p.getEstado() == EstadoPartido.PROCESADO)
                .filter(p -> p.getEquipoLocal().getId().equals(equipoDtId)
                        || p.getEquipoVisitante().getId().equals(equipoDtId))
                .min(Comparator.comparing(Partido::getFechaHora))
                // Sin este chequeo el unboxing de los Integer nulos tira NPE y se pierde el
                // puntaje entero del plantel, jugadores incluidos.
                .filter(p -> p.getPuntosLocal() != null && p.getPuntosVisitante() != null)
                .map(p -> {
                    boolean esLocal = p.getEquipoLocal().getId().equals(equipoDtId);
                    int puntosDt = esLocal ? p.getPuntosLocal() : p.getPuntosVisitante();
                    int puntosRival = esLocal ? p.getPuntosVisitante() : p.getPuntosLocal();
                    return calcularPuntajeDt(puntosDt, puntosRival);
                });
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