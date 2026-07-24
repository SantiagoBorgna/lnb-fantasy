package com.fantasy.lnb.exception;

public class TransferenciasAgotadasException extends RuntimeException {
    public TransferenciasAgotadasException(int maxTransferencias) {
        super("Se agotaron las " + maxTransferencias + " transferencias permitidas para esta jornada.");
    }
}