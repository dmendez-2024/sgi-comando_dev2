package com.cajamarca.sgi.comando.interconnections;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Single reusable outbound integration engine required by SITC-NOM-001 v3.0.
 * Business adapters provide an interconnection/interface ID plus domain payload;
 * this class owns resolution/cache/transport/auth/retry/circuit/observability.
 */
@ApplicationScoped
public class GenericInterconnectionExecutor {
    private static final Logger LOG = Logger.getLogger(GenericInterconnectionExecutor.class);

    @Inject TenantContext tenant;
    @Inject CoreInterconnectionResolver coreResolver;
    @Inject ResolutionCache cache;
    @Inject CredentialRefResolver credentials;
    @Inject CircuitRegistry circuits;
    @Inject ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public IntegrationResult execute(String interconnectionId, String interfaceId, IntegrationRequest input) {
        if (input == null) input = new IntegrationRequest();
        String correlationId = input.correlationId == null || input.correlationId.isBlank() ? UUID.randomUUID().toString() : input.correlationId;
        circuits.assertAvailable(interconnectionId);

        ResolvedInterconnection cfg = cache.resolve(interconnectionId, interfaceId, tenant.instanceCountryId(), coreResolver);
        validateProtocol(cfg);
        String uri = buildUri(cfg, input.pathParams, input.queryParams);
        int maxAttempts = Math.max(1, 1 + cfg.effectiveMaxRetries());
        RuntimeException last = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long started = System.nanoTime();
            try {
                HttpRequest request = buildRequest(cfg, input, uri, correlationId);
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                long elapsed = (System.nanoTime() - started) / 1_000_000L;
                if (response.statusCode() >= 500 && attempt < maxAttempts && retryAllowed(cfg.method, input.idempotencyKey)) {
                    LOG.warnf("integration_retry id=%s interface=%s attempt=%d status=%d corr=%s", interconnectionId, interfaceId, attempt, response.statusCode(), correlationId);
                    sleepBackoff(attempt);
                    continue;
                }
                IntegrationResult result = new IntegrationResult();
                result.statusCode = response.statusCode(); result.body = response.body(); result.correlationId = correlationId;
                result.elapsedMs = elapsed; result.configurationVersion = cfg.configurationVersion; result.attempts = attempt;
                if (result.successful()) circuits.success(interconnectionId); else if (response.statusCode() >= 500) circuits.failure(interconnectionId);
                LOG.infof("integration_result id=%s interface=%s target=%s status=%d ms=%d attempt=%d corr=%s config=%s",
                    interconnectionId, interfaceId, cfg.targetProgramId, response.statusCode(), elapsed, attempt, correlationId, cfg.configurationVersion);
                return result;
            } catch (InterconnectionException e) {
                throw e;
            } catch (Exception e) {
                last = new InterconnectionException("TRANSPORT_FAILURE", "Interconnection transport failed", e);
                if (attempt < maxAttempts && retryAllowed(cfg.method, input.idempotencyKey)) {
                    LOG.warnf(e, "integration_transport_retry id=%s interface=%s attempt=%d corr=%s", interconnectionId, interfaceId, attempt, correlationId);
                    sleepBackoff(attempt);
                    continue;
                }
            }
        }
        circuits.failure(interconnectionId);
        throw last == null ? new InterconnectionException("TRANSPORT_FAILURE", "Interconnection failed") : last;
    }

    private HttpRequest buildRequest(ResolvedInterconnection cfg, IntegrationRequest input, String uri, String correlationId) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(uri))
            .timeout(Duration.ofMillis(cfg.effectiveTimeoutMs()))
            .header("Accept", "application/json")
            .header("X-Correlation-Id", correlationId)
            .header("X-Interconnection-Id", cfg.interconnectionId == null ? "" : cfg.interconnectionId)
            .header("X-Contract-Version", cfg.contractVersion == null ? "" : cfg.contractVersion);

        for (Map.Entry<String,String> h : input.headers.entrySet()) {
            if (!isForbiddenCallerHeader(h.getKey())) b.header(h.getKey(), h.getValue());
        }
        if (input.idempotencyKey != null && !input.idempotencyKey.isBlank()) b.header("Idempotency-Key", input.idempotencyKey);
        applyAuthentication(b, cfg);

        String method = cfg.method.toUpperCase();
        String json = input.body == null ? "" : objectMapper.writeValueAsString(input.body);
        if (!json.isEmpty()) b.header("Content-Type", "application/json");
        switch (method) {
            case "GET" -> b.GET();
            case "DELETE" -> b.DELETE();
            case "POST" -> b.POST(HttpRequest.BodyPublishers.ofString(json));
            case "PUT" -> b.PUT(HttpRequest.BodyPublishers.ofString(json));
            case "PATCH" -> b.method("PATCH", HttpRequest.BodyPublishers.ofString(json));
            default -> throw new InterconnectionException("UNSUPPORTED_METHOD", "Unsupported HTTP method in CORE binding: " + method);
        }
        return b.build();
    }

    private void applyAuthentication(HttpRequest.Builder b, ResolvedInterconnection cfg) {
        String auth = cfg.authType == null ? "NONE" : cfg.authType.toUpperCase();
        if ("NONE".equals(auth)) return;
        String secret = credentials.resolve(cfg.credentialRef);
        switch (auth) {
            case "BEARER", "BEARER_TOKEN", "JWT", "SERVICE_TOKEN", "OAUTH2" -> b.header("Authorization", "Bearer " + secret);
            case "BASIC" -> b.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8)));
            case "MTLS" -> throw new InterconnectionException("MTLS_RUNTIME_ADAPTER_REQUIRED", "mTLS requires the production SSL credential adapter");
            default -> throw new InterconnectionException("UNSUPPORTED_AUTH", "Unsupported authentication type: " + auth);
        }
    }

    private static String buildUri(ResolvedInterconnection cfg, Map<String,String> pathParams, Map<String,String> queryParams) {
        String path = cfg.path;
        for (Map.Entry<String,String> e : pathParams.entrySet()) path = path.replace("{" + e.getKey() + "}", enc(e.getValue()));
        if (path.contains("{")) throw new InterconnectionException("UNRESOLVED_PATH_PARAMETER", "An interface path parameter was not supplied");
        StringBuilder uri = new StringBuilder(trimSlash(cfg.baseUrl));
        if (!path.startsWith("/")) uri.append('/');
        uri.append(path);
        if (!queryParams.isEmpty()) {
            boolean first = !uri.toString().contains("?");
            for (Map.Entry<String,String> e : queryParams.entrySet()) {
                uri.append(first ? '?' : '&'); first = false;
                uri.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
            }
        }
        return uri.toString();
    }

    private static void validateProtocol(ResolvedInterconnection cfg) {
        String protocol = cfg.protocol == null ? "HTTPS" : cfg.protocol.toUpperCase();
        if (!"HTTPS".equals(protocol) && !"HTTP".equals(protocol)) {
            throw new InterconnectionException("TRANSPORT_ADAPTER_REQUIRED", "Resolved protocol requires a non-HTTP transport adapter: " + protocol);
        }
    }
    private static boolean retryAllowed(String method, String idempotencyKey) {
        String m = method == null ? "" : method.toUpperCase();
        return "GET".equals(m) || "PUT".equals(m) || "DELETE".equals(m) || (idempotencyKey != null && !idempotencyKey.isBlank());
    }
    private static void sleepBackoff(int attempt) {
        try { Thread.sleep(Math.min(1000L, 100L * (1L << Math.min(attempt - 1, 3)))); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new InterconnectionException("RETRY_INTERRUPTED", "Retry interrupted", e); }
    }
    private static boolean isForbiddenCallerHeader(String key) {
        return key == null || key.equalsIgnoreCase("Authorization") || key.equalsIgnoreCase("Host") || key.equalsIgnoreCase("X-Interconnection-Id");
    }
    private static String trimSlash(String s) { return s.endsWith("/") ? s.substring(0, s.length()-1) : s; }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
