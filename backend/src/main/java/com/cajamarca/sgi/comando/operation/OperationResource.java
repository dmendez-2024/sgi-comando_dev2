package com.cajamarca.sgi.comando.operation;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.execution.*;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.patrols.*;
import com.cajamarca.sgi.comando.storage.*;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import com.cajamarca.sgi.comando.visint.*;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.time.Instant;
import java.util.*;

/** Vista Operación: Hitos ejecutados en el Punto, sus fotos y el veredicto de VISINT. */
@Path("/api/operation") @Produces(MediaType.APPLICATION_JSON)
public class OperationResource {
    public record ReviewSummary(UUID id, String status, String result, boolean simulated) {}
    public record ExecutionRow(UUID id, Instant executedAt, String postCode, String postName, String protocolCode, int protocolVersion, String patrolCode, String patrolName,
                               String checkpointCode, String checkpointName, String employeeName, int captureNo, List<UUID> evidenceIds, List<String> flags, ReviewSummary review) {}
    public record EvidenceView(UUID id, Instant capturedAt, Double latitude, Double longitude, String source, List<String> flags) {}
    public record ReviewDetail(UUID id, String status, String result, String findings, UUID matchedStandardImageId, String reasonCode, String modelVersion,
                               int standardImageVersion, boolean simulated, int attempts, String lastError,
                               Instant createdAt, Instant requestedAt, Instant reviewedAt) {}
    public record StandardView(UUID id, int position) {}
    public record ExecutionDetail(ExecutionRow row, String observation, Double latitude, Double longitude, String standardNotes, List<EvidenceView> evidences,
                                  List<StandardView> standards, ReviewDetail review) {}

    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject EntityManager em;
    @Inject StorageService storage;
    @Inject StandardImageStore standardImages;

    @GET @Path("/executions")
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
    public List<ExecutionRow> list(@QueryParam("pointId") UUID pointId, @QueryParam("limit") @DefaultValue("50") int limit) {
        if (pointId == null) throw new BadRequestException("pointId es obligatorio");
        requirePoint(pointId);
        return TaskExecution.<TaskExecution>find("instanceCountryId=?1 and pointId=?2 order by executedAt desc", tenant.instanceCountryId(), pointId)
            .page(0, Math.max(1, Math.min(200, limit))).list().stream().map(this::row).toList();
    }

    @GET @Path("/executions/{id}")
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
    public ExecutionDetail detail(@PathParam("id") UUID id) {
        TaskExecution x = execution(id);
        VisualReview r = VisualReview.find("taskExecutionId", x.id).firstResult();
        List<EvidenceView> photos = photos(x.id).stream().map(o -> new EvidenceView(o.id, o.capturedAt, o.latitude, o.longitude, o.source, flags(o.flags))).toList();
        PatrolCheckpoint cp = PatrolCheckpoint.findById(x.targetId);
        List<StandardView> standards = r != null ? VisualReviewStandard.of(r.id).stream().map(s -> new StandardView(s.standardImageId, s.position)).toList()
            : PatrolCheckpointStandardImage.of(x.targetId).stream().map(i -> new StandardView(i.id, i.position)).toList();
        return new ExecutionDetail(row(x), x.observation, x.latitude, x.longitude, cp == null ? null : cp.standardImageNotes, photos, standards, r == null ? null : detail(r));
    }

    @GET @Path("/evidences/{id}/content")
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
    public Response evidence(@PathParam("id") UUID id) {
        EvidenceObject o = EvidenceObject.find("id=?1 and instanceCountryId=?2", id, tenant.instanceCountryId()).firstResult();
        if (o == null) throw new NotFoundException("Foto no encontrada");
        List<?> exec = em.createNativeQuery("select task_execution_id from task_execution_evidence where evidence_id=:e").setParameter("e", id).getResultList();
        if (exec.isEmpty()) throw new NotFoundException("Foto no asociada a una ejecución");
        execution((UUID) exec.get(0));
        return Response.ok(storage.read(o.bucket, o.objectKey)).type(o.contentType).header(HttpHeaders.CACHE_CONTROL, "private, max-age=300").build();
    }

    /** Foto estándar enviada a VISINT (snapshot de la revisión); si el Hito no tenía VISINT, la actual del Hito. */
    @GET @Path("/executions/{id}/standards/{standardId}")
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
    public Response standard(@PathParam("id") UUID id, @PathParam("standardId") UUID standardId) {
        TaskExecution x = execution(id);
        VisualReview r = VisualReview.find("taskExecutionId", x.id).firstResult();
        if (r != null) {
            VisualReviewStandard s = VisualReviewStandard.find("reviewId=?1 and standardImageId=?2", r.id, standardId).firstResult();
            if (s == null) throw new NotFoundException("Foto estándar no encontrada");
            return Response.ok(storage.read(s.bucket, s.objectKey)).type(s.contentType).build();
        }
        PatrolCheckpointStandardImage i = PatrolCheckpointStandardImage.find("id=?1 and checkpointId=?2", standardId, x.targetId).firstResult();
        if (i == null) throw new NotFoundException("Foto estándar no encontrada");
        return Response.ok(standardImages.read(i.objectKey, null)).type(i.contentType).build();
    }

    @POST @Path("/visual-reviews/{id}/retry") @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public ReviewDetail retry(@PathParam("id") UUID id) {
        VisualReview r = VisualReview.find("id=?1 and instanceCountryId=?2", id, tenant.instanceCountryId()).firstResult();
        if (r == null) throw new NotFoundException("Revisión no encontrada");
        execution(r.taskExecutionId);
        if (!Set.of("ERROR_RETRYABLE", "ERROR_FINAL").contains(r.status)) throw new ClientErrorException("Solo se reintentan revisiones con error", 409);
        if (VisualReviewStandard.count("reviewId", r.id) == 0) throw new ClientErrorException("La revisión no tiene fotos estándar", 409);
        r.status = "QUEUED_FOR_VISINT";
        r.attempts = 0;
        r.nextAttemptAt = Instant.now();
        r.lastError = null;
        r.result = null;
        r.matchedStandardImageId = null;
        r.reasonCode = null;
        return detail(r);
    }

    private ExecutionRow row(TaskExecution x) {
        Object[] m = (Object[]) em.createNativeQuery("""
            select po.code, po.name, pp.code, pp.version_no, pd.code, pd.name, pc.code, pc.name, e.full_name
            from task_execution t join post po on po.id=t.post_id join patrol_protocol pp on pp.id=t.protocol_id
            join patrol_checkpoint pc on pc.id=t.target_id join patrol_definition pd on pd.id=pc.patrol_definition_id
            left join employee_operational_snapshot e on e.employee_id=t.employee_id and e.instance_country_id=t.instance_country_id
            where t.id=:id""").setParameter("id", x.id).getSingleResult();
        List<EvidenceObject> photos = photos(x.id);
        Set<String> flags = new TreeSet<>();
        photos.forEach(o -> flags.addAll(flags(o.flags)));
        VisualReview r = VisualReview.find("taskExecutionId", x.id).firstResult();
        return new ExecutionRow(x.id, x.executedAt, (String) m[0], (String) m[1], (String) m[2], ((Number) m[3]).intValue(), (String) m[4], (String) m[5], (String) m[6], (String) m[7],
            m[8] == null ? x.username : (String) m[8], x.captureNo, photos.stream().map(o -> o.id).toList(), List.copyOf(flags),
            r == null ? null : new ReviewSummary(r.id, r.status, r.result, r.simulated));
    }

    private ReviewDetail detail(VisualReview r) {
        return new ReviewDetail(r.id, r.status, r.result, r.findings, r.matchedStandardImageId, r.reasonCode, r.modelVersion, r.standardImageVersion, r.simulated, r.attempts, r.lastError, r.createdAt, r.requestedAt, r.reviewedAt);
    }

    @SuppressWarnings("unchecked")
    private List<EvidenceObject> photos(UUID executionId) {
        List<UUID> ids = em.createNativeQuery("select evidence_id from task_execution_evidence where task_execution_id=:t order by sort_order")
            .setParameter("t", executionId).getResultList();
        return ids.stream().map(eid -> (EvidenceObject) EvidenceObject.findById(eid)).toList();
    }

    private TaskExecution execution(UUID id) {
        TaskExecution x = TaskExecution.find("id=?1 and instanceCountryId=?2", id, tenant.instanceCountryId()).firstResult();
        if (x == null) throw new NotFoundException("Ejecución no encontrada");
        requirePoint(x.pointId);
        return x;
    }

    private void requirePoint(UUID pointId) {
        PointEntity p = PointEntity.find("id=?1 and instanceCountryId=?2", pointId, tenant.instanceCountryId()).firstResult();
        if (p == null) throw new NotFoundException("Punto no encontrado");
        scope.requireCompany(p.companyId);
    }

    private static List<String> flags(String csv) { return csv == null || csv.isBlank() ? List.of() : List.of(csv.split(",")); }
}
