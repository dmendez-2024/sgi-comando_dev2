package com.cajamarca.sgi.comando.operation;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.execution.EvidenceObject;
import com.cajamarca.sgi.comando.storage.StorageService;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** Fotos existentes de registros operativos, siempre limitadas al alcance de compañía del lector. */
@Path("/api/operation/events/{eventId}/evidences")
@RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
public class OperationalEvidenceResource {
    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject EntityManager em;
    @Inject StorageService storage;
    @Inject ObjectMapper mapper;

    @GET @Produces(MediaType.APPLICATION_JSON)
    public ArrayNode list(@PathParam("eventId") UUID eventId) {
        UUID pointId = requireVisibleEvent(eventId);
        ArrayNode result = mapper.createArrayNode();
        for (Object[] row : linkedPhotos(eventId, pointId)) {
            UUID id = (UUID) row[0];
            if (row[1] != null && row[1].toString().startsWith("image/"))
                result.addObject().put("id", id.toString()).put("source", "OBJECT");
        }
        for (Object[] row : patrolPhotos(eventId)) {
            if (embeddedPhoto((String) row[1]) != null)
                result.addObject().put("id", row[0].toString()).put("source", "PATROL");
        }
        return result;
    }

    @GET @Path("/{photoId}/content") @Produces("image/*")
    public Response content(@PathParam("eventId") UUID eventId, @PathParam("photoId") UUID photoId) {
        UUID pointId = requireVisibleEvent(eventId);
        if (linkedPhotos(eventId, pointId).stream().anyMatch(row -> photoId.equals(row[0]))) {
            EvidenceObject object = EvidenceObject.find("id=?1 and instanceCountryId=?2 and status in ?3",
                photoId, tenant.instanceCountryId(), List.of("STORED", "ATTACHED")).firstResult();
            if (object != null && object.contentType != null && object.contentType.startsWith("image/"))
                return Response.ok(storage.read(object.bucket, object.objectKey)).type(object.contentType)
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300").build();
        }
        for (Object[] row : patrolPhotos(eventId)) {
            if (!photoId.equals(row[0])) continue;
            byte[] photo = embeddedPhoto((String) row[1]);
            if (photo != null) return Response.ok(photo).type("image/jpeg")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300").build();
        }
        throw new NotFoundException("Fotografía no encontrada");
    }

    private UUID requireVisibleEvent(UUID eventId) {
        String[] queries = {
            "select p.id,p.company_id from operator_logbook_record r join point p on p.id=r.point_id and p.instance_country_id=r.instance_country_id where r.id=:event and r.instance_country_id=:tenant",
            "select p.id,p.company_id from operator_consignment_review_request r join point p on p.id=r.point_id and p.instance_country_id=r.instance_country_id where r.id=:event and r.instance_country_id=:tenant",
            "select p.id,p.company_id from operator_operational_report r join point p on p.id=r.point_id and p.instance_country_id=r.instance_country_id where r.id=:event and r.instance_country_id=:tenant",
            "select p.id,p.company_id from patrol_execution r join patrol_plan pl on pl.id=r.patrol_plan_id and pl.instance_country_id=r.instance_country_id join post po on po.id=pl.post_id and po.instance_country_id=r.instance_country_id join point p on p.id=po.point_id and p.instance_country_id=r.instance_country_id where r.id=:event and r.instance_country_id=:tenant",
            "select p.id,p.company_id from task_execution r join point p on p.id=r.point_id and p.instance_country_id=r.instance_country_id where r.id=:event and r.instance_country_id=:tenant"
        };
        for (String sql : queries) {
            @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery(sql).setParameter("event", eventId)
                .setParameter("tenant", tenant.instanceCountryId()).getResultList();
            if (rows.isEmpty()) continue;
            if (rows.get(0)[1] == null) throw new NotFoundException("Registro fuera de alcance");
            scope.requireCompany(UUID.fromString(rows.get(0)[1].toString()));
            return UUID.fromString(rows.get(0)[0].toString());
        }
        throw new NotFoundException("Registro operativo no encontrado");
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> patrolPhotos(UUID eventId) {
        return em.createNativeQuery("select id,evidence_json from patrol_checkpoint_execution where instance_country_id=:tenant and patrol_execution_id=:event order by validated_at,id")
            .setParameter("tenant", tenant.instanceCountryId()).setParameter("event", eventId).getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> linkedPhotos(UUID eventId, UUID pointId) {
        return em.createNativeQuery("""
            select distinct e.id,e.content_type from evidence_object e
            join task_execution_evidence link on link.evidence_id=e.id
            join task_execution task on task.id=link.task_execution_id and task.instance_country_id=e.instance_country_id
            where e.instance_country_id=:tenant and e.status in ('STORED','ATTACHED') and task.point_id=:point
              and (task.id=:event or task.group_id=:event or task.patrol_execution_id=:event)
            order by e.id
            """).setParameter("tenant", tenant.instanceCountryId()).setParameter("event", eventId)
            .setParameter("point", pointId).getResultList();
    }

    private byte[] embeddedPhoto(String json) {
        try {
            JsonNode data = mapper.readTree(json);
            String encoded = data.path("photoBase64").asText("");
            return encoded.isBlank() ? null : Base64.getDecoder().decode(encoded);
        } catch (Exception ignored) {
            return null;
        }
    }
}
