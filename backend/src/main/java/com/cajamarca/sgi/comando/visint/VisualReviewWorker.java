package com.cajamarca.sgi.comando.visint;

import com.cajamarca.sgi.comando.execution.*;
import com.cajamarca.sgi.comando.operations.PointEntity;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import java.time.*;
import java.util.*;

/**
 * Envía a VISINT las revisiones pendientes: una llamada síncrona por revisión (foto del agente contra las fotos estándar enviadas).
 * PASS → PASSED, FAIL → FAILED, ERROR → ERROR_FINAL. Si VISINT no responde se reintenta con espera creciente.
 */
@ApplicationScoped
public class VisualReviewWorker {
    private static final Logger LOG = Logger.getLogger(VisualReviewWorker.class);
    @Inject EntityManager em;
    @Inject VisintClient visint;
    @ConfigProperty(name="sgi.visint.worker-enabled") boolean enabled;
    @ConfigProperty(name="sgi.visint.max-attempts") int maxAttempts;
    @ConfigProperty(name="sgi.visint.match-threshold", defaultValue="0.8") double defaultThreshold;

    @Scheduled(every="2s", delayed="10s", concurrentExecution=Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        if (!enabled) return;
        try { processDue(); } catch (RuntimeException e) { LOG.warn("Revisión VISINT pendiente: " + e.getMessage()); }
    }

    @SuppressWarnings("unchecked")
    public int processDue() {
        List<UUID> due = QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
            "select id from visual_review where status in ('QUEUED_FOR_VISINT','ERROR_RETRYABLE') and next_attempt_at<=now() order by next_attempt_at limit 20")
            .getResultList());
        int done = 0;
        for (UUID id : due) if (QuarkusTransaction.requiringNew().call(() -> processOne(id))) done++;
        return done;
    }

    @SuppressWarnings("unchecked")
    boolean processOne(UUID id) {
        if (em.createNativeQuery("select id from visual_review where id=:id and status in ('QUEUED_FOR_VISINT','ERROR_RETRYABLE') for update skip locked")
            .setParameter("id", id).getResultList().isEmpty()) return false;
        VisualReview r = VisualReview.findById(id);
        TaskExecution x = TaskExecution.findById(r.taskExecutionId);
        List<UUID> photoIds = em.createNativeQuery("select evidence_id from task_execution_evidence where task_execution_id=:t order by sort_order")
            .setParameter("t", x.id).setMaxResults(1).getResultList();
        List<VisintPort.StandardRef> standards = VisualReviewStandard.of(r.id).stream()
            .map(s -> new VisintPort.StandardRef(s.standardImageId, s.position, s.bucket, s.objectKey, s.sha256, s.contentType)).toList();
        if (photoIds.isEmpty() || standards.isEmpty()) {
            r.status = "ERROR_FINAL"; r.nextAttemptAt = null;
            r.lastError = photoIds.isEmpty() ? "La ejecución no tiene foto del agente" : "La revisión no tiene fotos estándar";
            return true;
        }
        EvidenceObject o = EvidenceObject.findById(photoIds.get(0));
        VisintPort.EvidenceRef photo = new VisintPort.EvidenceRef(o.id, o.bucket, o.objectKey, o.sha256, o.contentType, o.capturedAt, o.latitude, o.longitude);
        PointEntity point = PointEntity.findById(x.pointId);
        r.attempts++;
        r.requestedAt = Instant.now();
        try {
            VisintPort.ReviewResult res = visint.review(new VisintPort.ReviewRequest(r.id, r.attempts, x.id, x.targetType, x.employeeId,
                point == null ? null : point.companyId, x.pointId, x.postId, serviceId(x), x.targetId, photo, standards, r.correlationId, r.matchThreshold != null ? r.matchThreshold : defaultThreshold));
            r.visintExternalId = cut(res.externalId(), 120);
            r.result = res.result();
            r.findings = cut(res.findings(), 1000);
            r.matchedStandardImageId = res.matchedStandardImageId() != null && standards.stream().anyMatch(s -> s.imageId().equals(res.matchedStandardImageId())) ? res.matchedStandardImageId() : null;
            r.reasonCode = cut(res.reasonCode(), 60);
            r.modelVersion = cut(res.modelVersion(), 120);
            r.qualityValid = res.qualityValid(); r.qualityScore = res.qualityScore();
            r.matchCompatible = res.matchCompatible(); r.matchScore = res.matchScore();
            r.status = switch (res.result()) { case "PASS" -> "PASSED"; case "FAIL" -> "FAILED"; default -> "ERROR_FINAL"; };
            r.reviewedAt = Instant.now();
            r.nextAttemptAt = null;
            r.lastError = null;
        } catch (VisintUnavailableException e) {
            r.lastError = cut(e.getMessage() == null ? "VISINT no disponible" : e.getMessage(), 500);
            if (r.attempts >= maxAttempts) { r.status = "ERROR_FINAL"; r.nextAttemptAt = null; }
            else { r.status = "ERROR_RETRYABLE"; r.nextAttemptAt = Instant.now().plus(backoff(r.attempts)); }
        }
        return true;
    }

    /** Servicio que se informa a VISINT: la ronda (Patrullas), la consigna (Consignas), el registro del visitante (Bitácora) o el relevo. */
    static UUID serviceId(TaskExecution x) {
        if ("CONSIGNMENT_EVIDENCE".equals(x.targetType)) {
            com.cajamarca.sgi.comando.consignments.ConsignmentEvidence e = com.cajamarca.sgi.comando.consignments.ConsignmentEvidence.findById(x.targetId);
            return e == null ? x.groupId : e.consignmentId;
        }
        return x.patrolExecutionId != null ? x.patrolExecutionId : x.groupId;
    }

    /** 30 s, 60 s, 120 s, 240 s… con tope de 10 minutos. */
    static Duration backoff(int attempt) { return Duration.ofSeconds(Math.min(600, 30L << Math.max(0, attempt - 1))); }
    private static String cut(String s, int max) { return s == null ? null : s.substring(0, Math.min(max, s.length())); }
}
