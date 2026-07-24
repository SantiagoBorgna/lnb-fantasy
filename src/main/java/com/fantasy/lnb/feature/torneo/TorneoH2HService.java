package com.fantasy.lnb.feature.torneo;

import com.fantasy.lnb.feature.jornada.EstadoJornada;
import com.fantasy.lnb.feature.jornada.Jornada;
import com.fantasy.lnb.feature.jornada.JornadaRepository;
import com.fantasy.lnb.feature.plantel.PlantelJornada;
import com.fantasy.lnb.feature.plantel.PlantelJornadaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TorneoH2HService {

    private final EnfrentamientoH2HRepository enfrentamientoRepo;
    private final TorneoEquipoRepository torneoEquipoRepo;
    private final JornadaRepository jornadaRepo;
    private final PlantelJornadaRepository plantelRepo;

    /**
     * Genera el fixture Round-Robin para el torneo.
     * Se llama cuando finaliza el Draft.
     */
    @Transactional
    public void generarFixture(Torneo torneo) {
        if (torneo.getTipoPuntuacion() != TipoPuntuacion.H2H) {
            return; // Solo aplica a torneos H2H
        }

        List<TorneoEquipo> equipos = torneo.getParticipantes();
        if (equipos == null || equipos.size() < 2) return;

        // 1. Obtener todas las jornadas restantes (que no estén finalizadas)
        List<Jornada> jornadasFuturas = jornadaRepo.findByEstadoOrderByFechaInicioAsc(EstadoJornada.ABIERTA_A_CAMBIOS);
        
        // Algoritmo Round-Robin
        int numEquipos = equipos.size();
        boolean hasBye = false;
        if (numEquipos % 2 != 0) {
            equipos.add(null); // Dummy team for BYE
            numEquipos++;
            hasBye = true;
        }

        int numJornadas = jornadasFuturas.size();
        int rondas = numEquipos - 1;
        
        List<EnfrentamientoH2H> enfrentamientos = new ArrayList<>();
        
        // Arrays para rotar
        TorneoEquipo[] rotacion = equipos.toArray(new TorneoEquipo[0]);

        for (int j = 0; j < numJornadas; j++) {
            Jornada jornada = jornadasFuturas.get(j);
            
            // Cada "ronda" del round robin se mapea a una jornada (si hay más jornadas que rondas, se repite el fixture)
            int r = j % rondas;
            
            // Construir los partidos de esta ronda
            for (int i = 0; i < numEquipos / 2; i++) {
                TorneoEquipo local = rotacion[i];
                TorneoEquipo visitante = rotacion[numEquipos - 1 - i];

                // Si no es el "Bye", guardar enfrentamiento
                if (local != null && visitante != null) {
                    enfrentamientos.add(EnfrentamientoH2H.builder()
                            .torneo(torneo)
                            .jornada(jornada)
                            .equipoLocal(local)
                            .equipoVisitante(visitante)
                            .build());
                } else {
                    // El que juega contra el Bye (el que no es null)
                    TorneoEquipo libre = (local != null) ? local : visitante;
                    enfrentamientos.add(EnfrentamientoH2H.builder()
                            .torneo(torneo)
                            .jornada(jornada)
                            .equipoLocal(libre)
                            .equipoVisitante(null)
                            .build());
                }
            }

            // Rotar array (fijando el elemento 0)
            TorneoEquipo ultimo = rotacion[numEquipos - 1];
            for (int k = numEquipos - 1; k > 1; k--) {
                rotacion[k] = rotacion[k - 1];
            }
            rotacion[1] = ultimo;
        }

        enfrentamientoRepo.saveAll(enfrentamientos);
        log.info("[H2H] Fixture generado para el torneo {} con {} jornadas.", torneo.getId(), numJornadas);
    }

    /**
     * Resuelve los enfrentamientos de una jornada específica.
     * Se llama cuando finaliza la jornada global y se calculan los puntos fantasy.
     */
    @Transactional
    public void resolverJornada(Long jornadaId) {
        List<EnfrentamientoH2H> enfrentamientos = enfrentamientoRepo.findByJornada_IdAndProcesadoFalse(jornadaId);

        for (EnfrentamientoH2H duelo : enfrentamientos) {
            TorneoEquipo local = duelo.getEquipoLocal();
            TorneoEquipo visitante = duelo.getEquipoVisitante();

            // Buscar planteles
            PlantelJornada pLocal = plantelRepo.findByUsuario_IdAndJornada_IdAndTorneo_Id(
                    local.getEquipoVirtual().getUsuario().getId(), jornadaId, duelo.getTorneo().getId()).orElse(null);
            
            Double ptsLocal = (pLocal != null && pLocal.getPuntajeObtenidoFecha() != null) ? pLocal.getPuntajeObtenidoFecha() : 0.0;
            duelo.setPuntajeLocal(ptsLocal);
            local.setPuntosFavor(local.getPuntosFavor() + ptsLocal);

            if (visitante != null) {
                PlantelJornada pVisitante = plantelRepo.findByUsuario_IdAndJornada_IdAndTorneo_Id(
                        visitante.getEquipoVirtual().getUsuario().getId(), jornadaId, duelo.getTorneo().getId()).orElse(null);
                
                Double ptsVisitante = (pVisitante != null && pVisitante.getPuntajeObtenidoFecha() != null) ? pVisitante.getPuntajeObtenidoFecha() : 0.0;
                duelo.setPuntajeVisitante(ptsVisitante);
                visitante.setPuntosFavor(visitante.getPuntosFavor() + ptsVisitante);

                // Resultado: 3 victoria, 1 empate, 0 derrota
                if (ptsLocal > ptsVisitante) {
                    local.setPartidosGanados(local.getPartidosGanados() + 1);
                    visitante.setPartidosPerdidos(visitante.getPartidosPerdidos() + 1);
                    local.setPuntajeGlobal(local.getPuntajeGlobal() + 3.0);
                } else if (ptsVisitante > ptsLocal) {
                    visitante.setPartidosGanados(visitante.getPartidosGanados() + 1);
                    local.setPartidosPerdidos(local.getPartidosPerdidos() + 1);
                    visitante.setPuntajeGlobal(visitante.getPuntajeGlobal() + 3.0);
                } else {
                    local.setPartidosEmpatados(local.getPartidosEmpatados() + 1);
                    visitante.setPartidosEmpatados(visitante.getPartidosEmpatados() + 1);
                    local.setPuntajeGlobal(local.getPuntajeGlobal() + 1.0);
                    visitante.setPuntajeGlobal(visitante.getPuntajeGlobal() + 1.0);
                }
                torneoEquipoRepo.save(visitante);
            } else {
                // BYE (fecha libre) - se asume victoria técnica pero suma 0 puntos
                local.setPartidosGanados(local.getPartidosGanados() + 1);
                // No suma puntajeGlobal
            }

            torneoEquipoRepo.save(local);
            duelo.setProcesado(true);
            enfrentamientoRepo.save(duelo);
        }

        log.info("[H2H] Jornada {} resuelta. {} enfrentamientos procesados.", jornadaId, enfrentamientos.size());
    }
}
