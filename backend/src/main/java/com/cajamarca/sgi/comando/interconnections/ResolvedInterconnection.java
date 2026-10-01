package com.cajamarca.sgi.comando.interconnections;

import java.net.URI;

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
    // ResolutionView del resolver real de CORE (GET /interconnections/{code}/resolve); normalize() los lleva a los campos de arriba.
    public String code;
    public String resolvedUrl;
    public String httpMethod;
    public Integer retries;

    /** CORE responde resolvedUrl, httpMethod, code y retries: se mapean a baseUrl + path, method, interconnectionId y maxRetries. */
    public void normalize() {
        if (interconnectionId == null) interconnectionId = code;
        if (method == null) method = httpMethod;
        if (maxRetries == null) maxRetries = retries;
        if ((baseUrl == null || baseUrl.isBlank()) && resolvedUrl != null && !resolvedUrl.isBlank()) {
            URI u = URI.create(resolvedUrl.trim());
            if (u.getScheme() == null || !u.getScheme().toLowerCase().startsWith("http") || u.getRawAuthority() == null)
                throw new InterconnectionException("INVALID_CORE_RESOLUTION", "CORE returned a non-HTTP resolvedUrl");
            baseUrl = u.getScheme() + "://" + u.getRawAuthority();
            path = (u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath()) + (u.getRawQuery() == null ? "" : "?" + u.getRawQuery());
        }
    }

    /** URL completa del binding (baseUrl + path). */
    public String url() {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + (path.startsWith("/") ? path : "/" + path);
    }

    public int effectiveTimeoutMs() { return timeoutMs == null || timeoutMs < 1 ? 5000 : timeoutMs; }
    public int effectiveMaxRetries() { return maxRetries == null || maxRetries < 0 ? 0 : maxRetries; }
    public int effectiveCacheTtlSeconds(int fallback) { return cacheTtlSeconds == null || cacheTtlSeconds < 1 ? fallback : cacheTtlSeconds; }
    public int effectiveAllowStaleSeconds(int fallback) { return allowStaleSeconds == null || allowStaleSeconds < 0 ? fallback : allowStaleSeconds; }
}
