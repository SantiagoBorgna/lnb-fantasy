package com.fantasy.lnb.feature.plantel;

import com.fantasy.lnb.exception.PlantelIncompletoException;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.usuario.EquipoVirtual;
import com.fantasy.lnb.feature.usuario.EquipoVirtualRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PuntuacionService {

    private final PlantelJornadaRepository plantelRepo;
    private final MotorPuntuacionPlantel motor;
    private final JornadaRepository jornadaRepo;
    private final EquipoVirtualRepository equipoVirtualRepo;
    private final PartidoRepository partidoRepo;

    /**
     * Calcula y persiste el puntaje de TODOS los planteles
     * para la jornada recién FINALIZADA.
     *
     * Se llama desde el JornadaTransicionCronJob después de que
     * la jornada transiciona a FINALIZADA.
     *
     * Flujo:
     * 1. Carga todos los planteles de la jornada con sus jugadores
     * 2. Para cada jugador aplica el multiplicador sobre su puntaje bruto
     * 3. Suma el puntaje del DT
     * 4. Persiste el puntajeObtenidoFecha en PlantelJornada
     * 5. Acumula al puntajeGlobal del EquipoVirtual
     */
    @Transactional
    public void calcularPuntajesDeJornada(Long jornadaId) {

        List<PlantelJornada> planteles = plantelRepo
                .findAllByJornadaIdWithJugadores(jornadaId);

        if (planteles.isEmpty()) {
            log.warn("[PUNTUACION] No hay planteles para la jornada {}.", jornadaId);
            return;
        }

        log.info("[PUNTUACION] Calculando puntajes de {} planteles " +
                "para jornada {}...", planteles.size(), jornadaId);

        for (PlantelJornada plantel : planteles) {
            try {
                double puntajeTotal = calcularPuntajePlantel(plantel, jornadaId);

                plantel.setPuntajeObtenidoFecha(puntajeTotal);
                plantelRepo.save(plantel);

                // Acumular al puntaje global del equipo virtual
                equipoVirtualRepo.findByUsuario_Id(plantel.getUsuario().getId())
                        .ifPresent(equipo -> {
                            equipo.setPuntajeGlobal(
                                    equipo.getPuntajeGlobal() + puntajeTotal);
                            equipoVirtualRepo.save(equipo);
                        });

                log.info("[PUNTUACION] Usuario {} | Jornada {} | Puntaje: {}",
                        plantel.getUsuario().getEmail(),
                        jornadaId,
                        puntajeTotal);

            } catch (Exception e) {
                // Un plantel que falla no interrumpe el cálculo del resto
                log.error("[PUNTUACION] Error calculando puntaje del plantel {}: {}",
                        plantel.getId(), e.getMessage(), e);
            }
        }

        log.info("[PUNTUACION] Cálculo de jornada {} completado.", jornadaId);
    }

    // ── Privados ────────────────────────────────────────────────────────────

    private double calcularPuntajePlantel(PlantelJornada plantel, Long jornadaId) {

        if (plantel.getJugadores() == null || plantel.getJugadores().size() != 10) {
            throw new PlantelIncompletoException(jornadaId);
        }

        double puntajeJugadores = plantel.getJugadores().stream()
                .mapToDouble(jp -> motor.calcularPuntajeConMultiplicador(jp, jornadaId))
                .sum();

        // El DT es opcional — si no eligió DT su aporte es 0
        double puntajeDt = 0.0;
        if (plantel.getDt() != null) {
            puntajeDt = calcularPuntajeDtDesdeBD(plantel, jornadaId);
        }

        double total = Math.round((puntajeJugadores + puntajeDt) * 100.0) / 100.0;

        log.debug("[PUNTUACION] Plantel {} | Jugadores: {} | DT: {} | Total: {}",
                plantel.getId(), puntajeJugadores, puntajeDt, total);

        return total;
    }

    /**
     * Para calcular el puntaje del DT necesitamos el marcador del partido
     * de su equipo en esta jornada. Lo obtenemos sumando los puntos anotados
     * por todos los jugadores de ese equipo en sus estadísticas de la jornada.
     */
    private double calcularPuntajeDtDesdeBD(PlantelJornada plantel, Long jornadaId) {
        if (plantel.getDt() == null)
            return 0.0;

        Long equipoDtId = plantel.getDt().getEquipoReal().getId();

        // Buscamos el partido de esta jornada donde juegue el equipo del DT
        return partidoRepo.findByJornada_Id(jornadaId).stream()
                .filter(p -> p.getEquipoLocal().getId().equals(equipoDtId) ||
                        p.getEquipoVisitante().getId().equals(equipoDtId))
                .findFirst()
                .map(p -> {
                    // Identificamos quién es el equipo del DT en este partido
                    boolean esLocal = p.getEquipoLocal().getId().equals(equipoDtId);
                    int puntosDT = esLocal ? p.getPuntosLocal() : p.getPuntosVisitante();
                    int puntosRival = esLocal ? p.getPuntosVisitante() : p.getPuntosLocal();

                    // Usamos el motor de puntuación que ya tenés inyectado
                    return motor.calcularPuntajeDt(puntosDT, puntosRival);
                })
                .orElse(0.0);
    }
}