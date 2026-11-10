package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.interconnections.CredentialRefResolver;
import com.cajamarca.sgi.comando.interconnections.InterconnectionException;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Callback idempotente de STC para cerrar la novedad asociada al taskCode. */
@Path("/api/v1/inbound/stc/task-status")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class StcTaskStatusResource {
    static final String FINALIZED = "FINALIZED";

    @Inject EntityManager em;
    @Inject CredentialRefResolver credentials;

    @ConfigProperty(name = "sgi.stc.inbound.credential-ref", defaultValue = "STC_SGI_COM_0001_v001")
    Optional<String> credentialRef;

    public record TaskStatusRequest(String taskCode, String status) {}
    public record TaskStatusResponse(String message) {}

    @POST
    @Transactional
    public TaskStatusResponse receive(
        @HeaderParam("Authorization") String authorization,
        TaskStatusRequest request
    ) {
        requireServiceToken(authorization);
        String taskCode = taskCode(request == null ? null : request.taskCode());
        String targetStatus = status(request == null ? null : request.status());

        @SuppressWarnings("unchecked") List<Object[]> matches = em.createNativeQuery("""
            select id,status,instance_country_id
              from operator_operational_report
             where routing_target='STC' and routing_reference_id=:taskCode
             for update
            """).setParameter("taskCode", taskCode).getResultList();
        if (matches.isEmpty()) throw error(404, "TASK_NOT_FOUND");
        if (matches.size() > 1) throw error(409, "DUPLICATE_TASK_CODE");

        Object[] row = matches.get(0);
        UUID reportId = uuid(row[0]);
        String previousStatus = row[1].toString();
        UUID instanceCountryId = uuid(row[2]);
        if (targetStatus.equals(previousStatus)) return new TaskStatusResponse("OK");
        if (!"APPROVED".equals(previousStatus)) throw error(409, "INVALID_REPORT_STATUS");

        int updated = em.createNativeQuery("""
            update operator_operational_report
               set status='FINALIZED',routing_status='FINALIZED',updated_at=current_timestamp
             where id=:report and instance_country_id=:tenant and status='APPROVED'
            """).setParameter("report", reportId).setParameter("tenant", instanceCountryId).executeUpdate();
        if (updated != 1) throw error(409, "CONCURRENT_STATUS_CHANGE");

        em.createNativeQuery("""
            insert into operator_operational_report_decision
              (id,instance_country_id,report_id,action,previous_status,resulting_status,
               actor_username,actor_role,comment,created_at)
            values
              (:id,:tenant,:report,'FINALIZE','APPROVED','FINALIZED','STC','SYSTEM',
               'Tarea finalizada en STC: ' || :taskCode,:at)
            """).setParameter("id", UUID.randomUUID()).setParameter("tenant", instanceCountryId)
            .setParameter("report", reportId).setParameter("taskCode", taskCode)
            .setParameter("at", Instant.now()).executeUpdate();
        return new TaskStatusResponse("OK");
    }

    private void requireServiceToken(String authorization) {
        String ref = credentialRef.orElse("").trim();
        if (ref.isEmpty()) throw error(503, "SERVICE_TOKEN_NOT_CONFIGURED");
        final String configured;
        try { configured = credentials.resolve(ref); }
        catch (InterconnectionException exception) { throw error(503, "SERVICE_TOKEN_NOT_AVAILABLE"); }
        String prefix = "Bearer ";
        String provided = authorization != null && authorization.startsWith(prefix)
            ? authorization.substring(prefix.length()).trim() : "";
        byte[] expected = configured == null ? new byte[0] : configured.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8)))
            throw error(401, "UNAUTHORIZED");
    }

    static String taskCode(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 160) throw error(400, "INVALID_TASK_CODE");
        return normalized;
    }

    static String status(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!FINALIZED.equals(normalized)) throw error(400, "INVALID_STATUS");
        return normalized;
    }

    private static UUID uuid(Object value) {
        return value instanceof UUID id ? id : UUID.fromString(value.toString());
    }

    static WebApplicationException error(int status, String message) {
        return new WebApplicationException(Response.status(status).type(MediaType.APPLICATION_JSON)
            .entity(Map.of("message", message)).build());
    }
}
