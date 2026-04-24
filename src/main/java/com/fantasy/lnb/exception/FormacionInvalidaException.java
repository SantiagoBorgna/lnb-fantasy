package com.fantasy.lnb.exception;

// Formación inválida o incompatible con las posiciones de los titulares
public class FormacionInvalidaException extends RuntimeException {
    public FormacionInvalidaException(String formacion) {
        super("Formación inválida o incompatible con las posiciones: " + formacion);
    }
}