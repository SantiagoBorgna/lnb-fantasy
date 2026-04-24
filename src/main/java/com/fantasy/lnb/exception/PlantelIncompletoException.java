package com.fantasy.lnb.exception;

public class PlantelIncompletoException extends RuntimeException {
    public PlantelIncompletoException(Long jornadaId) {
        super("El plantel de la jornada " + jornadaId +
                " está incompleto y no puede ser puntuado.");
    }
}