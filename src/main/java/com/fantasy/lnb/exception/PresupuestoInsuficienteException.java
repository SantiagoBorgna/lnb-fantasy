package com.fantasy.lnb.exception;

// Presupuesto insuficiente para comprar los jugadores seleccionados
public class PresupuestoInsuficienteException extends RuntimeException {
    public PresupuestoInsuficienteException(double presupuesto, double costo) {
        super(String.format(
                "Presupuesto insuficiente. Disponible: %.2f | Costo del plantel: %.2f",
                presupuesto, costo));
    }
}