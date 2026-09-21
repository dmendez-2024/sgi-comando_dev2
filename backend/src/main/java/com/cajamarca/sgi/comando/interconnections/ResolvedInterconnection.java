package com.cajamarca.sgi.comando.interconnections;

/** Effective binding returned by CORE for one Instancia PE + environment + interface. */
public class ResolvedInterconnection {
    public String interconnectionId;
    public String interfaceId;
    public String targetProgramId;
    public String interactionType;
    public String mode;
    public String protocol;
    public String baseUrl;
    public String method;
    public String path;
    public String authType;
    public String credentialRef;
    public Integer timeoutMs;
    public Integer maxRetries;
    public String contractVersion;
    public String configurationVersion;
    public Integer cacheTtlSeconds;
    public Integer allowStaleSeconds;
    public String status;

    public int effectiveTimeoutMs() { return timeoutMs == null || timeoutMs < 1 ? 5000 : timeoutMs; }
    public int effectiveMaxRetries() { return maxRetries == null || maxRetries < 0 ? 0 : maxRetries; }
    public int effectiveCacheTtlSeconds(int fallback) { return cacheTtlSeconds == null || cacheTtlSeconds < 1 ? fallback : cacheTtlSeconds; }
    public int effectiveAllowStaleSeconds(int fallback) { return allowStaleSeconds == null || allowStaleSeconds < 0 ? fallback : allowStaleSeconds; }
}
