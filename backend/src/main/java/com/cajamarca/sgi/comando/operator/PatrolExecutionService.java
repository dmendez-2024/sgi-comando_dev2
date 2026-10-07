package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.execution.*;
import com.cajamarca.sgi.comando.patrols.PatrolCheckpoint;
import com.cajamarca.sgi.comando.storage.StandardReferenceImage;
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
 * Registra un Hito de patrulla ejecutado por el agente y le asocia las fotos ya subidas por /evidences.
 * Idempotente por eventId. Un Hito se registra una vez por ronda (patrolRunId), salvo que VISINT diga "no cumple":
 * entonces el agente puede enviar una nueva captura (captureNo 2, 3…).
 */
@ApplicationScoped
public class PatrolExecutionService {
    @Inject OperatorContext ctx;
    @Inject OperatorPatrols patrols;
    @Inject TenantContext tenant;
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;
    @Inject com.cajamarca.sgi.comando.visint.VisualReviewService reviews;

    @Transactional
    public ObjectNode submit(JsonNode batch) {
        UUID employee = ctx.employee();
        UUID t = tenant.instanceCountryId();
        PatrolExecutionContract.validate(batch, employee, t, Instant.now());
        JsonNode e = batch.path("events").get(0);
        UUID eventId = uuid(e, "eventId"), assignmentId = uuid(e, "assignmentId"), runId = uuid(e, "patrolRunId"), checkpointId = uuid(e, "checkpointId");
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
        OperatorPatrols.Applicable target = patrols.requireCheckpoint(checkpointId, a.post().id);
        if (!target.patrol().id.equals(uuid(e, "patrolId"))) throw new BadRequestException("El Hito no pertenece a la patrulla indicada");
        PatrolCheckpoint cp = target.checkpoint();

        List<UUID> evidenceIds = new ArrayList<>();
        e.path("evidenceIds").forEach(x -> evidenceIds.add(UUID.fromString(x.asText())));
        if (evidenceIds.size() > 1 || (cp.requiresEvidence && evidenceIds.isEmpty()))
            throw new BadRequestException((cp.requiresEvidence ? "Este Hito requiere 1 foto" : "Este Hito admite como máximo 1 foto") + "; se enviaron " + evidenceIds.size());
        List<EvidenceObject> evidences = new ArrayList<>();
        for (UUID id : evidenceIds) {
            EvidenceObject ev = EvidenceObject.find("id=?1 and instanceCountryId=?2", id, t).firstResult();
            if (ev == null || !ev.eventId.equals(eventId) || !ev.assignmentId.equals(assignmentId) || !ev.username.equals(ctx.username())
                || !ev.targetId.equals(checkpointId) || !"STORED".equals(ev.status)) throw new BadRequestException("Foto no autorizada o no cargada: " + id);
            evidences.add(ev);
        }

        upsertPatrolRun(runId, target, a, employee, executed);
        int captureNo = nextCapture(t, runId, checkpointId);

        TaskExecution x = new TaskExecution();
        x.id = eventId; x.instanceCountryId = t; x.executionType = PatrolExecutionContract.TYPE; x.assignmentId = assignmentId;
        x.shiftOccurrenceId = a.shift().id; x.pointId = a.point().id; x.postId = a.post().id; x.employeeId = employee; x.username = ctx.username();
        x.targetType = "PATROL_CHECKPOINT"; x.targetId = checkpointId; x.protocolId = target.protocol().id; x.protocolVersionNo = target.protocol().versionNo;
        x.patrolExecutionId = runId; x.captureNo = captureNo; x.executedAt = executed; x.receivedAt = Instant.now();
        x.latitude = e.hasNonNull("latitude") ? e.path("latitude").asDouble() : null;
        x.longitude = e.hasNonNull("longitude") ? e.path("longitude").asDouble() : null;
        x.accuracyM = e.hasNonNull("accuracyM") ? e.path("accuracyM").asDouble() : null;
        x.observation = e.hasNonNull("observation") ? e.path("observation").asText() : null;
        x.batchId = uuid(batch, "batchId"); x.correlationId = uuid(batch, "correlationId"); x.deviceId = text(batch, "deviceId");
        x.payloadHash = digest; x.payloadJson = e.toString(); x.status = "RECEIVED";
        x.persistAndFlush();
        for (int i = 0; i < evidences.size(); i++) {
            em.createNativeQuery("insert into task_execution_evidence(task_execution_id,evidence_id,sort_order) values(:t,:e,:o)")
                .setParameter("t", eventId).setParameter("e", evidences.get(i).id).setParameter("o", i + 1).executeUpdate();
            evidences.get(i).status = "ATTACHED";
        }
        String validation = cp.visintEnabled ? reviews.enqueue(x, StandardReferenceImage.PATROL_CHECKPOINT, cp.id, cp.standardImageVersion).status : "NOT_REQUESTED";
        return ack(eventId, evidences.size(), validation);
    }

    private int countEvidence(UUID eventId) {
        return ((Number) em.createNativeQuery("select count(*) from task_execution_evidence where task_execution_id=:e").setParameter("e", eventId).getSingleResult()).intValue();
    }

    private void upsertPatrolRun(UUID runId, OperatorPatrols.Applicable target, OperatorContext.Assignment a, UUID employee, Instant executed) {
        // Una ronda iniciada con START (POST /patrol-executions) guarda la patrulla en su patrol_plan, no en patrol_definition_id.
        List<?> rows = em.createNativeQuery("""
            select coalesce(pe.patrol_definition_id, pp.patrol_definition_id), pe.assignment_id from patrol_execution pe
            left join patrol_plan pp on pp.id=pe.patrol_plan_id and pp.instance_country_id=pe.instance_country_id
            where pe.id=:id and pe.instance_country_id=:t""")
            .setParameter("id", runId).setParameter("t", tenant.instanceCountryId()).getResultList();
        if (rows.isEmpty()) {
            em.createNativeQuery("""
                insert into patrol_execution(id,instance_country_id,patrol_plan_id,occurrence_id,employee_id,started_at,created_at,updated_at,patrol_definition_id,assignment_id,username)
                values(:id,:t,null,null,:emp,:start,now(),now(),:def,:a,:u)""")
                .setParameter("id", runId).setParameter("t", tenant.instanceCountryId()).setParameter("emp", employee).setParameter("start", executed)
                .setParameter("def", target.patrol().id).setParameter("a", a.assignment().id).setParameter("u", ctx.username()).executeUpdate();
            return;
        }
        Object[] r = (Object[]) rows.get(0);
        if (!target.patrol().id.equals(r[0]) || !a.assignment().id.equals(r[1])) throw new ClientErrorException("La ronda indicada pertenece a otra patrulla o asignación", 409);
    }

    /**
     * Número de la nueva captura del Hito en la ronda: 1 si es la primera. VISINT nunca bloquea al agente: mientras la última está
     * en cola, con falla técnica o "no cumple", puede enviar otra captura. Solo un Hito ya validado (o sin VISINT) se considera registrado.
     */
    private int nextCapture(UUID t, UUID runId, UUID checkpointId) {
        TaskExecution last = TaskExecution.find("instanceCountryId=?1 and patrolExecutionId=?2 and targetId=?3 order by captureNo desc", t, runId, checkpointId).firstResult();
        if (last == null) return 1;
        String review = reviews.statusFor(last.id);
        if (!"PASSED".equals(review) && !"NOT_REQUESTED".equals(review)) return last.captureNo + 1;
        throw new ClientErrorException("Este Hito ya fue registrado en la ronda", 409);
    }

    private ObjectNode ack(UUID id, int evidenceCount, String validationStatus) {
        ObjectNode n = mapper.createObjectNode().put("serverVersion", "operator-v1");
        n.putArray("acknowledgedEventIds").add(id.toString());
        n.putArray("rejectedEvents");
        n.putArray("results").addObject().put("eventId", id.toString()).put("status", "RECEIVED").put("evidenceCount", evidenceCount).put("validationStatus", validationStatus);
        return n;
    }
}
