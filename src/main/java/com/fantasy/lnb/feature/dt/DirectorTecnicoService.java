package com.fantasy.lnb.feature.dt;

import com.fantasy.lnb.feature.jornada.EstadoPartido;
import com.fantasy.lnb.feature.jornada.Partido;
import com.fantasy.lnb.feature.jornada.PartidoRepository;
import com.fantasy.lnb.feature.plantel.MotorPuntuacionPlantel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectorTecnicoService {

    private final DirectorTecnicoRepository dtRepo;
    private final PartidoRepository partidoRepo;
    private final MotorPuntuacionPlantel motorPuntuacionPlantel;

    @Transactional
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void actualizarPromediosDts() {
        List<DirectorTecnico> dts = dtRepo.findAll();
        int actualizados = 0;

        // El scraper pasa los partidos a PROCESADO apenas carga sus estadísticas, así que
        // mirar solo FINALIZADO dejaba afuera a todos los partidos que ya tienen marcador.
        List<Partido> partidosJugados = partidoRepo
            .findByEstadoIn(List.of(EstadoPartido.FINALIZADO, EstadoPartido.PROCESADO));

        // El promedio es por jornada, no por partido: si el equipo juega más de una vez en
        // la jornada solo cuenta el primero, igual que en el puntaje del plantel. Se le
        // delega el criterio al motor para que ambos salgan siempre del mismo partido.
        Map<Long, List<Partido>> partidosPorJornada = partidosJugados.stream()
            .collect(Collectors.groupingBy(p -> p.getJornada().getId()));

        for (DirectorTecnico dt : dts) {
            Long equipoId = dt.getEquipoReal().getId();

            // Puntaje del DT en cada jornada en que su equipo tiene un partido con marcador
            List<Double> puntajesPorJornada = partidosPorJornada.values().stream()
                .map(partidosJornada -> motorPuntuacionPlantel.calcularPuntajeDtEnJornada(equipoId, partidosJornada))
                .flatMap(Optional::stream)
                .toList();

            if (puntajesPorJornada.isEmpty()) {
                dt.setPromedioFantasy(0.0);
            } else {
                double promedio = puntajesPorJornada.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
                dt.setPromedioFantasy(Math.round(promedio * 100.0) / 100.0);
            }
            actualizados++;
            dtRepo.save(dt);
        }
        
        log.info("[DT-PROMEDIOS] Actualización completada. DTs actualizados: {}", actualizados);
    }
}

