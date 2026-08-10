package com.fantasy.lnb.feature.showdown;

import com.fantasy.lnb.feature.estadisticas.EstadisticaPartido;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.fantasy.lnb.feature.mercado.JugadorReal;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShowdownPuntuacionService {

    private final ShowdownEventoRepository eventoRepo;
    private final ShowdownParticipanteRepository participanteRepo;
    private final EstadisticaPartidoRepository estadisticaRepo;

    /**
     * Se ejecuta periódicamente para revisar si hay eventos EN_CURSO
     * cuyos partidos ya hayan finalizado y procesado sus estadísticas.
     */
    @Scheduled(fixedDelay = 600000) // cada 10 min
    @Transactional
    public void procesarPuntajesEventosEnCurso() {
        // Transicionar eventos de ABIERTO a EN_CURSO si el partido ya comenzó
        List<ShowdownEvento> eventosAbiertos = eventoRepo.findAll().stream()
                .filter(e -> e.getEstado() == EstadoShowdown.ABIERTO)
                .toList();
        for (ShowdownEvento e : eventosAbiertos) {
            if (e.getPartido().getFecha().isBefore(java.time.LocalDateTime.now())) {
                e.setEstado(EstadoShowdown.EN_CURSO);
                eventoRepo.save(e);
                log.info("[SHOWDOWN] Evento {} pasa a estado EN_CURSO", e.getCodigoInscripcion());
            }
        }

        List<ShowdownEvento> eventosEnCurso = eventoRepo.findAll().stream()
                .filter(e -> e.getEstado() == EstadoShowdown.EN_CURSO)
                .toList();

        for (ShowdownEvento evento : eventosEnCurso) {
            // Verificar si el partido ya tiene estadísticas cargadas
            List<EstadisticaPartido> estadisticas = estadisticaRepo.findByGesPartidoId(evento.getPartido().getGesHash());
            
            if (!estadisticas.isEmpty()) {
                log.info("[SHOWDOWN] Procesando puntajes finales para el evento: {}", evento.getCodigoInscripcion());
                
                // Mapear jugadorId -> Puntos Fantasy en ese partido específico
                Map<Long, Double> puntosPorJugador = estadisticas.stream()
                        .collect(Collectors.toMap(
                                e -> e.getJugadorReal().getId(),
                                EstadisticaPartido::getPuntajeFantasyCalculado,
                                (p1, p2) -> p1 // por si hay duplicados
                        ));

                List<ShowdownParticipante> participantes = participanteRepo.findAllByEventoIdOrderByPuntosTotalesDesc(evento.getId());
                
                for (ShowdownParticipante p : participantes) {
                    double total = 0.0;
                    
                    for (JugadorReal jugador : List.of(p.getBase(), p.getEscolta(), p.getAlero(), p.getAlaPivot(), p.getPivot())) {
                        double puntos = puntosPorJugador.getOrDefault(jugador.getId(), 0.0);
                        if (jugador.getId().equals(p.getCapitanId())) {
                            puntos *= 1.5;
                        }
                        total += puntos;
                    }
                    
                    p.setPuntosTotales(total);
                }
                
                participanteRepo.saveAll(participantes);
                
                evento.setEstado(EstadoShowdown.FINALIZADO);
                eventoRepo.save(evento);
                
                log.info("[SHOWDOWN] Evento {} finalizado exitosamente.", evento.getCodigoInscripcion());
            }
        }
    }
}
