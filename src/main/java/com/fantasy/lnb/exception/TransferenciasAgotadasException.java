package com.fantasy.lnb.exception;

public class TransferenciasAgotadasException extends RuntimeException {
    public TransferenciasAgotadasException() {
        super("Se agotaron las 3 transferencias permitidas para esta jornada.");
    }
}