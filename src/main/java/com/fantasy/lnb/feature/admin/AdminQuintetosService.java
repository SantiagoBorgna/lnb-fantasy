package com.fantasy.lnb.feature.admin;

import com.fantasy.lnb.feature.admin.dto.AdminQuintetosResponseDto;
import com.fantasy.lnb.feature.admin.dto.JugadorQuintetoDto;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartido;
import com.fantasy.lnb.feature.estadisticas.EstadisticaPartidoRepository;
import com.fantasy.lnb.feature.mercado.PosicionJugador;
import com.fantasy.lnb.feature.plantel.PlantelJornada;
import com.fantasy.lnb.feature.plantel.PlantelJornadaRepository;
import com.fantasy.lnb.feature.plantel.JugadorPlantel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminQuintetosService {

    private final PlantelJornadaRepository plantelJornadaRepo;
    private final EstadisticaPartidoRepository estadisticaRepo;

    public AdminQuintetosResponseDto getQuintetosPorJornada(Long jornadaId) {
        // Obtenemos estadisticas de la jornada
        List<EstadisticaPartido> estadisticasJornada = estadisticaRepo.findByJornada_Id(jornadaId);
        Map<Long, EstadisticaPartido> statsMap = estadisticasJornada.stream()
                .filter(e -> e.getJugadorReal() != null)
                .collect(Collectors.toMap(e -> e.getJugadorReal().getId(), e -> e, (e1, e2) -> e1));

        // 1. Mejor Quinteto de Usuario
        Optional<PlantelJornada> mejorPlantelOpt = plantelJornadaRepo
                .findFirstByJornada_IdAndTorneoIsNullOrderByPuntajeObtenidoFechaDesc(jornadaId);

        String nombreUsuario = "N/A";
        Double puntajeUsuario = 0.0;
        List<JugadorQuintetoDto> mejorQuintetoUsuario = new ArrayList<>();

        if (mejorPlantelOpt.isPresent()) {
            PlantelJornada mejorPlantel = mejorPlantelOpt.get();
            nombreUsuario = mejorPlantel.getUsuario().getNombreDisplay();
            puntajeUsuario = mejorPlantel.getPuntajeObtenidoFecha() != null ? mejorPlantel.getPuntajeObtenidoFecha() : 0.0;

            mejorQuintetoUsuario = mejorPlantel.getTitulares().stream()
                    .map(pj -> {
                        EstadisticaPartido stat = statsMap.get(pj.getJugadorReal().getId());
                        Double puntos = stat != null && stat.getPuntajeFantasyCalculado() != null ? stat.getPuntajeFantasyCalculado() : 0.0;
                        boolean esCapitan = pj.getRol() == com.fantasy.lnb.feature.plantel.RolPlantel.CAPITAN;
                        if (esCapitan) {
                            puntos *= 1.5;
                        }
                        
                        return JugadorQuintetoDto.builder()
                            .id(pj.getJugadorReal().getId())
                            .nombre(pj.getJugadorReal().getNombreCompleto())
                            .apellido("") // nombreCompleto tiene todo
                            .clubReal(pj.getJugadorReal().getEquipoReal() != null ? pj.getJugadorReal().getEquipoReal().getSigla() : "N/A")
                            .posicion(pj.getJugadorReal().getPosicion())
                            .puntosFantasy(puntos)
                            .esCapitan(esCapitan)
                            .build();
                    })
                    .collect(Collectors.toList());
        }

        // 2. Quinteto Ideal Teórico
        // Agrupar por posición y obtener el mejor de cada posición
        Map<PosicionJugador, Optional<EstadisticaPartido>> mejoresPorPosicion = estadisticasJornada.stream()
                .filter(e -> e.getPuntajeFantasyCalculado() != null && e.getJugadorReal() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getJugadorReal().getPosicion(),
                        Collectors.maxBy(Comparator.comparing(EstadisticaPartido::getPuntajeFantasyCalculado))
                ));

        List<JugadorQuintetoDto> quintetoIdealTeorico = new ArrayList<>();
        Double puntajeTotalIdeal = 0.0;
        EstadisticaPartido capitanIdeal = null;

        for (PosicionJugador pos : PosicionJugador.values()) {
            Optional<EstadisticaPartido> mejorEnPosOpt = mejoresPorPosicion.getOrDefault(pos, Optional.empty());
            if (mejorEnPosOpt.isPresent()) {
                EstadisticaPartido mejorEnPos = mejorEnPosOpt.get();
                if (capitanIdeal == null || mejorEnPos.getPuntajeFantasyCalculado() > capitanIdeal.getPuntajeFantasyCalculado()) {
                    capitanIdeal = mejorEnPos;
                }
            }
        }

        for (PosicionJugador pos : PosicionJugador.values()) {
            Optional<EstadisticaPartido> mejorEnPosOpt = mejoresPorPosicion.getOrDefault(pos, Optional.empty());
            if (mejorEnPosOpt.isPresent()) {
                EstadisticaPartido mejorEnPos = mejorEnPosOpt.get();
                boolean esCapitan = mejorEnPos.equals(capitanIdeal);
                Double puntos = mejorEnPos.getPuntajeFantasyCalculado();
                if (esCapitan) {
                    puntos = puntos * 1.5;
                }
                
                puntajeTotalIdeal += puntos;

                quintetoIdealTeorico.add(JugadorQuintetoDto.builder()
                        .id(mejorEnPos.getJugadorReal().getId())
                        .nombre(mejorEnPos.getJugadorReal().getNombreCompleto())
                        .apellido("")
                        .clubReal(mejorEnPos.getJugadorReal().getEquipoReal() != null ? mejorEnPos.getJugadorReal().getEquipoReal().getSigla() : "N/A")
                        .posicion(mejorEnPos.getJugadorReal().getPosicion())
                        .puntosFantasy(puntos)
                        .esCapitan(esCapitan)
                        .build());
            }
        }

        return AdminQuintetosResponseDto.builder()
                .nombreUsuarioGanador(nombreUsuario)
                .puntajeUsuarioGanador(puntajeUsuario)
                .mejorQuintetoUsuario(mejorQuintetoUsuario)
                .puntajeQuintetoIdeal(puntajeTotalIdeal)
                .quintetoIdealTeorico(quintetoIdealTeorico)
                .build();
    }
}
