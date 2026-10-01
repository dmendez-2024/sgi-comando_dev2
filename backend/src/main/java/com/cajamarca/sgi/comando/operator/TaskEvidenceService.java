package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.execution.*;
import com.cajamarca.sgi.comando.storage.StandardReferenceImage;
import com.cajamarca.sgi.comando.visint.VisualReviewService;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.ReliefContract.*;

/**
 * Fase 4 · TASK_EVIDENCE_SUBMITTED: foto del agente para una evidencia de Consigna (una por turno) o un campo de Bitácora
 * (una por registro de visitante, groupId = entryId). Si VISINT valida el destino, se encola la revisión; si "no cumple",
 * el agente puede enviar una nueva captura en el mismo turno/registro.
 */
@ApplicationScoped
public class TaskEvidenceService {
    public static final String TYPE = "TASK_EVIDENCE_SUBMITTED";

    @Inject OperatorContext ctx;
    @Inject OperatorTasks tasks;
    @Inject TenantContext tenant;
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;
    @Inject VisualReviewService reviews;

    @Transactional
    public ObjectNode submit(JsonNode batch) {
        UUID employee = ctx.employee();
        UUID t = tenant.instanceCountryId();
        JsonNode e = validate(batch, employee, t, Instant.now());
        UUID eventId = uuid(e, "eventId"), assignmentId = uuid(e, "assignmentId"), targetId = uuid(e, "targetId");
        String targetType = text(e, "targetType");
        OperatorContext.Assignment a = ctx.assignment(assignmentId, employee);
        ctx.lock(assignmentId);

        String digest = OperatorResource.hash(e.toString().getBytes(StandardCharsets.UTF_8));
        TaskExecution previous = TaskExecution.find("id=?1 and instanceCountryId=?2", eventId, t).firstResult();
        if (previous != null) {
            if (!previous.payloadHash.equals(digest) || !previous.username.equals(ctx.username())) throw new ClientErrorException("Identificador reutilizado con datos diferentes", 409);
            return ack(eventId, countEvidence(eventId), reviews.statusFor(eventId));
        }

        Instant executed = time(e, "executedAt");
        if (executed.isBefore(a.shift().startsAt.minusSeconds(43200)) || executed.isAfter(a.shift().endsAt.plusSeconds(43200))) throw new BadRequestException("Fecha fuera de la ventana del turno");
        OperatorTasks.Target target = tasks.require(targetType, targetId, a.post());
        UUID group = switch (targetType) {
            case StandardReferenceImage.CONSIGNMENT_EVIDENCE -> assignmentId;
            case StandardReferenceImage.LOGBOOK_FIELD -> {
                if (!e.hasNonNull("groupId")) throw new BadRequestException("groupId (id del registro del visitante) es obligatorio en Bitácora");
                yield uuid(e, "groupId");
            }
            default -> throw new BadRequestException("Use PATROL_CHECKPOINT_COMPLETED para los Hitos de patrulla");
        };

        List<UUID> evidenceIds = new ArrayList<>();
        e.path("evidenceIds").forEach(x -> evidenceIds.add(UUID.fromString(x.asText())));
        if (evidenceIds.size() != 1) throw new BadRequestException("Se requiere 1 foto; se enviaron " + evidenceIds.size());
        EvidenceObject ev = EvidenceObject.find("id=?1 and instanceCountryId=?2", evidenceIds.get(0), t).firstResult();
        if (ev == null || !ev.eventId.equals(eventId) || !ev.assignmentId.equals(assignmentId) || !ev.username.equals(ctx.username())
            || !ev.targetId.equals(targetId) || !"STORED".equals(ev.status)) throw new BadRequestException("Foto no autorizada o no cargada: " + evidenceIds.get(0));

        int captureNo = nextCapture(t, group, targetId, targetType);
        TaskExecution x = new TaskExecution();
        x.id = eventId; x.instanceCountryId = t; x.executionType = TYPE; x.assignmentId = assignmentId;
        x.shiftOccurrenceId = a.shift().id; x.pointId = a.point().id; x.postId = a.post().id; x.employeeId = employee; x.username = ctx.username();
        x.targetType = targetType; x.targetId = targetId; x.protocolId = target.protocolId(); x.protocolVersionNo = target.protocolVersion();
        x.groupId = group; x.captureNo = captureNo; x.executedAt = executed; x.receivedAt = Instant.now();
        x.latitude = e.hasNonNull("latitude") ? e.path("latitude").asDouble() : null;
        x.longitude = e.hasNonNull("longitude") ? e.path("longitude").asDouble() : null;
        x.accuracyM = e.hasNonNull("accuracyM") ? e.path("accuracyM").asDouble() : null;
        x.observation = e.hasNonNull("observation") ? e.path("observation").asText() : null;
        x.batchId = uuid(batch, "batchId"); x.correlationId = uuid(batch, "correlationId"); x.deviceId = text(batch, "deviceId");
        x.payloadHash = digest; x.payloadJson = e.toString(); x.status = "RECEIVED";
        x.persistAndFlush();
        em.createNativeQuery("insert into task_execution_evidence(task_execution_id,evidence_id,sort_order) values(:t,:e,1)")
            .setParameter("t", eventId).setParameter("e", ev.id).executeUpdate();
        ev.status = "ATTACHED";
        String validation = target.visintEnabled() ? reviews.enqueue(x, targetType, targetId, target.standardImageVersion()).status : "NOT_REQUESTED";
        return ack(eventId, 1, validation);
    }

    /** Número de la nueva captura en el turno (Consigna) o registro (Bitácora): la siguiente solo si la última "no cumple". */
    private int nextCapture(UUID t, UUID group, UUID targetId, String targetType) {
        TaskExecution last = TaskExecution.find("instanceCountryId=?1 and groupId=?2 and targetId=?3 and patrolExecutionId is null order by captureNo desc", t, group, targetId).firstResult();
        if (last == null) return 1;
        String review = reviews.statusFor(last.id);
        if ("FAILED".equals(review)) return last.captureNo + 1;
        if ("QUEUED_FOR_VISINT".equals(review) || "ERROR_RETRYABLE".equals(review)) throw new ClientErrorException("La foto anterior aún se está validando", 409);
        throw new ClientErrorException(StandardReferenceImage.CONSIGNMENT_EVIDENCE.equals(targetType)
            ? "Esta evidencia ya fue registrada en el turno" : "Este campo ya fue registrado para este visitante", 409);
    }

    static JsonNode validate(JsonNode batch, UUID employee, UUID country, Instant now) {
        uuid(batch, "batchId"); uuid(batch, "correlationId");
        if (!employee.equals(uuid(batch, "employeeId")) || !country.equals(uuid(batch, "instanceCountryId"))) throw new ForbiddenException("Identidad o instancia incorrecta");
        if (text(batch, "deviceId").length() > 120) throw new BadRequestException("deviceId demasiado largo");
        Instant captured = time(batch, "capturedAt");
        JsonNode events = batch.path("events");
        if (!events.isArray() || events.size() != 1) throw new BadRequestException("Se admite un evento por lote");
        JsonNode e = events.get(0);
        if (!TYPE.equals(text(e, "type"))) throw new BadRequestException("Tipo de evento no soportado");
        for (String k : new String[]{"eventId", "assignmentId", "targetId"}) uuid(e, k);
        text(e, "targetType");
        Instant executed = time(e, "executedAt");
        if (executed.isAfter(now.plusSeconds(300)) || executed.isAfter(captured.plusSeconds(300))) throw new BadRequestException("Fecha de ejecución futura");
        if (e.hasNonNull("observation") && e.path("observation").asText().length() > 1000) throw new BadRequestException("Observación demasiado larga");
        JsonNode ids = e.path("evidenceIds");
        if (!ids.isArray()) throw new BadRequestException("evidenceIds debe ser una lista");
        for (JsonNode id : ids) { try { UUID.fromString(id.asText()); } catch (IllegalArgumentException x) { throw new BadRequestException("evidenceId inválido"); } }
        return e;
    }

    private int countEvidence(UUID eventId) {
        return ((Number) em.createNativeQuery("select count(*) from task_execution_evidence where task_execution_id=:e").setParameter("e", eventId).getSingleResult()).intValue();
    }

    private ObjectNode ack(UUID id, int evidenceCount, String validationStatus) {
        ObjectNode n = mapper.createObjectNode().put("serverVersion", "operator-v1");
        n.putArray("acknowledgedEventIds").add(id.toString());
        n.putArray("rejectedEvents");
        n.putArray("results").addObject().put("eventId", id.toString()).put("status", "RECEIVED").put("evidenceCount", evidenceCount).put("validationStatus", validationStatus);
        return n;
    }
}
