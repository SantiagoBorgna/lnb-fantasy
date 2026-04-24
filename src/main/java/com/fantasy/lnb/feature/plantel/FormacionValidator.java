package com.fantasy.lnb.feature.plantel;

import com.fantasy.lnb.feature.mercado.PosicionJugador;
import java.util.List;
import java.util.Map;

/**
 * Valida que la formación declarada coincida con las posiciones
 * reales de los jugadores titulares.
 *
 * Formaciones permitidas según el PRD:
 * 1-2-2 | 1-3-1 | 2-1-2 | 2-2-1 | 3-1-1
 *
 * El orden es estricto: Bases/Escoltas - Aleros/AlaPivots - Pivots
 * Ejemplo "2-1-2": 2 bases/escoltas, 1 alero/alapivot, 2 pivots
 */
public class FormacionValidator {

    // Formaciones válidas: clave = string, valor = [guards, forwards, centers]
    private static final Map<String, int[]> FORMACIONES_VALIDAS = Map.of(
            "1-2-2", new int[] { 1, 2, 2 },
            "1-3-1", new int[] { 1, 3, 1 },
            "2-1-2", new int[] { 2, 1, 2 },
            "2-2-1", new int[] { 2, 2, 1 },
            "3-1-1", new int[] { 3, 1, 1 });

    /**
     * @param formacion String de la formación declarada (ej: "2-1-2")
     * @param titulares Lista de posiciones de los 5 jugadores titulares
     *                  (incluye al capitán)
     * @return true si la formación es válida y coincide con las posiciones
     */
    public static boolean esValida(
            String formacion,
            List<PosicionJugador> titulares) {

        if (!FORMACIONES_VALIDAS.containsKey(formacion)) {
            return false;
        }

        int[] requeridos = FORMACIONES_VALIDAS.get(formacion);

        long guards = titulares.stream().filter(FormacionValidator::esGuard).count();
        long forwards = titulares.stream().filter(FormacionValidator::esForward).count();
        long centers = titulares.stream().filter(FormacionValidator::esCenter).count();

        return guards == requeridos[0]
                && forwards == requeridos[1]
                && centers == requeridos[2];
    }

    public static boolean esFormacionValida(String formacion) {
        return FORMACIONES_VALIDAS.containsKey(formacion);
    }

    // ── Clasificación de posiciones ─────────────────────────────────────────
    // Guard = Base, Escolta
    // Forward = Alero, Ala-Pivot
    // Center = Pivot

    private static boolean esGuard(PosicionJugador p) {
        return p == PosicionJugador.BASE || p == PosicionJugador.ESCOLTA;
    }

    private static boolean esForward(PosicionJugador p) {
        return p == PosicionJugador.ALERO || p == PosicionJugador.ALA_PIVOT;
    }

    private static boolean esCenter(PosicionJugador p) {
        return p == PosicionJugador.PIVOT;
    }

    private FormacionValidator() {
    }
}