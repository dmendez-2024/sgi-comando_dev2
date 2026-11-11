package com.cajamarca.sgi.comando.operation;

import com.cajamarca.sgi.comando.assignments.EmployeeOperationalSnapshot;
import com.cajamarca.sgi.comando.interconnections.IntegrationResult;
import com.cajamarca.sgi.comando.interconnections.InterconnectionException;
import com.cajamarca.sgi.comando.outbox.OutboxEvent;
import com.cajamarca.sgi.comando.security.IdentIdentity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import io.quarkus.security.identity.SecurityIdentity;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Crea exactamente una salida durable por sanción, sin enviar borradores ni evidencias. */
@ApplicationScoped
public class IncidentSanctionDeliveryService {
    static final String AGGREGATE_TYPE = "INCIDENT_NOTIFICATION";
    static final String EVENT_TYPE = "INCIDENT_SANCTION_REQUESTED";
    private static final Logger LOG = Logger.getLogger(IncidentSanctionDeliveryService.class);

    @Inject ObjectMapper mapper;
    @Inject SecurityIdentity identity;
    @Inject DhoSanctionAdapter dho;

    /** Finaliza la entrega dentro de la misma transacción del incidente; un rechazo de DHO provoca rollback local. */
    public void deliverIfRequired(IncidentNotificationEntity incident, JsonNode body) {
        boolean required = "FINALIZED".equals(incident.status) && body.path("sanction").asBoolean(false);
        if (!required) {
            if (incident.sanctionEventId == null) incident.sanctionDeliveryStatus = "NOT_REQUIRED";
            return;
        }
        if (incident.sanctionEventId != null) return;

        Long emitterPersonaId = IdentIdentity.personaId(identity);
        if (emitterPersonaId == null || emitterPersonaId <= 0)
            throw new BadRequestException("El usuario autenticado no tiene persona_id; no se puede enviar la sanción a DHO.");

        List<UUID> selected = collaboratorIds(body.path("collaboratorIds"));
        List<EmployeeOperationalSnapshot> snapshots = EmployeeOperationalSnapshot.list(
            "instanceCountryId=?1 and employeeId in ?2", incident.instanceCountryId, selected);
        Map<UUID, EmployeeOperationalSnapshot> byId = snapshots.stream()
            .collect(Collectors.toMap(employee -> employee.employeeId, Function.identity(), (left, right) -> left));

        List<IncidentSanctionPayload.Employee> employees = new ArrayList<>(selected.size());
        for (UUID employeeId : selected) {
            EmployeeOperationalSnapshot employee = byId.get(employeeId);
            if (employee == null)
                throw new BadRequestException("El colaborador " + employeeId + " ya no existe en el catálogo de empleados.");
            if (employee.personaId == null || employee.personaId <= 0)
                throw new BadRequestException("El colaborador " + employee.fullName + " no tiene persona_id; no se puede enviar la sanción a DHO.");
            employees.add(new IncidentSanctionPayload.Employee(
                employee.employeeId,
                employee.personaId,
                text(body, "point"),
                text(body, "client"),
                text(body, "company")
            ));
        }

        // Estas filas se confirman junto con el incidente solo si DHO acepta la solicitud.
        // Cualquier error remoto provoca rollback de toda la transaccion.
        for (IncidentSanctionPayload.Employee employee : employees) {
            IncidentSanctionEmployeeEntity sanction = new IncidentSanctionEmployeeEntity();
            sanction.id = UUID.randomUUID();
            sanction.instanceCountryId = incident.instanceCountryId;
            sanction.incidentId = incident.id;
            sanction.employeeId = employee.employeeId();
            sanction.personaId = employee.personaId();
            sanction.decisionStatus = "PENDING";
            sanction.persist();
        }

        Instant queuedAt = Instant.now();
        OutboxEvent event = OutboxEvent.of(
            incident.instanceCountryId, AGGREGATE_TYPE, incident.id, EVENT_TYPE, "{}");
        event.id = stableEventId(body, incident.id);
        IncidentSanctionPayload payload = new IncidentSanctionPayload(
            event.id,
            incident.id,
            incident.instanceCountryId,
            emitterPersonaId,
            incident.createdAt == null ? queuedAt : incident.createdAt,
            text(body, "sanctionDescription"),
            queuedAt,
            List.copyOf(employees)
        );
        try { event.payloadJson = mapper.writeValueAsString(payload); }
        catch (JsonProcessingException error) { throw new IllegalStateException("No se pudo construir la sanción para DHO", error); }
        event.persist();

        incident.sanctionEventId = event.id;
        incident.sanctionDeliveryStatus = "PENDING";
        incident.sanctionDeliveryAttempts = 0;
        incident.sanctionNextAttemptAt = queuedAt;
        incident.sanctionSentAt = null;
        incident.sanctionLastHttpStatus = null;
        incident.sanctionLastError = null;

        IntegrationResult result;
        try {
            result = dho.send(mapper.valueToTree(payload));
        } catch (RuntimeException error) {
            String code = error instanceof InterconnectionException integration
                ? integration.code() : "DHO_COMMUNICATION_FAILURE";
            throw failure(503, code,
                "No se pudo conectar con Recursos Humanos. El incidente no fue finalizado. Intente nuevamente mas tarde.");
        }
        WebApplicationException rejection = rejection(result, mapper);
        if (rejection != null) throw rejection;

        Instant sentAt = Instant.now();
        event.publishedAt = sentAt;
        incident.sanctionDeliveryStatus = "SENT";
        incident.sanctionDeliveryAttempts = Math.max(1, result.attempts);
        incident.sanctionNextAttemptAt = null;
        incident.sanctionSentAt = sentAt;
        incident.sanctionLastHttpStatus = result.statusCode;
        incident.sanctionLastError = null;
        LOG.infof("incident_sanction_sent incident=%s event=%s employees=%d http=%d attempts=%d",
            incident.id, event.id, employees.size(), result.statusCode, incident.sanctionDeliveryAttempts);
    }

    /** Una salida ya creada es inmutable para que los reintentos conserven el mismo eventId y contenido. */
    public void requireQueuedSanctionUnchanged(IncidentNotificationEntity incident, JsonNode previous, JsonNode incoming) {
        if (incident.sanctionEventId == null) return;
        boolean changed = !"FINALIZED".equals(text(incoming, "status"))
            || previous.path("sanction").asBoolean(false) != incoming.path("sanction").asBoolean(false)
            || !Objects.equals(text(previous, "sanctionDescription"), text(incoming, "sanctionDescription"))
            || !Objects.equals(text(previous, "sanctionRequestId"), text(incoming, "sanctionRequestId"))
            || !Objects.equals(text(previous, "pointId"), text(incoming, "pointId"))
            || !new LinkedHashSet<>(collaboratorTexts(previous.path("collaboratorIds")))
                .equals(new LinkedHashSet<>(collaboratorTexts(incoming.path("collaboratorIds"))));
        if (changed) throw new BadRequestException(
            "La sanción ya fue preparada para DHO; no se pueden cambiar su decisión, descripción, Punto ni colaboradores.");
    }

    static List<UUID> collaboratorIds(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) throw new BadRequestException("Seleccione uno o más colaboradores involucrados.");
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        for (JsonNode value : node) {
            try {
                UUID id = UUID.fromString(value.asText());
                if (!ids.add(id)) throw new BadRequestException("Hay colaboradores duplicados en la sanción.");
            } catch (IllegalArgumentException error) {
                throw new BadRequestException("El identificador de un colaborador es inválido.");
            }
        }
        return List.copyOf(ids);
    }

    static WebApplicationException rejection(IntegrationResult result, ObjectMapper mapper) {
        if (result.statusCode >= 200 && result.statusCode < 300) {
            JsonNode response = parse(result.body, mapper);
            if (response == null || !response.has("success") || response.path("success").asBoolean()) return null;
            return failure(422, "DHO_REJECTED",
                withRemoteMessage("Recursos Humanos rechazó la sanción.", response.path("message").asText("")));
        }
        JsonNode response = parse(result.body, mapper);
        String remote = response == null ? "" : response.path("message").asText("");
        return switch (result.statusCode) {
            case 400, 422 -> failure(422, "DHO_VALIDATION_ERROR",
                withRemoteMessage("Recursos Humanos rechazó los datos de la sanción.", remote));
            case 401, 403 -> failure(502, "DHO_AUTHENTICATION_ERROR",
                "No se pudo autenticar la comunicación con Recursos Humanos. Comuníquese con soporte.");
            case 404 -> failure(502, "DHO_ENDPOINT_NOT_FOUND",
                "El servicio de sanciones de Recursos Humanos no está disponible o no está configurado.");
            case 409 -> failure(409, "DHO_IDEMPOTENCY_CONFLICT",
                "Recursos Humanos reportó un conflicto al registrar la sanción. Vuelva a intentarlo o comuníquese con soporte.");
            case 408, 429 -> failure(503, "DHO_TEMPORARILY_UNAVAILABLE",
                "Recursos Humanos no pudo procesar la sancion en este momento. El incidente no fue finalizado. Intente nuevamente mas tarde.");
            default -> result.statusCode >= 500
                ? failure(503, "DHO_PROCESSING_ERROR",
                    "No se pudo conectar con Recursos Humanos. El incidente no fue finalizado. Intente nuevamente mas tarde.")
                : failure(502, "DHO_UNEXPECTED_RESPONSE",
                    "Recursos Humanos devolvió una respuesta inesperada al registrar la sanción.");
        };
    }

    private static JsonNode parse(String body, ObjectMapper mapper) {
        if (body == null || body.isBlank()) return null;
        try { return mapper.readTree(body); }
        catch (Exception ignored) { return null; }
    }

    private static String withRemoteMessage(String prefix, String remote) {
        String clean = remote == null ? "" : remote.replaceAll("\\p{Cntrl}", " ").trim();
        if (clean.isEmpty()) return prefix;
        if (clean.length() > 300) clean = clean.substring(0, 300);
        return prefix + " " + clean;
    }

    private static WebApplicationException failure(int status, String code, String message) {
        return new WebApplicationException(Response.status(status).type(MediaType.APPLICATION_JSON)
            .entity(Map.of("code", code, "message", message)).build());
    }

    private static List<String> collaboratorTexts(JsonNode node) {
        if (!node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asText()));
        return values;
    }

    private static UUID stableEventId(JsonNode body, UUID incidentId) {
        String requestId = text(body, "sanctionRequestId");
        if (!requestId.isEmpty()) {
            try { return UUID.fromString(requestId); }
            catch (IllegalArgumentException error) { throw new BadRequestException("sanctionRequestId invalido."); }
        }
        return UUID.nameUUIDFromBytes((EVENT_TYPE + ":" + incidentId).getBytes(StandardCharsets.UTF_8));
    }

    private static String text(JsonNode body, String field) { return body.path(field).asText("").trim(); }
}
