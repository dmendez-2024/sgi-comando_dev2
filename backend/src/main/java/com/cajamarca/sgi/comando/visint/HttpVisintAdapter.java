package com.cajamarca.sgi.comando.visint;

import com.cajamarca.sgi.comando.storage.StorageService;
import com.fasterxml.jackson.databind.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * VISINT real (POST multipart/form-data, ver docs/API_CONTRACTS.md): en "image" va la foto del agente y en
 * "referenceImages" (+ "referenceIds") las fotos estándar del Hito (1 a 5). VISINT responde el status y la foto estándar que coincidió.
 * La URL la da {@link VisintEndpoint} (CORE y, si falla, SGI_VISINT_URL).
 */
@ApplicationScoped
public class HttpVisintAdapter implements VisintPort {
    private static final Logger LOG = Logger.getLogger(HttpVisintAdapter.class);
    @Inject StorageService storage;
    @Inject ObjectMapper mapper;
    @Inject VisintEndpoint endpoint;
    @ConfigProperty(name="sgi.visint.token") Optional<String> token;
    @ConfigProperty(name="sgi.visint.auth-header", defaultValue="X-API-Key") String authHeader;
    @ConfigProperty(name="sgi.visint.auth-scheme") Optional<String> authScheme;
    @ConfigProperty(name="sgi.visint.timeout-seconds", defaultValue="60") int timeoutSeconds;

    @Override
    public ReviewResult review(ReviewRequest r) {
        String url = endpoint.url();
        Multipart form = new Multipart();
        form.field("requestId", r.reviewId() + "-" + r.attempt());
        form.field("source", "SGI_COM");
        form.field("companyId", r.companyId());
        form.field("pointId", r.pointId());
        form.field("postId", r.postId());
        form.field("serviceType", serviceType(r.taskType()));
        form.field("serviceId", r.serviceId());
        form.field("activityId", r.activityId());
        EvidenceRef photo = r.evidence();
        form.field("evidenceId", photo.evidenceId());
        // Umbral de decisión que exige VISINT en cada petición: PASS si la mejor coincidencia lo alcanza o supera.
        form.field("matchThreshold", MatchThreshold.format(r.matchThreshold()));
        form.field("capturedAt", photo.capturedAt());
        form.field("latitude", photo.latitude());
        form.field("longitude", photo.longitude());
        form.file("image", photo.evidenceId().toString(), photo.contentType(), photo(photo.bucket(), photo.objectKey()));
        for (StandardRef s : r.standards()) {
            form.file("referenceImages", s.imageId().toString(), s.contentType(), photo(s.bucket(), s.objectKey()));
            form.field("referenceIds", s.imageId());
        }
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(timeoutSeconds))
            .header("Content-Type", "multipart/form-data; boundary=" + form.boundary)
            .header("X-Correlation-Id", r.correlationId().toString())
            .POST(HttpRequest.BodyPublishers.ofByteArray(form.bytes()));
        if (token.isPresent() && !token.get().isBlank()) req.header(authHeader, authScheme.filter(s -> !s.isBlank()).map(s -> s + " " + token.get()).orElse(token.get()));
        HttpResponse<String> res;
        try {
            res = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build().send(req.build(), HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new VisintUnavailableException("VISINT no disponible: llamada interrumpida", e);
        } catch (Exception e) {
            LOG.warnf("VISINT no respondió en %s (requestId %s-%d): %s", url, r.reviewId(), r.attempt(), e.getMessage());
            throw new VisintUnavailableException("VISINT no disponible: " + e.getMessage(), e);
        }
        if (res.statusCode() / 100 != 2) {
            // VISINT puede responder un veredicto estructurado con código de error (p. ej. 500 + ERROR_VISINT): se respeta.
            if (hasStatus(res.body(), mapper)) return parseResult(res.body(), mapper);
            String body = res.body() == null ? "" : res.body().replaceAll("\\s+", " ").trim();
            throw new VisintUnavailableException("VISINT respondió HTTP " + res.statusCode() + (body.isEmpty() ? "" : ": " + body.substring(0, Math.min(300, body.length()))));
        }
        return parseResult(res.body(), mapper);
    }

    /**
     * Estados del plan de integración VISINT: PASS → cumple. FAIL_QUALITY / FAIL_NO_MATCH / FAIL_INVALID_IMAGE (u otro con
     * match.compatible=false) → no cumple. TIMEOUT / PROCESSING → VISINT no alcanzó a evaluar: se reintenta. ERROR_VISINT u otro
     * → ERROR (falla técnica, no es incumplimiento). Los puntajes (quality/match) se guardan solo como dato: no cambian el veredicto.
     */
    public static ReviewResult parseResult(String json, ObjectMapper mapper) {
        JsonNode n;
        try { n = mapper.readTree(json); } catch (Exception e) { throw new VisintUnavailableException("Respuesta de VISINT inválida", e); }
        String status = n.path("status").asText("").toUpperCase(Locale.ROOT);
        String reason = text(n, "reasonCode");
        if (status.isEmpty()) throw new VisintUnavailableException("Respuesta de VISINT sin status");
        if (status.equals("TIMEOUT") || status.equals("PROCESSING") || (reason != null && reason.toUpperCase(Locale.ROOT).endsWith("TIMEOUT")))
            throw new VisintUnavailableException("VISINT no alcanzó a evaluar (" + (reason == null ? status : reason) + ")");
        String result = status.equals("PASS") ? "PASS"
            : status.startsWith("FAIL") ? "FAIL"
            : status.startsWith("ERROR") ? "ERROR"
            : n.path("match").path("compatible").isBoolean() && !n.path("match").path("compatible").asBoolean() ? "FAIL" : "ERROR";
        String code = reason == null ? status : reason;
        String findings = "Código VISINT: " + code + (!code.equals(status) && !result.equals("PASS") ? " (" + status + ")" : "");
        return new ReviewResult(text(n, "requestId"), result, findings, result.equals("PASS") ? uuid(text(n, "matchedReferenceId")) : null, code, text(n, "modelVersion"),
            bool(n.path("quality"), "valid"), number(n.path("quality"), "score"), bool(n.path("match"), "compatible"), number(n.path("match"), "score"));
    }

    /** Si no se puede leer una foto se trata como falla temporal: sigue la regla de reintentos y termina en ERROR_FINAL. */
    private byte[] photo(String bucket, String key) {
        try { return storage.read(bucket, key); }
        catch (RuntimeException e) { throw new VisintUnavailableException("No se pudo leer la foto para VISINT: " + e.getMessage(), e); }
    }

    private static boolean hasStatus(String json, ObjectMapper mapper) {
        try { return json != null && !mapper.readTree(json).path("status").asText("").isBlank(); } catch (Exception e) { return false; }
    }

    static String serviceType(String taskType) {
        return switch (taskType) { case "PATROL_CHECKPOINT" -> "PATRULLA"; case "CONSIGNMENT_EVIDENCE" -> "CONSIGNA"; case "LOGBOOK_FIELD" -> "BITACORA"; case "POST_CONFIG" -> "RELEVO"; default -> taskType; };
    }
    private static Boolean bool(JsonNode n, String f) { return n.path(f).isBoolean() ? n.path(f).asBoolean() : null; }
    private static Double number(JsonNode n, String f) { return n.path(f).isNumber() ? n.path(f).asDouble() : null; }
    private static String text(JsonNode n, String f) { return n.hasNonNull(f) && !n.path(f).asText().isBlank() ? n.path(f).asText() : null; }
    private static UUID uuid(String s) { try { return s == null ? null : UUID.fromString(s); } catch (IllegalArgumentException e) { return null; } }

    /** Cuerpo multipart/form-data mínimo (campos de texto y archivos). */
    static final class Multipart {
        final String boundary = "sgi-" + UUID.randomUUID();
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        void field(String name, Object value) {
            if (value == null) return;
            write("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n");
        }
        void file(String name, String baseName, String contentType, byte[] data) {
            String ext = switch (contentType == null ? "" : contentType) { case "image/png" -> ".png"; case "image/webp" -> ".webp"; default -> ".jpg"; };
            write("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"; filename=\"" + baseName + ext + "\"\r\nContent-Type: "
                + (contentType == null ? "image/jpeg" : contentType) + "\r\n\r\n");
            out.writeBytes(data);
            write("\r\n");
        }
        byte[] bytes() { write("--" + boundary + "--\r\n"); return out.toByteArray(); }
        private void write(String s) { out.writeBytes(s.getBytes(StandardCharsets.UTF_8)); }
    }
}
