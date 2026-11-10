package com.cajamarca.sgi.comando.operator;

/** Error controlado de las dependencias utilizadas al aprobar una novedad. */
public class NoveltyIntegrationException extends RuntimeException {
    private final String code;
    private final int status;

    public NoveltyIntegrationException(String code, int status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public NoveltyIntegrationException(String code, int status, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public int status() { return status; }
}
