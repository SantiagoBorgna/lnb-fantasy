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
    private final com.fantasy.lnb.feature.torneo.TorneoEquipoRepository torneoEquipoRepo;
    private final com.fantasy.lnb.feature.torneo.TorneoH2HService h2hService;

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
    public void calcularPuntajesDeJornada(Long jornadaId, boolean esCierreDefinitivo) {

        List<PlantelJornada> planteles = plantelRepo
                .findAllByJornadaIdWithJugadores(jornadaId);

        if (planteles.isEmpty()) {
            log.warn("[PUNTUACION] No hay planteles para la jornada {}.", jornadaId);
            return;
        }

        String tipoCalculo = esCierreDefinitivo ? "CIERRE DEFINITIVO" : "PARCIAL EN VIVO";
        log.info("[PUNTUACION] [{}] Calculando puntajes para jornada {}...", tipoCalculo, jornadaId);

        for (PlantelJornada plantel : planteles) {
            try {
                double puntajeTotal = calcularPuntajePlantel(plantel, jornadaId);

                // 1. Siempre actualizamos el puntaje de la jornada (lo que se ve en la
                // canchita)
                plantel.setPuntajeObtenidoFecha(puntajeTotal);
                plantelRepo.save(plantel);

                // 2. Sumar al ranking
                if (esCierreDefinitivo) {
                    if (plantel.getTorneo() == null) {
                        // Ranking Global
                        equipoVirtualRepo.findByUsuario_Id(plantel.getUsuario().getId())
                                .ifPresent(equipo -> {
                                    equipo.setPuntajeGlobal(equipo.getPuntajeGlobal() + puntajeTotal);
                                    equipoVirtualRepo.save(equipo);
                                    log.debug("[PUNTUACION] Ranking global actualizado para {}: +{}",
                                            plantel.getUsuario().getEmail(), puntajeTotal);
                                });
                    } else {
                        // Ranking Torneo
                        torneoEquipoRepo.findByTorneo_Id(plantel.getTorneo().getId()).stream()
                                .filter(te -> te.getEquipoVirtual().getUsuario().getId().equals(plantel.getUsuario().getId()))
                                .findFirst()
                                .ifPresent(te -> {
                                    if (plantel.getTorneo().getTipoPuntuacion() != com.fantasy.lnb.feature.torneo.TipoPuntuacion.H2H) {
                                        te.setPuntajeGlobal(te.getPuntajeGlobal() + puntajeTotal);
                                    }
                                    torneoEquipoRepo.save(te);
                                    log.debug("[PUNTUACION] Ranking torneo actualizado para {}: +{}", plantel.getUsuario().getEmail(), puntajeTotal);
                                });
                    }
                }

                log.info("[PUNTUACION] Usuario {} | Jornada {} | Puntaje: {}",
                        plantel.getUsuario().getEmail(),
                        jornadaId,
                        puntajeTotal);

            } catch (PlantelIncompletoException e) {
                log.warn("[PUNTUACION] Usuario {} con plantel incompleto. Puntaje 0.", plantel.getUsuario().getEmail());
            } catch (Exception e) {
                log.error("[PUNTUACION] Error calculando para usuario {}: {}", plantel.getUsuario().getEmail(), e.getMessage());
            }
        }

        // 3. Después de calcular todos los planteles, si es cierre definitivo, resolver duelos H2H
        if (esCierreDefinitivo) {
            h2hService.resolverJornada(jornadaId);
        }

        log.info("[PUNTUACION] Cálculo de jornada {} completado ({})", jornadaId, tipoCalculo);
    }

    // ── Privados ────────────────────────────────────────────────────────────

    private double calcularPuntajePlantel(PlantelJornada plantel, Long jornadaId) {

        if (plantel.getJugadores() == null || plantel.getJugadores().isEmpty()) {
            log.warn("[PUNTUACION] Plantel {} vacio en jornada {}. Puntaje 0.", plantel.getId(), jornadaId);
            return 0.0;
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