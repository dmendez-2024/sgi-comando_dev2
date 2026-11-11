package com.cajamarca.sgi.comando.operation;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.assignments.AssignmentResource;
import com.cajamarca.sgi.comando.companies.Company;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.operations.PostEntity;
import com.cajamarca.sgi.comando.operations.ServiceEntity;
import com.cajamarca.sgi.comando.security.IdentIdentity;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

@Path("/api/incidents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","MONITOR"})
public class IncidentNotificationResource {
    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject OperationResource operation;
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;
    @Inject IncidentEvidenceResource images;
    @Inject AssignmentResource assignments;
    @Inject IncidentCoverageRolePolicy coverageRolePolicy;
    @Inject IncidentCatalogService catalog;
    @Inject IncidentSanctionDeliveryService sanctionDelivery;
    @Inject SecurityIdentity identity;

    @GET @Path("/catalog")
    public ObjectNode catalog() {
        return catalog.catalog();
    }

    @GET
    public List<ObjectNode> list() throws JsonProcessingException {
        Set<UUID> allowed = scope.allowedCompanyIdsForCompanyRoles();
        List<IncidentNotificationEntity> records = allowed.isEmpty()
                ? IncidentNotificationEntity.list("instanceCountryId=?1 and companyId is null and createdBy=?2 order by updatedAt desc",
                    tenant.instanceCountryId(), scope.username())
                : IncidentNotificationEntity.list("instanceCountryId=?1 and (companyId in ?2 or (companyId is null and createdBy=?3)) order by updatedAt desc",
                    tenant.instanceCountryId(), allowed, scope.username());
        List<ObjectNode> result = new ArrayList<>(records.size());
        for (IncidentNotificationEntity record : records) result.add(view(record));
        return result;
    }

    @POST @Transactional
    public ObjectNode create(ObjectNode body) throws JsonProcessingException {
        IncidentNotificationEntity record = new IncidentNotificationEntity();
        record.id = UUID.randomUUID();
        record.instanceCountryId = tenant.instanceCountryId();
        record.code = "INC-" + Instant.now().atZone(ZoneId.of("America/Guayaquil")).getYear() + "-"
                + String.format("%06d", ((Number) em.createNativeQuery("select nextval('incident_notification_code_seq')").getSingleResult()).longValue());
        record.createdBy = scope.username();
        record.sanctionDeliveryStatus = "NOT_REQUIRED";
        apply(record, body);
        record.persistAndFlush();
        sanctionDelivery.deliverIfRequired(record, body);
        return view(record);
    }

    @PUT @Path("/{id}") @Transactional
    public ObjectNode update(@PathParam("id") UUID id, ObjectNode body) throws JsonProcessingException {
        IncidentNotificationEntity record = findVisible(id);
        apply(record, body);
        record.flush();
        sanctionDelivery.deliverIfRequired(record, body);
        return view(record);
    }

    private IncidentNotificationEntity findVisible(UUID id) {
        return images.visible(id, true);
    }

    private void apply(IncidentNotificationEntity record, ObjectNode body) throws JsonProcessingException {
        if (body == null) throw new BadRequestException("El incidente es obligatorio");
        String status = text(body, "status");
        if (!Set.of("DRAFT", "FINALIZED").contains(status)) throw new BadRequestException("Estado de incidente inválido");
        JsonNode previous = record.payloadJson == null ? mapper.createObjectNode() : mapper.readTree(record.payloadJson);
        sanctionDelivery.requireQueuedSanctionUnchanged(record, previous, body);
        IncidentCatalogService.Selection selection = catalog.canonicalize(body, "FINALIZED".equals(status));
        String appliedAssignmentId = text(previous, "coverageAssignmentId");
        body.remove("coverageAssignmentId");
        body.remove("coverageAppliedAt");
        if (!appliedAssignmentId.isEmpty()) {
            if (!"FINALIZED".equals(status)) throw new BadRequestException("Un incidente con cobertura aplicada no puede volver a borrador.");
            if (!Objects.equals(text(previous,"targetShiftId"),text(body,"targetShiftId"))
                    || !Objects.equals(text(previous,"replacementEmployeeId"),text(body,"replacementEmployeeId"))
                    || !Objects.equals(text(previous,"incidentType"),text(body,"incidentType"))
                    || !Objects.equals(text(previous,"category"),text(body,"category"))
                    || !Objects.equals(text(previous,"subcategory"),text(body,"subcategory"))
                    || !Objects.equals(text(previous,"pointId"),text(body,"pointId"))
                    || !Objects.equals(text(previous,"postId"),text(body,"postId")))
                throw new BadRequestException("La cobertura ya aplicada no puede cambiarse desde el incidente; use Asignaciones para otra reasignación.");
            body.put("coverageAssignmentId", appliedAssignmentId);
            body.put("coverageAppliedAt", text(previous,"coverageAppliedAt"));
        }
        UUID pointId = uuid(body, "pointId"), postId = uuid(body, "postId");
        UUID operationalReportId = uuid(body, "operationalReportId");
        if (operationalReportId != null) {
            @SuppressWarnings("unchecked") List<Object> linked = em.createNativeQuery("""
                select id from operator_operational_report
                 where id=:id and instance_country_id=:tenant and report_type='INCIDENT'
                """).setParameter("id", operationalReportId).setParameter("tenant", tenant.instanceCountryId()).getResultList();
            if (linked.isEmpty()) throw new BadRequestException("La novedad relacionada no existe o no es un incidente");
            if (record.operationalReportId != null && !record.operationalReportId.equals(operationalReportId))
                throw new BadRequestException("La novedad relacionada de un incidente existente no puede cambiarse");
            body.put("operationalReportId", operationalReportId.toString());
        } else if (record.operationalReportId != null) {
            operationalReportId = record.operationalReportId;
            body.put("operationalReportId", operationalReportId.toString());
        }
        PointEntity point = null;
        PostEntity post = null;
        if (pointId != null) {
            point = PointEntity.find("id=?1 and instanceCountryId=?2", pointId, tenant.instanceCountryId()).firstResult();
            if (point == null) throw new NotFoundException("Punto no encontrado");
            scope.requireCompanyForCompanyRoles(point.companyId);
            ServiceEntity service = ServiceEntity.find("id=?1 and instanceCountryId=?2", point.serviceId, tenant.instanceCountryId()).firstResult();
            if (service == null) throw new BadRequestException("El Punto no tiene Servicio válido");
            if (uuid(body, "clientId") != null && !service.clientId.equals(uuid(body, "clientId")))
                throw new BadRequestException("El Cliente no corresponde al Punto");
            Company company = Company.find("id=?1 and instanceCountryId=?2", point.companyId, tenant.instanceCountryId()).firstResult();
            body.put("clientId", service.clientId.toString());
            body.put("client", service.clientName);
            body.put("point", point.name);
            body.put("companyId", point.companyId.toString());
            body.put("company", company == null ? "" : company.name);
            body.put("companyCode", company == null ? "" : company.code);
            body.put("city", point.city);
            if (postId != null) {
                post = PostEntity.find("id=?1 and pointId=?2 and instanceCountryId=?3", postId, pointId, tenant.instanceCountryId()).firstResult();
                if (post == null) throw new BadRequestException("El Puesto no corresponde al Punto");
                body.put("post", post.name);
            } else body.put("post", "");
        } else {
            if (postId != null) throw new BadRequestException("Seleccione el Punto antes del Puesto");
            body.put("clientId", ""); body.put("client", ""); body.put("point", "");
            body.put("companyId", ""); body.put("company", ""); body.put("companyCode", "");
            body.put("city", ""); body.put("post", "");
        }

        String incidentType = text(body, "incidentType");
        String absenceMode = "Inasistencia programada".equals(incidentType) ? "PROGRAMMED"
                : "Inasistencia efectiva".equals(incidentType) ? "EFFECTIVE" : "";
        body.put("absenceMode", absenceMode);
        if ("FINALIZED".equals(status)) {
            if (text(body, "title").isBlank() || text(body, "category").isBlank()
                    || text(body, "subcategory").isBlank() || incidentType.isBlank()
                    || text(body, "severity").isBlank() || point == null
                    || !body.path("collaboratorIds").isArray() || body.path("collaboratorIds").isEmpty()
                    || text(body, "description").isBlank() || text(body, "resolution").isBlank())
                throw new BadRequestException("Complete los campos obligatorios antes de finalizar");
            if (body.path("sanction").asBoolean(false) && text(body, "sanctionDescription").isBlank())
                throw new BadRequestException("Describa la sanción");
        }
        if (!absenceMode.isEmpty() && "FINALIZED".equals(status) && appliedAssignmentId.isEmpty()) {
            if (post == null) throw new BadRequestException("Seleccione el Puesto para la cobertura");
            if ("EFFECTIVE".equals(absenceMode)) coverageRolePolicy.requireCurrentShiftPermission();
            else coverageRolePolicy.requireFutureShiftPermission();
            UUID shiftId = uuid(body, "targetShiftId"), employeeId = uuid(body, "replacementEmployeeId");
            OperationResource.CoverageOptions options = operation.coverageOptions(pointId, postId);
            List<OperationResource.CoverageShift> shifts = "EFFECTIVE".equals(absenceMode)
                    ? options.currentShift() == null ? List.of() : List.of(options.currentShift()) : options.nextShifts();
            OperationResource.CoverageShift shift = shifts.stream().filter(s -> s.id().equals(shiftId)).findFirst().orElse(null);
            if (shift == null || employeeId == null) throw new BadRequestException("El turno o agente de cobertura ya no está disponible");
            OperationResource.ReplacementCandidate candidate = shift.candidates().stream()
                    .filter(c -> c.employeeId().equals(employeeId)).findFirst().orElse(null);
            if (candidate == null) throw new BadRequestException("El agente de cobertura ya no está disponible");
            body.put("targetShiftLabel", shift.startsAt() + " / " + shift.endsAt());
            body.put("replacementEmployeeName", candidate.fullName());
            AssignmentResource.AssignmentDto assignment=assignments.assignIncidentCoverage(pointId,postId,shiftId,employeeId,
                    "Consola · " + incidentType + " · " + record.code);
            body.put("coverageAssignmentId", assignment.id().toString());
            body.put("coverageAppliedAt", Instant.now().toString());
        }
        if ("FINALIZED".equals(status)) {
            operationalReportId = ensureOperationalReport(record, operationalReportId, body, selection, pointId, postId);
            body.put("operationalReportId", operationalReportId.toString());
        } else if (operationalReportId != null && isConsoleReport(operationalReportId)) {
            throw new ClientErrorException("Un incidente enviado a Novedades no puede volver a borrador.", 409);
        }
        record.status = status;
        record.pointId = pointId;
        record.postId = postId;
        record.operationalReportId = operationalReportId;
        record.companyId = point == null ? null : point.companyId;
        record.categoryCatalogId = selection.category() == null ? null : selection.category().id();
        record.subcategoryCatalogId = selection.subcategory() == null ? null : selection.subcategory().id();
        record.incidentTypeCatalogId = selection.incidentType() == null ? null : selection.incidentType().id();
        record.severityCatalogId = selection.severity() == null ? null : selection.severity().id();
        record.updatedBy = scope.username();
        try { images.normalize(record, body); }
        catch (java.io.IOException e) { throw new InternalServerErrorException("No se pudieron validar las imágenes", e); }
        record.payloadJson = mapper.writeValueAsString(body);
        if (record.payloadJson.length() > 200_000) throw new BadRequestException("El incidente supera el tamaño permitido");
    }

    private UUID ensureOperationalReport(IncidentNotificationEntity record, UUID reportId, ObjectNode body,
                                         IncidentCatalogService.Selection selection, UUID pointId, UUID postId) {
        Instant occurredAt = incidentDate(record, body);
        String severity = reportSeverity(selection.severity().systemKey());
        String username = scope.username();
        Long personaId = IdentIdentity.personaId(identity);
        UUID employeeId = employeeId(personaId);
        Instant now = Instant.now();

        if (reportId == null) {
            reportId = UUID.randomUUID();
            em.createNativeQuery("""
                insert into operator_operational_report
                  (id,instance_country_id,assignment_id,employee_id,username,point_id,post_id,
                   report_type,category,subcategory,title,description,severity,status,occurred_at,source,
                   created_at,updated_at,routing_target,routing_status,photo_count,reporter_persona_id)
                values
                  (:id,:tenant,null,:employee,:username,:point,:post,
                   'INCIDENT',:category,:subcategory,:title,:description,:severity,'CONFIRMED',:occurred,'SGI_COMANDO',
                   :now,:now,'STC','PENDING_INTEGRATION',0,:persona)
                """).setParameter("id", reportId).setParameter("tenant", tenant.instanceCountryId())
                .setParameter("employee", employeeId).setParameter("username", username)
                .setParameter("point", pointId).setParameter("post", postId)
                .setParameter("category", selection.category().name())
                .setParameter("subcategory", selection.subcategory().name())
                .setParameter("title", reportTitle(body)).setParameter("description", text(body, "description"))
                .setParameter("severity", severity).setParameter("occurred", occurredAt)
                .setParameter("persona", personaId).setParameter("now", now).executeUpdate();
            return reportId;
        }

        @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery("""
            select source,status from operator_operational_report
             where id=:id and instance_country_id=:tenant and report_type='INCIDENT'
             for update
            """).setParameter("id", reportId).setParameter("tenant", tenant.instanceCountryId()).getResultList();
        if (rows.isEmpty()) throw new BadRequestException("La novedad relacionada no existe o no es un incidente");
        Object[] linked = rows.get(0);
        if (!"SGI_COMANDO".equals(linked[0].toString())) return reportId;
        if (!"CONFIRMED".equals(linked[1].toString()))
            throw new ClientErrorException("La novedad ya fue procesada y el incidente no puede modificarse.", 409);

        em.createNativeQuery("""
            update operator_operational_report
               set employee_id=:employee,username=:username,point_id=:point,post_id=:post,
                   category=:category,subcategory=:subcategory,title=:title,description=:description,
                   severity=:severity,occurred_at=:occurred,reporter_persona_id=coalesce(reporter_persona_id,:persona),
                   updated_at=:now
             where id=:id and instance_country_id=:tenant and source='SGI_COMANDO' and status='CONFIRMED'
            """).setParameter("employee", employeeId).setParameter("username", username)
            .setParameter("point", pointId).setParameter("post", postId)
            .setParameter("category", selection.category().name())
            .setParameter("subcategory", selection.subcategory().name())
            .setParameter("title", reportTitle(body)).setParameter("description", text(body, "description"))
            .setParameter("severity", severity).setParameter("occurred", occurredAt)
            .setParameter("persona", personaId).setParameter("now", now)
            .setParameter("id", reportId).setParameter("tenant", tenant.instanceCountryId()).executeUpdate();
        return reportId;
    }

    private boolean isConsoleReport(UUID reportId) {
        @SuppressWarnings("unchecked") List<Object> rows = em.createNativeQuery("""
            select source from operator_operational_report
             where id=:id and instance_country_id=:tenant
            """).setParameter("id", reportId).setParameter("tenant", tenant.instanceCountryId()).getResultList();
        return !rows.isEmpty() && "SGI_COMANDO".equals(rows.get(0).toString());
    }

    private UUID employeeId(Long personaId) {
        if (personaId == null) return null;
        @SuppressWarnings("unchecked") List<Object> rows = em.createNativeQuery("""
            select employee_id from employee_operational_snapshot
             where instance_country_id=:tenant and persona_id=:persona
             order by updated_at desc limit 1
            """).setParameter("tenant", tenant.instanceCountryId()).setParameter("persona", personaId).getResultList();
        if (rows.isEmpty()) return null;
        Object value = rows.get(0);
        return value instanceof UUID id ? id : UUID.fromString(value.toString());
    }

    private static Instant incidentDate(IncidentNotificationEntity record, JsonNode body) {
        String value = text(body, "createdAt");
        if (!value.isEmpty()) {
            try { return Instant.parse(value); }
            catch (Exception e) { throw new BadRequestException("createdAt debe ser una fecha ISO-8601 válida"); }
        }
        return record.createdAt == null ? Instant.now() : record.createdAt;
    }

    private static String reportTitle(JsonNode body) {
        String title = text(body, "title");
        if (title.length() > 200) throw new BadRequestException("El título no puede superar 200 caracteres");
        return title;
    }

    static String reportSeverity(String value) {
        return switch (value == null ? "" : value.trim().toUpperCase(Locale.ROOT)) {
            case "INFORMATIVE", "MINOR", "LOW" -> "LOW";
            case "MODERATE", "MEDIUM" -> "MEDIUM";
            case "MAJOR", "HIGH" -> "HIGH";
            case "CRITICAL" -> "CRITICAL";
            default -> "MEDIUM";
        };
    }

    private ObjectNode view(IncidentNotificationEntity record) throws JsonProcessingException {
        ObjectNode data = (ObjectNode) mapper.readTree(record.payloadJson);
        data.put("id", record.id.toString());
        data.put("code", record.code);
        data.put("status", record.status);
        data.put("companyId", record.companyId == null ? "" : record.companyId.toString());
        data.put("pointId", record.pointId == null ? "" : record.pointId.toString());
        data.put("postId", record.postId == null ? "" : record.postId.toString());
        data.put("operationalReportId", record.operationalReportId == null ? "" : record.operationalReportId.toString());
        data.put("categoryCatalogId", record.categoryCatalogId == null ? "" : record.categoryCatalogId.toString());
        data.put("subcategoryCatalogId", record.subcategoryCatalogId == null ? "" : record.subcategoryCatalogId.toString());
        data.put("incidentTypeCatalogId", record.incidentTypeCatalogId == null ? "" : record.incidentTypeCatalogId.toString());
        data.put("severityCatalogId", record.severityCatalogId == null ? "" : record.severityCatalogId.toString());
        data.put("createdAt", record.createdAt.toString());
        data.put("updatedAt", record.updatedAt.toString());
        data.put("sanctionDeliveryStatus", record.sanctionDeliveryStatus == null ? "NOT_REQUIRED" : record.sanctionDeliveryStatus);
        data.put("sanctionDeliveryAttempts", record.sanctionDeliveryAttempts);
        if (record.sanctionEventId != null) data.put("sanctionEventId", record.sanctionEventId.toString());
        if (record.sanctionSentAt != null) data.put("sanctionSentAt", record.sanctionSentAt.toString());
        return data;
    }

    private static String text(JsonNode body, String key) { return body.path(key).asText("").trim(); }
    private static UUID uuid(JsonNode body, String key) {
        String value = text(body, key);
        if (value.isEmpty()) return null;
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException e) { throw new BadRequestException(key + " inválido"); }
    }
}
