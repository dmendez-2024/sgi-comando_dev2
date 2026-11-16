package com.cajamarca.sgi.comando.rrhh;

import com.cajamarca.sgi.comando.interconnections.GenericInterconnectionExecutor;
import com.cajamarca.sgi.comando.interconnections.IntegrationRequest;
import com.cajamarca.sgi.comando.interconnections.IntegrationResult;
import com.cajamarca.sgi.comando.interconnections.InterconnectionException;
import com.cajamarca.sgi.comando.interconnections.InterconnectionIds;
import com.cajamarca.sgi.comando.storage.ImageSniffer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Consulta la foto del empleado en DHO sin persistirla en SGI Comando. */
@ApplicationScoped
public class DhoEmployeePhotoAdapter {
    private static final int MAX_PHOTO_BYTES = 5 * 1024 * 1024;
    private static final int MAX_BASE64_CHARS = ((MAX_PHOTO_BYTES + 2) / 3) * 4;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    @Inject GenericInterconnectionExecutor executor;
    @Inject ObjectMapper mapper;

    public record EmployeePhoto(byte[] bytes, String contentType) {}

    /** Devuelve null cuando DHO confirma que no existe una foto para la persona. */
    public EmployeePhoto fetch(long personaId) {
        if (personaId <= 0) return null;

        IntegrationRequest request = new IntegrationRequest();
        request.body = Map.of("personaId", personaId);
        request.idempotencyKey = "employee-photo:" + personaId;
        request.maxRetriesOverride = 1;
        request.retryServerErrors = false;
        request.retryOnlyTimeouts = true;

        IntegrationResult result = executor.execute(
            InterconnectionIds.DHO_EMPLOYEE_PHOTO,
            InterconnectionIds.DHO_EMPLOYEE_PHOTO_INTERFACE,
            request
        );
        if (result.statusCode == 404 || result.statusCode == 204) return null;
        if (!result.successful()) {
            throw new InterconnectionException(
                "DHO_EMPLOYEE_PHOTO_HTTP_" + result.statusCode,
                "DHO rejected the employee photo query"
            );
        }
        if (result.body == null || result.body.isBlank()) return null;

        try {
            JsonNode response = mapper.readTree(result.body);
            JsonNode success = response.get("success");
            if (success == null || !success.isBoolean()) throw invalid("Missing success flag");
            if (!success.booleanValue()) return null;

            JsonNode responsePersonaId = response.get("personaId");
            if (responsePersonaId == null || !responsePersonaId.isIntegralNumber()
                    || responsePersonaId.longValue() != personaId) {
                throw invalid("The response personaId does not match the request");
            }

            String contentType = normalizeContentType(response.path("contentType").asText(""));
            if (!ALLOWED_CONTENT_TYPES.contains(contentType)) throw invalid("Unsupported content type");
            validateExtension(response.path("fileExtension").asText(""), contentType);

            String encoded = response.path("photoBase64").asText("").trim();
            int comma = encoded.indexOf(',');
            if (encoded.regionMatches(true, 0, "data:", 0, 5) && comma >= 0) {
                encoded = encoded.substring(comma + 1).trim();
            }
            if (encoded.isEmpty()) throw invalid("Missing photo content");
            if (encoded.length() > MAX_BASE64_CHARS) throw invalid("Photo exceeds the allowed size");

            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length == 0 || bytes.length > MAX_PHOTO_BYTES) throw invalid("Photo exceeds the allowed size");
            String detectedContentType = ImageSniffer.detect(bytes);
            if (!contentType.equals(detectedContentType)) throw invalid("Photo bytes do not match contentType");
            return new EmployeePhoto(bytes, contentType);
        } catch (InterconnectionException error) {
            throw error;
        } catch (Exception error) {
            throw new InterconnectionException(
                "DHO_EMPLOYEE_PHOTO_INVALID_RESPONSE",
                "DHO returned an invalid employee photo response",
                error
            );
        }
    }

    private static String normalizeContentType(String value) {
        int parameters = value.indexOf(';');
        return (parameters < 0 ? value : value.substring(0, parameters)).trim().toLowerCase(Locale.ROOT);
    }

    private static void validateExtension(String value, String contentType) {
        String extension = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (extension.startsWith(".")) extension = extension.substring(1);
        if (extension.isEmpty()) throw invalid("Missing file extension");
        boolean valid = switch (contentType) {
            case "image/jpeg" -> extension.equals("jpg") || extension.equals("jpeg");
            case "image/png" -> extension.equals("png");
            case "image/webp" -> extension.equals("webp");
            default -> false;
        };
        if (!valid) throw invalid("fileExtension does not match contentType");
    }

    private static InterconnectionException invalid(String reason) {
        return new InterconnectionException("DHO_EMPLOYEE_PHOTO_INVALID_RESPONSE", reason);
    }
}
