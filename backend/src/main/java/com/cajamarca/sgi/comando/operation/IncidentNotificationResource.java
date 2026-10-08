package com.cajamarca.sgi.comando.operation;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.companies.Company;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.operations.PostEntity;
import com.cajamarca.sgi.comando.operations.ServiceEntity;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
@RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
public class IncidentNotificationResource {
    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject OperationResource operation;
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;

    @GET
    public List<ObjectNode> list() throws JsonProcessingException {
        Set<UUID> allowed = scope.allowedCompanyIds();
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
        apply(record, body);
        record.persistAndFlush();
        return view(record);
    }

    @PUT @Path("/{id}") @Transactional
    public ObjectNode update(@PathParam("id") UUID id, ObjectNode body) throws JsonProcessingException {
        IncidentNotificationEntity record = findVisible(id);
        apply(record, body);
        record.flush();
        return view(record);
    }

    private IncidentNotificationEntity findVisible(UUID id) {
        IncidentNotificationEntity record = IncidentNotificationEntity.find(
                "id=?1 and instanceCountryId=?2", id, tenant.instanceCountryId()).firstResult();
        if (record == null) throw new NotFoundException("Incidente no encontrado");
        if (record.companyId == null) {
            if (!record.createdBy.equals(scope.username())) throw new ForbiddenException("Borrador fuera de alcance");
        } else scope.requireCompany(record.companyId);
        return record;
    }

    private void apply(IncidentNotificationEntity record, ObjectNode body) throws JsonProcessingException {
        if (body == null) throw new BadRequestException("El incidente es obligatorio");
        String status = text(body, "status");
        if (!Set.of("DRAFT", "FINALIZED").contains(status)) throw new BadRequestException("Estado de incidente inválido");
        UUID pointId = uuid(body, "pointId"), postId = uuid(body, "postId");
        PointEntity point = null;
        PostEntity post = null;
        if (pointId != null) {
            point = PointEntity.find("id=?1 and instanceCountryId=?2", pointId, tenant.instanceCountryId()).firstResult();
            if (point == null) throw new NotFoundException("Punto no encontrado");
            scope.requireCompany(point.companyId);
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
        if (!absenceMode.isEmpty() && "FINALIZED".equals(status)) {
            if (post == null) throw new BadRequestException("Seleccione el Puesto para la cobertura");
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
        }
        // El formulario registra la decisión de cobertura; no cambia por sí mismo el plan de Asignaciones.
        record.status = status;
        record.pointId = pointId;
        record.postId = postId;
        record.companyId = point == null ? null : point.companyId;
        record.updatedBy = scope.username();
        record.payloadJson = mapper.writeValueAsString(body);
        if (record.payloadJson.length() > 200_000) throw new BadRequestException("El incidente supera el tamaño permitido");
    }

    private ObjectNode view(IncidentNotificationEntity record) throws JsonProcessingException {
        ObjectNode data = (ObjectNode) mapper.readTree(record.payloadJson);
        data.put("id", record.id.toString());
        data.put("code", record.code);
        data.put("status", record.status);
        data.put("companyId", record.companyId == null ? "" : record.companyId.toString());
        data.put("pointId", record.pointId == null ? "" : record.pointId.toString());
        data.put("postId", record.postId == null ? "" : record.postId.toString());
        data.put("createdAt", record.createdAt.toString());
        data.put("updatedAt", record.updatedAt.toString());
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
