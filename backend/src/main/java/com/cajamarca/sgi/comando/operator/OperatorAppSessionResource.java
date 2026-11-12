package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Inicio y cierre de sesión del agente en la app móvil ({@code operator_app_session}). La app genera {@code sessionId}
 * al ingresar y lo reutiliza al cerrar sesión, así los reintentos desde la cola sin conexión son idempotentes.
 * {@code application} es opcional; por defecto {@value #DEFAULT_APPLICATION}.
 */
@Path("/api/v1/operator/sessions")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OperatorAppSessionResource {
    public static final String DEFAULT_APPLICATION = "SGI_OPERADOR";
    /** Aplicaciones que pueden registrar sesiones; agregar aquí las futuras. */
    static final Set<String> APPLICATIONS = Set.of(DEFAULT_APPLICATION);
    static final Set<String> LOGOUT_REASONS = Set.of("USER", "EXPIRED", "REVOKED");
    /** Una sesión registrada sin conexión puede llegar tarde, pero no con fechas absurdas. */
    static final Duration MAX_DELAY = Duration.ofDays(30);

    @Inject OperatorContext ctx;
    @Inject TenantContext tenant;
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;

    @org.jboss.resteasy.reactive.server.ServerExceptionMapper
    public Response mapError(WebApplicationException e) { return OperatorErrors.withMessage(e); }

    /** Inicio de sesión. */
    @POST @Transactional
    public ObjectNode login(JsonNode body) {
        UUID employee = ctx.employee();
        UUID sessionId = uuid(body, "sessionId");
        String application = application(body);
        Instant loggedInAt = time(body, "loggedInAt");
        UUID assignmentId = optionalUuid(body, "assignmentId");
        if (assignmentId != null) ctx.assignment(assignmentId, employee);
        Object[] existing = find(sessionId);
        if (existing != null) {
            owned(existing, employee);
            if (existing[3] == null) em.createNativeQuery("""
                update operator_app_session set logged_in_at=:at,login_received_at=current_timestamp,
                  assignment_id=coalesce(assignment_id,:assignment),device_id=coalesce(device_id,:device),app_version=coalesce(app_version,:version)
                where id=:id and instance_country_id=:t""")
                .setParameter("at", loggedInAt).setParameter("assignment", assignmentId).setParameter("device", text(body, "deviceId", 160))
                .setParameter("version", text(body, "appVersion", 40)).setParameter("id", sessionId).setParameter("t", tenant.instanceCountryId()).executeUpdate();
            return response(find(sessionId), true);
        }
        em.createNativeQuery("""
            insert into operator_app_session(id,instance_country_id,application,employee_id,username,assignment_id,device_id,app_version,logged_in_at,login_received_at)
            values(:id,:t,:application,:employee,:username,:assignment,:device,:version,:at,current_timestamp)""")
            .setParameter("id", sessionId).setParameter("t", tenant.instanceCountryId()).setParameter("application", application)
            .setParameter("employee", employee).setParameter("username", ctx.username()).setParameter("assignment", assignmentId)
            .setParameter("device", text(body, "deviceId", 160)).setParameter("version", text(body, "appVersion", 40))
            .setParameter("at", loggedInAt).executeUpdate();
        return response(find(sessionId), false);
    }

    /**
     * Cierre de sesión. Si el inicio aún no llegó (cola sin conexión), la fila se crea sin {@code loggedInAt} y se
     * completa cuando llegue.
     */
    @POST @Path("/{sessionId}/logout") @Transactional
    public ObjectNode logout(@PathParam("sessionId") UUID sessionId, JsonNode body) {
        UUID employee = ctx.employee();
        if (sessionId == null) throw new BadRequestException("sessionId es obligatorio");
        Instant loggedOutAt = time(body, "loggedOutAt");
        String reason = Optional.ofNullable(text(body, "reason", 30)).map(v -> v.toUpperCase(Locale.ROOT)).orElse("USER");
        if (!LOGOUT_REASONS.contains(reason)) throw new BadRequestException("Motivo de cierre de sesión inválido");
        Object[] existing = find(sessionId);
        if (existing == null) {
            em.createNativeQuery("""
                insert into operator_app_session(id,instance_country_id,application,employee_id,username,device_id,app_version,logged_out_at,logout_received_at,logout_reason)
                values(:id,:t,:application,:employee,:username,:device,:version,:at,current_timestamp,:reason)""")
                .setParameter("id", sessionId).setParameter("t", tenant.instanceCountryId()).setParameter("application", application(body))
                .setParameter("employee", employee).setParameter("username", ctx.username())
                .setParameter("device", text(body, "deviceId", 160)).setParameter("version", text(body, "appVersion", 40))
                .setParameter("at", loggedOutAt).setParameter("reason", reason).executeUpdate();
            return response(find(sessionId), false);
        }
        owned(existing, employee);
        if (existing[5] != null) return response(existing, true);
        if (existing[3] != null && loggedOutAt.isBefore(instant(existing[3])))
            throw new BadRequestException("El cierre de sesión no puede ser anterior al inicio");
        em.createNativeQuery("""
            update operator_app_session set logged_out_at=:at,logout_received_at=current_timestamp,logout_reason=:reason
            where id=:id and instance_country_id=:t""")
            .setParameter("at", loggedOutAt).setParameter("reason", reason).setParameter("id", sessionId)
            .setParameter("t", tenant.instanceCountryId()).executeUpdate();
        return response(find(sessionId), false);
    }

    private Object[] find(UUID id) {
        List<?> rows = em.createNativeQuery("""
            select id,application,employee_id,logged_in_at,assignment_id,logged_out_at,logout_reason
            from operator_app_session where id=:id and instance_country_id=:t""")
            .setParameter("id", id).setParameter("t", tenant.instanceCountryId()).getResultList();
        return rows.isEmpty() ? null : (Object[]) rows.get(0);
    }

    private static void owned(Object[] row, UUID employee) {
        if (!employee.toString().equals(row[2].toString())) throw new ClientErrorException("El identificador de sesión ya está en uso", 409);
    }

    private ObjectNode response(Object[] row, boolean replay) {
        ObjectNode out = mapper.createObjectNode().put("sessionId", row[0].toString()).put("application", row[1].toString());
        out.put("loggedInAt", row[3] == null ? null : instant(row[3]).toString());
        out.put("assignmentId", row[4] == null ? null : row[4].toString());
        out.put("loggedOutAt", row[5] == null ? null : instant(row[5]).toString());
        out.put("logoutReason", row[6] == null ? null : row[6].toString());
        return out.put("idempotentReplay", replay);
    }

    private static String application(JsonNode body) {
        String value = Optional.ofNullable(text(body, "application", 40)).map(v -> v.toUpperCase(Locale.ROOT)).orElse(DEFAULT_APPLICATION);
        if (!APPLICATIONS.contains(value)) throw new BadRequestException("Aplicación no reconocida");
        return value;
    }

    private static UUID uuid(JsonNode body, String field) {
        UUID value = optionalUuid(body, field);
        if (value == null) throw new BadRequestException(field + " es obligatorio");
        return value;
    }

    private static UUID optionalUuid(JsonNode body, String field) {
        String value = text(body, field, 36);
        if (value == null) return null;
        try { return UUID.fromString(value); } catch (IllegalArgumentException e) { throw new BadRequestException(field + " inválido"); }
    }

    private static Instant time(JsonNode body, String field) {
        String value = text(body, field, 40);
        if (value == null) throw new BadRequestException(field + " es obligatorio");
        Instant at;
        try { at = Instant.parse(value); } catch (DateTimeParseException e) { throw new BadRequestException(field + " inválido"); }
        Instant now = Instant.now();
        if (at.isAfter(now.plusSeconds(300)) || at.isBefore(now.minus(MAX_DELAY))) throw new BadRequestException(field + " fuera de rango");
        return at;
    }

    private static String text(JsonNode body, String field, int max) {
        if (body == null) return null;
        String value = body.path(field).asText("").trim();
        if (value.isEmpty()) return null;
        if (value.length() > max) throw new BadRequestException(field + " supera la longitud permitida");
        return value;
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant i) return i;
        if (value instanceof java.time.OffsetDateTime t) return t.toInstant();
        if (value instanceof java.sql.Timestamp t) return t.toInstant();
        return Instant.parse(value.toString());
    }
}
