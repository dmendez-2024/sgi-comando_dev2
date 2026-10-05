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

/** Vista Operación: tareas con foto ejecutadas en el Punto (Hitos de patrulla, Consignas, Bitácora, fotos del puesto del relevo), sus fotos y el veredicto de VISINT. */
@Path("/api/operation") @Produces(MediaType.APPLICATION_JSON)
public class OperationResource {
    public record ReviewSummary(UUID id, String status, String result, boolean simulated) {}
    /** module: PATRULLA | CONSIGNA | BITACORA | RELEVO. group: patrulla / consigna / acreditación / relevo; task: Hito / evidencia / campo / foto del puesto. */
    public record ExecutionRow(UUID id, Instant executedAt, String module, String postCode, String postName, String protocolCode, int protocolVersion,
                               String groupCode, String groupName, String taskCode, String taskName, String employeeName, int captureNo,
                               List<UUID> evidenceIds, List<String> flags, ReviewSummary review) {}
    /** distanceM / radiusM: distancia de la foto al punto de referencia (Hito o consigna con GPS) y radio esperado; null si falta alguna coordenada. Solo informativo. */
    public record EvidenceView(UUID id, Instant capturedAt, Double latitude, Double longitude, String source, List<String> flags, Integer distanceM, Integer radiusM) {}
    public record ReviewDetail(UUID id, String status, String result, String findings, UUID matchedStandardImageId, String reasonCode, String modelVersion,
                               int standardImageVersion, boolean simulated, int attempts, String lastError,
                               Instant createdAt, Instant requestedAt, Instant reviewedAt, Double matchThreshold, Double matchScore) {}
    public record StandardView(UUID id, int position) {}
    public record ExecutionDetail(ExecutionRow row, String observation, Double latitude, Double longitude, String standardNotes, List<EvidenceView> evidences,
                                  List<StandardView> standards, ReviewDetail review) {}

    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject EntityManager em;
    @Inject StorageService storage;
    @Inject StandardImageStore standardImages;
    @org.eclipse.microprofile.config.inject.ConfigProperty(name="sgi.evidence.default-radius-m") int defaultRadius;

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
        double[] ref = reference(x);
        List<EvidenceView> photos = photos(x.id).stream().map(o -> new EvidenceView(o.id, o.capturedAt, o.latitude, o.longitude, o.source, flags(o.flags),
            ref == null || o.latitude == null || o.longitude == null ? null : (int) Math.round(com.cajamarca.sgi.comando.operator.GeoDistance.meters(o.latitude, o.longitude, ref[0], ref[1])),
            ref == null ? null : (int) ref[2])).toList();
        List<StandardView> standards = r != null ? VisualReviewStandard.of(r.id).stream().map(s -> new StandardView(s.standardImageId, s.position)).toList()
            : StandardReferenceImage.of(x.targetType, x.targetId).stream().map(i -> new StandardView(i.id, i.position)).toList();
        return new ExecutionDetail(row(x), x.observation, x.latitude, x.longitude, standardNotes(x), photos, standards, r == null ? null : detail(r));
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
        StandardReferenceImage i = StandardReferenceImage.find("id=?1 and targetType=?2 and targetId=?3", standardId, x.targetType, x.targetId).firstResult();
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
        r.qualityValid = null; r.qualityScore = null; r.matchCompatible = null; r.matchScore = null;
        return detail(r);
    }

    /** Etiquetas por módulo: protocolo (código, versión), grupo (código, nombre) y tarea (código, nombre). */
    private static final Map<String,String[]> LABELS = Map.of(
        StandardReferenceImage.PATROL_CHECKPOINT, new String[]{"PATRULLA", """
            select pp.code, pp.version_no, pd.code, pd.name, pc.code, pc.name from patrol_checkpoint pc
            join patrol_definition pd on pd.id=pc.patrol_definition_id join patrol_protocol pp on pp.id=pd.protocol_id where pc.id=:t"""},
        StandardReferenceImage.CONSIGNMENT_EVIDENCE, new String[]{"CONSIGNA", """
            select cp.code, cp.version_no, c.code, c.title, 'E' || ce.sort_order, ce.name from consignment_evidence ce
            join consignment c on c.id=ce.consignment_id join consignment_protocol cp on cp.id=c.protocol_id where ce.id=:t"""},
        StandardReferenceImage.LOGBOOK_FIELD, new String[]{"BITACORA", """
            select lp.code, lp.version_no, a.code, lp.name, f.section, f.name from logbook_protocol_field f
            join logbook_protocol lp on lp.id=f.protocol_id left join logbook_accreditation a on a.id=f.accreditation_id where f.id=:t"""});

    @SuppressWarnings("unchecked")
    private ExecutionRow row(TaskExecution x) {
        Object[] who = (Object[]) em.createNativeQuery("""
            select po.code, po.name, e.full_name from task_execution t join post po on po.id=t.post_id
            left join employee_operational_snapshot e on e.employee_id=t.employee_id and e.instance_country_id=t.instance_country_id where t.id=:id""")
            .setParameter("id", x.id).getSingleResult();
        boolean relief = com.cajamarca.sgi.comando.operator.ReliefStationReviews.TYPE.equals(x.executionType);
        String[] label = relief ? new String[]{"RELEVO", null} : LABELS.get(x.targetType);
        List<Object[]> found = label == null || relief ? List.of() : em.createNativeQuery(label[1]).setParameter("t", x.targetId).getResultList();
        Object[] m = found.isEmpty() ? new Object[6] : found.get(0);
        if (relief) {
            String[] station = com.cajamarca.sgi.comando.operator.ReliefStationReviews.station(x);
            m = new Object[]{"Relevo", 0, null, "Fotos del puesto", station[0], station[1]};
        }
        List<EvidenceObject> photos = photos(x.id);
        Set<String> flags = new TreeSet<>();
        photos.forEach(o -> flags.addAll(flags(o.flags)));
        VisualReview r = VisualReview.find("taskExecutionId", x.id).firstResult();
        return new ExecutionRow(x.id, x.executedAt, label == null ? x.targetType : label[0], (String) who[0], (String) who[1],
            (String) m[0], m[1] == null ? x.protocolVersionNo : ((Number) m[1]).intValue(), (String) m[2], (String) m[3], (String) m[4], (String) m[5],
            who[2] == null ? x.username : (String) who[2], x.captureNo, photos.stream().map(o -> o.id).toList(), List.copyOf(flags),
            r == null ? null : new ReviewSummary(r.id, r.status, r.result, r.simulated));
    }

    private static String standardNotes(TaskExecution x) {
        return switch (x.targetType) {
            case StandardReferenceImage.PATROL_CHECKPOINT -> { PatrolCheckpoint cp = PatrolCheckpoint.findById(x.targetId); yield cp == null ? null : cp.standardImageNotes; }
            case StandardReferenceImage.CONSIGNMENT_EVIDENCE -> {
                com.cajamarca.sgi.comando.consignments.ConsignmentEvidence e = com.cajamarca.sgi.comando.consignments.ConsignmentEvidence.findById(x.targetId);
                yield e == null ? null : e.standardImageNotes;
            }
            case StandardReferenceImage.LOGBOOK_FIELD -> {
                com.cajamarca.sgi.comando.bitacora.LogbookProtocolField f = com.cajamarca.sgi.comando.bitacora.LogbookProtocolField.findById(x.targetId);
                yield f == null ? null : f.standardImageNotes;
            }
            case StandardReferenceImage.POST_CONFIG -> {
                com.cajamarca.sgi.comando.postconfig.PostOperationalConfig c = com.cajamarca.sgi.comando.postconfig.PostOperationalConfig.find("postId=?1 and instanceCountryId=?2", x.postId, x.instanceCountryId).firstResult();
                yield c == null ? null : c.visualTitle;
            }
            default -> null;
        };
    }

    /** Punto de referencia de la tarea: [latitud, longitud, radio en m] del Hito, o de la consigna en modo GPS; null si no tiene. */
    private double[] reference(TaskExecution x) {
        if (StandardReferenceImage.PATROL_CHECKPOINT.equals(x.targetType)) {
            PatrolCheckpoint cp = PatrolCheckpoint.findById(x.targetId);
            return cp == null || cp.latitude == null || cp.longitude == null ? null : new double[]{cp.latitude, cp.longitude, cp.radiusM == null ? defaultRadius : cp.radiusM};
        }
        if (StandardReferenceImage.CONSIGNMENT_EVIDENCE.equals(x.targetType)) {
            com.cajamarca.sgi.comando.consignments.ConsignmentEvidence e = com.cajamarca.sgi.comando.consignments.ConsignmentEvidence.findById(x.targetId);
            com.cajamarca.sgi.comando.consignments.Consignment c = e == null ? null : com.cajamarca.sgi.comando.consignments.Consignment.findById(e.consignmentId);
            return c == null || !"GPS".equals(c.expectedLocationMode) || c.expectedLatitude == null || c.expectedLongitude == null ? null : new double[]{c.expectedLatitude, c.expectedLongitude, defaultRadius};
        }
        return null;
    }

    private ReviewDetail detail(VisualReview r) {
        return new ReviewDetail(r.id, r.status, r.result, r.findings, r.matchedStandardImageId, r.reasonCode, r.modelVersion, r.standardImageVersion, r.simulated, r.attempts, r.lastError, r.createdAt, r.requestedAt, r.reviewedAt, r.matchThreshold, r.matchScore);
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
