package com.fantasy.lnb.exception;

// Intento de modificar el plantel con la jornada EN_JUEGO
public class JornadaEnJuegoException extends RuntimeException {
    public JornadaEnJuegoException() {
        super("No se puede modificar el plantel mientras la jornada está EN_JUEGO.");
    }
}