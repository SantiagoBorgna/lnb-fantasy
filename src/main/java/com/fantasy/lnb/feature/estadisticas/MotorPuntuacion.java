package com.fantasy.lnb.feature.estadisticas;

import com.fantasy.lnb.scraper.dto.JugadorStatsDto;
import lombok.extern.slf4j.Slf4j;

/**
 * Clase utilitaria sin estado (@Component no necesario).
 * Centraliza TODAS las reglas de puntuación del PRD en un único lugar
 * para que cualquier cambio futuro de pesos afecte a todo el sistema.
 *
 * VALORES DEL PRD:
 * +1 por punto anotado
 * +1 por rebote defensivo
 * +1.5 por rebote ofensivo
 * +1.5 por asistencia
 * +1.5 por recuperación (robo)
 * +1.5 por tapón realizado
 * -0.5 por tapón recibido
 * +5 doble-doble / +15 triple-doble
 * +1 si fue titular en partido real
 * +3 si el equipo ganó (bono fijo)
 * -1.5 por pérdida de balón
 * -1 por tiro de campo fallado
 * -1 por tiro libre fallado
 * +1 por falta recibida
 * -1 por falta cometida
 * -3 si fue expulsado por 5ta falta
 * -3 por falta técnica/antideportiva
 * -5 por descalificación
 */
@Slf4j
public class MotorPuntuacion {

    // ── Constantes del PRD ──────────────────────────────────────────────────
    private static final double PTS_PUNTO = 1.0;
    private static final double PTS_REBOTE_DEF = 1.0;
    private static final double PTS_REBOTE_OF = 1.5;
    private static final double PTS_ASISTENCIA = 1.5;
    private static final double PTS_RECUPERACION = 1.5;
    private static final double PTS_TAPON_REALIZADO = 1.5;
    private static final double PTS_TAPON_RECIBIDO = -0.5;
    private static final double PTS_DOBLE_DOBLE = 5.0;
    private static final double PTS_TRIPLE_DOBLE = 15.0;
    private static final double PTS_FUE_TITULAR = 1.0;
    private static final double PTS_EQUIPO_GANO = 3.0;
    private static final double PTS_PERDIDA = -1.5;
    private static final double PTS_FALTA_RECIBIDA = 1.0;
    private static final double PTS_FALTA_COMETIDA = -1.0;
    private static final double PTS_EXPULSADO_FALTAS = -3.0;
    private static final double PTS_FALTA_TECNICA = -3.0;
    private static final double PTS_DESCALIFICADO = -5.0;
    private static final double PTS_TIRO_CAMPO_FALLADO = -1.0;
    private static final double PTS_TIRO_LIBRE_FALLADO = -1.0;

    // ── Método principal ────────────────────────────────────────────────────

    /**
     * @param dto        Stats scrapeadas del jugador
     * @param equipoGano true si el equipo del jugador ganó el partido
     * @return puntaje Fantasy calculado según las reglas del PRD
     */
    public static double calcular(JugadorStatsDto dto, boolean equipoGano) {
        double puntaje = 0.0;

        // Valores con null-safe (GES puede omitir campos en jugadores con 0 minutos)
        int puntos = safe(dto.getPuntos());
        int rebDef = safe(dto.getReboteDefensivo());
        int rebOf = safe(dto.getReboteOfensivo());
        int asistencias = safe(dto.getAsistencias());
        int recuperos = safe(dto.getRecuperaciones());
        int perdidas = safe(dto.getPerdidas());
        int taponesRealizados = safe(dto.getTaponesRealizados());
        int taponesRecibidos = safe(dto.getTaponesRecibidos());
        int fc = safe(dto.getFaltaCometida());
        int fr = safe(dto.getFaltaRecibida());

        // ── Extracción segura de tiros fallados ──
        int tiros2Fallados = dto.getTirosDos() != null ? safe(dto.getTirosDos().getFallados()) : 0;
        int tiros3Fallados = dto.getTirosTres() != null ? safe(dto.getTirosTres().getFallados()) : 0;
        int tirosLibresFallados = dto.getTirosLibres() != null ? safe(dto.getTirosLibres().getFallados()) : 0;
        int tirosCampoFallados = tiros2Fallados + tiros3Fallados;

        // ── Estadísticas positivas básicas ──
        puntaje += puntos * PTS_PUNTO;
        puntaje += rebDef * PTS_REBOTE_DEF;
        puntaje += rebOf * PTS_REBOTE_OF;
        puntaje += asistencias * PTS_ASISTENCIA;
        puntaje += recuperos * PTS_RECUPERACION;
        puntaje += taponesRealizados * PTS_TAPON_REALIZADO;

        // ── Bonos de doble-doble y triple-doble ──
        puntaje += calcularBonoMultiDouble(puntos, rebDef + rebOf, asistencias, recuperos);

        // ── Titular en partido real ──
        if (Boolean.TRUE.equals(dto.getCincoInicial())) {
            puntaje += PTS_FUE_TITULAR;
        }

        // ── Bono de victoria ──
        if (equipoGano) {
            puntaje += PTS_EQUIPO_GANO;
        }

        // ── Estadísticas negativas ──
        puntaje += perdidas * PTS_PERDIDA;
        puntaje += fr * PTS_FALTA_RECIBIDA;
        puntaje += fc * PTS_FALTA_COMETIDA;
        puntaje += taponesRecibidos * PTS_TAPON_RECIBIDO;

        // ── Tiros fallados ──
        puntaje += tirosCampoFallados * PTS_TIRO_CAMPO_FALLADO;
        puntaje += tirosLibresFallados * PTS_TIRO_LIBRE_FALLADO;

        // ── Expulsión por 5ta falta (heurística: FaltaCometida == 5 en LNB) ──
        if (fc >= 5) {
            puntaje += PTS_EXPULSADO_FALTAS;
            log.debug("[MOTOR] {} expulsado por faltas (fc={})", dto.getNombre(), fc);
        }

        // ── Falta técnica y descalificación (no disponibles en GES aún) ──
        // Se deja la lógica lista:
        // if (dto.getFaltaTecnica()) puntaje += PTS_FALTA_TECNICA;
        // if (dto.getDescalificado()) puntaje += PTS_DESCALIFICADO;

        log.debug("[MOTOR] {} : puntaje bruto calculado: {}", dto.getNombre(), puntaje);
        return Math.round(puntaje * 100.0) / 100.0; // Redondeo a 2 decimales
    }

    // ── Helpers privados ────────────────────────────────────────────────────

    /**
     * Detecta doble-doble y triple-doble contando categorías con valor >= 10.
     * Categorías válidas: puntos, rebotes totales, asistencias, recuperaciones.
     */
    private static double calcularBonoMultiDouble(
            int puntos, int rebotes, int asistencias, int recuperos) {

        int categoriasCon10 = 0;
        if (puntos >= 10)
            categoriasCon10++;
        if (rebotes >= 10)
            categoriasCon10++;
        if (asistencias >= 10)
            categoriasCon10++;
        if (recuperos >= 10)
            categoriasCon10++;

        if (categoriasCon10 >= 3)
            return PTS_TRIPLE_DOBLE;
        if (categoriasCon10 == 2)
            return PTS_DOBLE_DOBLE;
        return 0.0;
    }

    /** Evita NullPointerException en campos que GES puede omitir. */
    private static int safe(Integer valor) {
        return valor != null ? valor : 0;
    }

    private MotorPuntuacion() {
    } // Clase utilitaria, no instanciar
}