package com.cajamarca.sgi.comando.operation;

import com.cajamarca.sgi.comando.interconnections.CredentialRefResolver;
import com.cajamarca.sgi.comando.interconnections.InterconnectionException;
import com.cajamarca.sgi.comando.interconnections.InterconnectionIds;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Callback idempotente SIC:DHO -> SGI:Comando para aprobar o rechazar una sancion por empleado. */
@Path("/api/v1/inbound/sic-dho/sanction-decisions")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class DhoSanctionDecisionResource {
    private static final String CONTRACT_VERSION = "v1";

    @Inject CredentialRefResolver credentials;

    @ConfigProperty(name = "sgi.dho.sanction.inbound.credential-ref", defaultValue = "SIC_SGI_COM_0001_v001")
    Optional<String> credentialRef;

    public record DecisionRequest(
        UUID eventId,
        UUID incidentId,
        UUID employeeId,
        Long dhoSanctionId,
        String status,
        String responsible,
        String reason,
        Instant decidedAt,
        String estado,
        String responsable,
        String observacion,
        Instant fechaDecision
    ) {}

    public record DecisionResponse(String message) {}

    @POST
    @Transactional
    public DecisionResponse receive(
        @HeaderParam("Authorization") String authorization,
        @HeaderParam("X-Correlation-Id") String correlationId,
        @HeaderParam("X-Interconnection-Id") String interconnectionId,
        @HeaderParam("X-Contract-Version") String contractVersion,
        @HeaderParam("Idempotency-Key") String idempotencyKey,
        DecisionRequest request
    ) {
        requireServiceToken(authorization);
        validateHeaders(correlationId, interconnectionId, contractVersion, idempotencyKey, request);

        UUID eventId = required(request.eventId(), "eventId");
        UUID incidentId = required(request.incidentId(), "incidentId");
        UUID employeeId = required(request.employeeId(), "employeeId");
        if (request.dhoSanctionId() == null || request.dhoSanctionId() <= 0)
            throw error(400, "INVALID_DHO_SANCTION_ID");

        String decisionStatus = decisionStatus(first(request.status(), request.estado()));
        String decidedBy = requiredText(first(request.responsible(), request.responsable()), 160, "INVALID_RESPONSIBLE");
        String reason = optionalText(first(request.reason(), request.observacion()), 4000, "INVALID_REASON");
        Instant decidedAt = request.decidedAt() != null ? request.decidedAt() : request.fechaDecision();
        if (decidedAt == null) throw error(400, "INVALID_DECIDED_AT");
        String contentHash = contentHash(request, decisionStatus, decidedBy, reason, decidedAt);

        IncidentSanctionEmployeeEntity sanction = IncidentSanctionEmployeeEntity.find(
            "incidentId=?1 and employeeId=?2", incidentId, employeeId)
            .withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
        if (sanction == null) throw error(404, "SANCTION_EMPLOYEE_NOT_FOUND");

        if (eventId.equals(sanction.lastEventId)) {
            if (!contentHash.equals(sanction.lastEventHash)) throw error(409, "EVENT_ID_REUSED");
            return new DecisionResponse("OK");
        }
        if (sanction.dhoSanctionId != null && !sanction.dhoSanctionId.equals(request.dhoSanctionId()))
            throw error(409, "DHO_SANCTION_ID_CONFLICT");
        if (!"PENDING".equals(sanction.decisionStatus)) {
            if (decisionStatus.equals(sanction.decisionStatus)) return new DecisionResponse("OK");
            throw error(409, "SANCTION_ALREADY_DECIDED");
        }

        sanction.dhoSanctionId = request.dhoSanctionId();
        sanction.decisionStatus = decisionStatus;
        sanction.decisionReason = reason;
        sanction.decidedBy = decidedBy;
        sanction.decidedAt = decidedAt;
        sanction.lastEventId = eventId;
        sanction.lastEventHash = contentHash;
        return new DecisionResponse("OK");
    }

    private void validateHeaders(String correlationId, String interconnectionId, String contractVersion,
                                 String idempotencyKey, DecisionRequest request) {
        if (blank(correlationId)) throw error(400, "MISSING_CORRELATION_ID");
        if (!InterconnectionIds.DHO_SANCTION_DECISIONS.equals(interconnectionId))
            throw error(400, "INVALID_INTERCONNECTION_ID");
        if (!CONTRACT_VERSION.equalsIgnoreCase(trim(contractVersion)))
            throw error(400, "INVALID_CONTRACT_VERSION");
        if (blank(idempotencyKey)) throw error(400, "MISSING_IDEMPOTENCY_KEY");
        if (request == null || request.eventId() == null || !idempotencyKey.equals(request.eventId().toString()))
            throw error(400, "IDEMPOTENCY_KEY_MISMATCH");
    }

    private void requireServiceToken(String authorization) {
        String ref = credentialRef.orElse("").trim();
        if (ref.isEmpty()) throw error(503, "SERVICE_TOKEN_NOT_CONFIGURED");
        final String configured;
        try { configured = credentials.resolve(ref); }
        catch (InterconnectionException error) { throw error(503, "SERVICE_TOKEN_NOT_AVAILABLE"); }
        String prefix = "Bearer ";
        String provided = authorization != null && authorization.startsWith(prefix)
            ? authorization.substring(prefix.length()).trim() : "";
        byte[] expected = configured == null ? new byte[0] : configured.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8)))
            throw error(401, "UNAUTHORIZED");
    }

    private static String decisionStatus(String value) {
        return switch (trim(value).toUpperCase(Locale.ROOT)) {
            case "APROBADO", "APPROVED" -> "APPROVED";
            case "RECHAZADO", "REJECTED" -> "REJECTED";
            default -> throw error(400, "INVALID_STATUS");
        };
    }

    private static String contentHash(DecisionRequest request, String status, String responsible,
                                      String reason, Instant decidedAt) {
        String canonical = request.eventId() + "|" + request.incidentId() + "|" + request.employeeId()
            + "|" + request.dhoSanctionId() + "|" + status + "|" + responsible + "|"
            + (reason == null ? "" : reason) + "|" + decidedAt;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo calcular la idempotencia de la decision", error);
        }
    }

    private static <T> T required(T value, String field) {
        if (value == null) throw error(400, "INVALID_" + field.toUpperCase(Locale.ROOT));
        return value;
    }

    private static String requiredText(String value, int max, String code) {
        String normalized = trim(value);
        if (normalized.isEmpty() || normalized.length() > max) throw error(400, code);
        return normalized;
    }

    private static String optionalText(String value, int max, String code) {
        String normalized = trim(value);
        if (normalized.length() > max) throw error(400, code);
        return normalized.isEmpty() ? null : normalized;
    }

    private static String first(String primary, String legacy) {
        return !blank(primary) ? primary : legacy;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static String trim(String value) { return value == null ? "" : value.trim(); }

    private static WebApplicationException error(int status, String message) {
        return new WebApplicationException(Response.status(status).type(MediaType.APPLICATION_JSON)
            .entity(Map.of("message", message)).build());
    }
}
