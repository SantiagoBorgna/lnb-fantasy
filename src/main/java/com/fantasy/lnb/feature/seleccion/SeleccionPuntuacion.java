package com.fantasy.lnb.feature.seleccion;

import com.fantasy.lnb.feature.estadisticas.MotorPuntuacion;
import com.fantasy.lnb.scraper.dto.JugadorStatsDto;
import com.fantasy.lnb.scraper.dto.TirosEstatDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Puntajes de un partido de la selección con las mismas reglas que la liga (MotorPuntuacion).
 * La valoración (para el MVP) no viene de GES: se calcula con la fórmula clásica
 * (positivos - tiros fallados - pérdidas - tapones recibidos - faltas cometidas).
 */
final class SeleccionPuntuacion {

    static final double MULTIPLICADOR_CAPITAN = 1.5;

    private SeleccionPuntuacion() {
    }

    static int valoracion(SeleccionEstadistica e) {
        return e.getPuntos() + e.getRebotesOfensivos() + e.getRebotesDefensivos() + e.getAsistencias()
                + e.getRecuperaciones() + e.getTaponesRealizados() + e.getFaltasRecibidas()
                - e.getTirosCampoFallados() - e.getTirosLibresFallados() - e.getPerdidas()
                - e.getTaponesRecibidos() - e.getFaltasCometidas();
    }

    /**
     * Completa valoración, equipoGano, MVP (la valoración más alta del partido, con empates)
     * y el puntaje fantasy de cada planilla del partido.
     */
    static void calcular(SeleccionEvento evento, Collection<SeleccionEstadistica> planillas) {
        planillas.forEach(e -> e.setValoracion(valoracion(e)));
        int maxima = planillas.stream().mapToInt(SeleccionEstadistica::getValoracion).max().orElse(0);

        Long ganadorId = ganadorId(evento);
        for (SeleccionEstadistica e : planillas) {
            e.setEquipoGano(ganadorId != null && ganadorId.equals(e.getJugador().getEquipo().getId()));
            e.setFueMvp(maxima > 0 && e.getValoracion() == maxima);
            e.setPuntajeFantasy(MotorPuntuacion.calcular(aStatsDto(e), e.getEquipoGano(), e.getFueMvp()));
        }
    }

    /** Suma del quinteto con el capitán x1.5. Un jugador sin planilla (no jugó) suma 0. */
    static double puntosDe(SeleccionParticipante participante, Map<Long, Double> puntajePorJugador) {
        double total = 0.0;
        for (SeleccionJugador jugador : participante.quinteto()) {
            double puntos = puntajePorJugador.getOrDefault(jugador.getId(), 0.0);
            if (jugador.getId().equals(participante.getCapitanId())) {
                puntos *= MULTIPLICADOR_CAPITAN;
            }
            total += puntos;
        }
        return Math.round(total * 100.0) / 100.0;
    }

    static Map<Long, Double> puntajePorJugador(List<SeleccionEstadistica> planillas) {
        return planillas.stream().collect(Collectors.toMap(
                e -> e.getJugador().getId(), SeleccionEstadistica::getPuntajeFantasy, (a, b) -> a));
    }

    static Map<Long, SeleccionEstadistica> porJugador(List<SeleccionEstadistica> planillas) {
        return planillas.stream().collect(Collectors.toMap(e -> e.getJugador().getId(), Function.identity(), (a, b) -> a));
    }

    private static Long ganadorId(SeleccionEvento evento) {
        Integer local = evento.getPuntosLocal();
        Integer visitante = evento.getPuntosVisitante();
        if (local == null || visitante == null || local.equals(visitante)) {
            return null;
        }
        return local > visitante ? evento.getLocal().getId() : evento.getVisitante().getId();
    }

    private static JugadorStatsDto aStatsDto(SeleccionEstadistica e) {
        JugadorStatsDto dto = new JugadorStatsDto();
        dto.setIdJugador(e.getJugador().getId());
        dto.setNombre(e.getJugador().getNombreCompleto());
        dto.setPuntos(e.getPuntos());
        dto.setReboteOfensivo(e.getRebotesOfensivos());
        dto.setReboteDefensivo(e.getRebotesDefensivos());
        dto.setAsistencias(e.getAsistencias());
        dto.setRecuperaciones(e.getRecuperaciones());
        dto.setPerdidas(e.getPerdidas());
        dto.setTaponesRealizados(e.getTaponesRealizados());
        dto.setTaponesRecibidos(e.getTaponesRecibidos());
        dto.setFaltaCometida(e.getFaltasCometidas());
        dto.setFaltaRecibida(e.getFaltasRecibidas());
        dto.setCincoInicial(e.getTitular());
        dto.setValoracion(e.getValoracion());
        // El motor suma dobles + triples fallados: con el total de tiros de campo alcanza
        dto.setTirosDos(fallados(e.getTirosCampoFallados()));
        dto.setTirosLibres(fallados(e.getTirosLibresFallados()));
        return dto;
    }

    private static TirosEstatDto fallados(int cantidad) {
        TirosEstatDto tiros = new TirosEstatDto();
        tiros.setFallados(cantidad);
        return tiros;
    }
}
