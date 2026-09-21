package com.cajamarca.sgi.comando.interconnections;

public class InterconnectionException extends RuntimeException {
    private final String code;
    public InterconnectionException(String code, String message) { super(message); this.code = code; }
    public InterconnectionException(String code, String message, Throwable cause) { super(message, cause); this.code = code; }
    public String code() { return code; }
}
