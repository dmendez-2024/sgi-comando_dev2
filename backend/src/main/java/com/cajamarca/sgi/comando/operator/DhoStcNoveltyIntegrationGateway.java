package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.interconnections.GenericInterconnectionExecutor;
import com.cajamarca.sgi.comando.interconnections.IntegrationRequest;
import com.cajamarca.sgi.comando.interconnections.IntegrationResult;
import com.cajamarca.sgi.comando.interconnections.InterconnectionException;
import com.cajamarca.sgi.comando.interconnections.InterconnectionIds;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class DhoStcNoveltyIntegrationGateway implements NoveltyIntegrationGateway {
    static final String DHO_EMAIL_INTERFACE = "SGI_COM_SIC_DHO_0002_IF01";
    static final String STC_CREATE_INTERFACE = "SGI_COM_STC_0001_IF01";
    private static final Logger LOG = Logger.getLogger(DhoStcNoveltyIntegrationGateway.class);

    @Inject GenericInterconnectionExecutor executor;
    @Inject ObjectMapper mapper;

    /** ktoken legado de STC. No se guarda en CORE ni en código; llega desde Docker. */
    @ConfigProperty(name = "sgi.stc.ktoken", defaultValue = "")
    Optional<String> stcToken;

    @Override
    public String employeeEmail(UUID instanceCountryId, Long personaId) {
        if (instanceCountryId == null || personaId == null || personaId <= 0) return null;
        try {
            IntegrationRequest request = new IntegrationRequest();
            Map<String,Object> body = new LinkedHashMap<>();
            body.put("instanceCountryId", instanceCountryId.toString());
            body.put("personaId", personaId);
            request.body = body;
            request.idempotencyKey = "employee-email:" + instanceCountryId + ":" + personaId;
            request.maxRetriesOverride = 3;
            request.retryServerErrors = false;
            request.retryOnlyTimeouts = true;
            IntegrationResult result = executor.execute(
                InterconnectionIds.DHO_EMPLOYEE_EMAIL, DHO_EMAIL_INTERFACE, request);
            if (!result.successful() || result.body == null || result.body.isBlank()) return null;
            JsonNode response = mapper.readTree(result.body);
            String email = response.path("correo").asText("").trim();
            return email.isEmpty() ? null : email;
        } catch (Exception error) {
            LOG.warnf("dho_employee_email_unavailable personaId=%s reason=%s",
                personaId, error instanceof InterconnectionException e ? e.code() : error.getClass().getSimpleName());
            return null;
        }
    }

    @Override
    public CreatedTask createStcTask(ObjectNode payload, UUID noveltyId, String approvingUserEmail) {
        String token = stcToken.orElse("").trim();
        if (token.isEmpty()) throw new NoveltyIntegrationException("STC_CONFIGURATION_ERROR", 503,
            "No fue posible aprobar la novedad porque el ktoken de STC no está configurado.");
        if (approvingUserEmail == null || approvingUserEmail.isBlank())
            throw new NoveltyIntegrationException("STC_APPROVER_EMAIL_REQUIRED", 422,
                "No fue posible identificar el correo del usuario que aprueba la novedad.");

        IntegrationRequest request = new IntegrationRequest();
        request.body = payload;
        request.idempotencyKey = noveltyId.toString();
        request.maxRetriesOverride = 1;
        request.headers.put("app", "KTASK");
        request.headers.put("channel", "WEB");
        request.headers.put("user", approvingUserEmail.trim());
        request.headers.put("ktoken", token);

        final IntegrationResult result;
        try {
            result = executor.execute(InterconnectionIds.STC_CREATE_CASE, STC_CREATE_INTERFACE, request);
        } catch (InterconnectionException error) {
            if ("TRANSPORT_TIMEOUT".equals(error.code()))
                throw new NoveltyIntegrationException("STC_RESULT_UNKNOWN", 504,
                    "No se pudo confirmar si STC creó la tarea. La novedad no fue aprobada en SGI Comando. Verifique STC antes de volver a intentarlo.", error);
            throw new NoveltyIntegrationException("STC_UNAVAILABLE", 502,
                "No fue posible comunicarse con STC. La novedad no fue modificada.", error);
        }

        if (!result.successful()) throw httpFailure(result.statusCode);
        try {
            JsonNode response = mapper.readTree(result.body == null ? "" : result.body);
            JsonNode codes = response.path("taskCodeList");
            if (response.path("code").asInt(-1) != 100 || !codes.isArray() || codes.isEmpty()
                    || codes.get(0).asText("").isBlank())
                throw new NoveltyIntegrationException("STC_TASK_NOT_CREATED", 502,
                    "STC no confirmó la creación de la tarea. La novedad no fue modificada.");
            return new CreatedTask(codes.get(0).asText().trim(), result.attempts);
        } catch (NoveltyIntegrationException error) {
            throw error;
        } catch (Exception error) {
            throw new NoveltyIntegrationException("STC_INVALID_RESPONSE", 502,
                "STC devolvió una respuesta inválida. La novedad no fue modificada.", error);
        }
    }

    private static NoveltyIntegrationException httpFailure(int status) {
        if (status == 400 || status == 409 || status == 422)
            return new NoveltyIntegrationException("STC_REJECTED_REQUEST", 502,
                "STC rechazó los datos de la tarea. La novedad no fue modificada.");
        if (status == 401 || status == 403)
            return new NoveltyIntegrationException("STC_AUTHENTICATION_ERROR", 502,
                "STC rechazó las credenciales de integración. La novedad no fue modificada.");
        return new NoveltyIntegrationException("STC_UNAVAILABLE", 502,
            "STC no pudo crear la tarea (HTTP " + status + "). La novedad no fue modificada.");
    }
}
