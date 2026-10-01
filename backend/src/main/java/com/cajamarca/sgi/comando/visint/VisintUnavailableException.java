package com.cajamarca.sgi.comando.visint;

/** VISINT no respondió o respondió algo inválido; la revisión se reintenta. */
public class VisintUnavailableException extends RuntimeException {
    public VisintUnavailableException(String message) { super(message); }
    public VisintUnavailableException(String message, Throwable cause) { super(message, cause); }
}
