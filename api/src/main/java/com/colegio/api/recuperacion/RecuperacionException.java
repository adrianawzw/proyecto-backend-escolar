package com.colegio.api.recuperacion;

public class RecuperacionException extends RuntimeException {

    private final int status;
    private final long retryAfterSegundos;

    public RecuperacionException(int status, String mensaje) {
        this(status, mensaje, 0);
    }

    public RecuperacionException(int status, String mensaje, long retryAfterSegundos) {
        super(mensaje);
        this.status = status;
        this.retryAfterSegundos = retryAfterSegundos;
    }

    public int getStatus() {
        return status;
    }

    public long getRetryAfterSegundos() {
        return retryAfterSegundos;
    }
}
