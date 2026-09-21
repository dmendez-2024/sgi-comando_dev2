package com.cajamarca.sgi.comando.interconnections;

public class IntegrationResult {
    public int statusCode;
    public String body;
    public String correlationId;
    public long elapsedMs;
    public String configurationVersion;
    public int attempts;

    public boolean successful() { return statusCode >= 200 && statusCode < 300; }
}
