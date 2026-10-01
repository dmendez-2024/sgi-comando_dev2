package com.cajamarca.sgi.comando.interconnections;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@ApplicationScoped
public class CoreInterconnectionResolver {
    private static final Logger LOG = Logger.getLogger(CoreInterconnectionResolver.class);

    // Opcional: sin URL el backend arranca igual y cada llamada falla con CORE_RESOLVER_NOT_CONFIGURED.
    @ConfigProperty(name="sgi.interconnections.core-resolver-url")
    java.util.Optional<String> coreResolverUrl;

    @ConfigProperty(name="sgi.interconnections.environment")
    String environment;

    @ConfigProperty(name="sgi.interconnections.core-timeout-ms", defaultValue="2500")
    int coreTimeoutMs;

    @Inject ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build();

    public ResolvedInterconnection resolve(String interconnectionId, String interfaceId, UUID instanceCountryId) {
        if (coreResolverUrl.isEmpty() || coreResolverUrl.get().isBlank()) {
            throw new InterconnectionException("CORE_RESOLVER_NOT_CONFIGURED", "CORE resolver bootstrap URL is not configured");
        }
        try {
            String url = trimSlash(coreResolverUrl.get())
                + "/api/v1/interconnections/" + enc(interconnectionId)
                + "/resolve?instanceCountryId=" + enc(instanceCountryId.toString())
                + "&environment=" + enc(environment)
                + (interfaceId == null ? "" : "&interfaceId=" + enc(interfaceId));

            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(Math.max(coreTimeoutMs, 250)))
                .header("Accept", "application/json")
                .header("X-Correlation-Id", UUID.randomUUID().toString())
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new InterconnectionException("CORE_RESOLUTION_HTTP_" + response.statusCode(), "CORE could not resolve interconnection binding");
            }
            ResolvedInterconnection resolved = objectMapper.readValue(response.body(), ResolvedInterconnection.class);
            if (resolved != null) resolved.normalize();
            validateResolved(resolved, interconnectionId, interfaceId);
            return resolved;
        } catch (InterconnectionException e) {
            throw e;
        } catch (Exception e) {
            LOG.warnf(e, "CORE resolution failed for %s / %s", interconnectionId, interfaceId);
            throw new InterconnectionException("CORE_RESOLUTION_FAILED", "Unable to resolve effective binding from CORE", e);
        }
    }

    private static void validateResolved(ResolvedInterconnection r, String expectedId, String expectedInterface) {
        if (r == null || r.baseUrl == null || r.baseUrl.isBlank() || r.path == null || r.method == null) {
            throw new InterconnectionException("INVALID_CORE_RESOLUTION", "CORE returned an incomplete binding");
        }
        if (r.interconnectionId != null && !expectedId.equalsIgnoreCase(r.interconnectionId)) {
            throw new InterconnectionException("CORE_RESOLUTION_ID_MISMATCH", "CORE resolved a different interconnection ID");
        }
        if (expectedInterface != null && r.interfaceId != null && !expectedInterface.equals(r.interfaceId)) {
            throw new InterconnectionException("CORE_RESOLUTION_INTERFACE_MISMATCH", "CORE resolved a different interface ID");
        }
    }

    private static String trimSlash(String value) { return value.endsWith("/") ? value.substring(0, value.length() - 1) : value; }
    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
