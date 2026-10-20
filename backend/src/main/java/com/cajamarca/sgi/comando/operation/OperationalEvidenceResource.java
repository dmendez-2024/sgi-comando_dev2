package com.cajamarca.sgi.comando.operation;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.execution.EvidenceObject;
import com.cajamarca.sgi.comando.storage.StandardReferenceImage;
import com.cajamarca.sgi.comando.storage.StandardReferenceImages;
import com.cajamarca.sgi.comando.storage.StorageService;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fotos existentes de registros operativos, siempre limitadas al alcance de compañía del lector.
 * /references sirve lo que el agente recibe como guía: fotos estándar del destino y, en patrullas, el plano ATS del punto.
 */
@Path("/api/operation/events/{eventId}/evidences")
@RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
public class OperationalEvidenceResource {
    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject EntityManager em;
    @Inject StorageService storage;
    @Inject ObjectMapper mapper;
    @Inject StandardReferenceImages references;

    private static final int RELIEF = 0, LOGBOOK = 1, PATROL = 4, TASK = 5;
    private record VisibleEvent(UUID pointId, int kind) {}
    private record Reference(UUID id, String source, String label) {}

    @GET @Produces(MediaType.APPLICATION_JSON)
    public ArrayNode list(@PathParam("eventId") UUID eventId) {
        UUID pointId = requireVisibleEvent(eventId);
        ArrayNode result = mapper.createArrayNode();
        List<Object[]> relief = reliefPhotos(eventId);
        for (Object[] row : relief) {
            UUID id = (UUID) row[0];
            if (row[2] != null && row[2].toString().startsWith("image/"))
                result.addObject().put("id", id.toString()).put("source", "RELIEF").put("purpose", row[1].toString());
        }
        if (relief.isEmpty()) for (Object[] row : linkedPhotos(eventId, pointId)) {
            UUID id = (UUID) row[0];
            if (row[1] != null && row[1].toString().startsWith("image/"))
                result.addObject().put("id", id.toString()).put("source", "OBJECT");
        }
        for (Object[] row : inventoryPhotos(eventId, null)) {
            if (row[1] != null && row[1].toString().startsWith("image/"))
                result.addObject().put("id", row[0].toString()).put("source", "INVENTORY").put("purpose", "inventory");
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
        // Cada fuente consulta solo la foto pedida: no se cargan los demás archivos del evento.
        if (!linkedPhotos(eventId, pointId, photoId).isEmpty() || !inventoryPhotos(eventId, photoId).isEmpty()) {
            EvidenceObject object = EvidenceObject.find("id=?1 and instanceCountryId=?2 and status in ?3",
                photoId, tenant.instanceCountryId(), List.of("STORED", "ATTACHED")).firstResult();
            if (object != null && object.contentType != null && object.contentType.startsWith("image/"))
                return image(storage.read(object.bucket, object.objectKey), object.contentType);
        }
        Object[] relief = reliefPhoto(eventId, photoId);
        if (relief != null && relief[0] != null && relief[0].toString().startsWith("image/"))
            return image((byte[]) relief[1], relief[0].toString());
        byte[] patrol = embeddedPhoto(patrolPhotoJson(eventId, photoId));
        if (patrol != null) return image(patrol, "image/jpeg");
        throw new NotFoundException("Fotografía no encontrada");
    }

    @GET @Path("/references") @Produces(MediaType.APPLICATION_JSON)
    public ArrayNode referenceList(@PathParam("eventId") UUID eventId) {
        ArrayNode result = mapper.createArrayNode();
        for (Reference ref : references(visibleEvent(eventId), eventId))
            result.addObject().put("id", ref.id().toString()).put("source", ref.source()).put("label", ref.label());
        return result;
    }

    @GET @Path("/references/{imageId}/content") @Produces("image/*")
    public Response referenceContent(@PathParam("eventId") UUID eventId, @PathParam("imageId") UUID imageId) {
        Reference ref = references(visibleEvent(eventId), eventId).stream().filter(r -> r.id().equals(imageId)).findFirst()
            .orElseThrow(() -> new NotFoundException("Foto de referencia no encontrada"));
        if ("ATS_PLAN".equals(ref.source())) {
            @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery("select plan_image,plan_content_type from ats_point_package where id=:id and instance_country_id=:tenant")
                .setParameter("id", imageId).setParameter("tenant", tenant.instanceCountryId()).getResultList();
            if (rows.isEmpty()) throw new NotFoundException("Plano ATS no encontrado");
            return image(rows.get(0)[0], rows.get(0)[1].toString());
        }
        StandardReferenceImage img = StandardReferenceImage.find("id=?1 and instanceCountryId=?2", imageId, tenant.instanceCountryId()).firstResult();
        if (img == null) throw new NotFoundException("Foto de referencia no encontrada");
        return image(references.read(img), img.contentType);
    }

    /** Solo metadatos de las fotos guía según el tipo de registro; los archivos se leen al servir cada una. */
    @SuppressWarnings("unchecked")
    private List<Reference> references(VisibleEvent event, UUID eventId) {
        List<Reference> out = new ArrayList<>();
        UUID t = tenant.instanceCountryId();
        switch (event.kind()) {
            case RELIEF -> {
                List<Object> post = em.createNativeQuery("select post_id from relief_event where id=:event and instance_country_id=:tenant")
                    .setParameter("event", eventId).setParameter("tenant", t).getResultList();
                if (!post.isEmpty()) standards(out, StandardReferenceImage.POST_CONFIG, Map.of(UUID.fromString(post.get(0).toString()), "Referencia del puesto"));
            }
            case PATROL -> {
                List<Object[]> checkpoints = em.createNativeQuery("""
                    select c.id,c.sequence_no,c.name from patrol_execution r
                    join patrol_plan pl on pl.id=r.patrol_plan_id and pl.instance_country_id=r.instance_country_id
                    join patrol_checkpoint c on c.patrol_definition_id=pl.patrol_definition_id and c.instance_country_id=r.instance_country_id
                    where r.id=:event and r.instance_country_id=:tenant order by c.sequence_no""")
                    .setParameter("event", eventId).setParameter("tenant", t).getResultList();
                standards(out, StandardReferenceImage.PATROL_CHECKPOINT, checkpoints.stream().collect(Collectors.toMap(
                    row -> UUID.fromString(row[0].toString()), row -> "Hito " + row[1] + " · " + row[2], (a, b) -> a, LinkedHashMap::new)));
                List<Object[]> plan = em.createNativeQuery("select id,revision_no from ats_point_package where instance_country_id=:tenant and point_id=:point and is_current=true and octet_length(plan_image)>0")
                    .setParameter("tenant", t).setParameter("point", event.pointId()).getResultList();
                if (!plan.isEmpty()) out.add(new Reference(UUID.fromString(plan.get(0)[0].toString()), "ATS_PLAN", "Plano ATS · revisión " + plan.get(0)[1]));
            }
            case LOGBOOK -> {
                List<Object> refs = em.createNativeQuery("select evidence_refs_json from operator_logbook_record where id=:event and instance_country_id=:tenant")
                    .setParameter("event", eventId).setParameter("tenant", t).getResultList();
                List<UUID> fieldIds = new ArrayList<>();
                if (!refs.isEmpty() && refs.get(0) != null) {
                    try { mapper.readTree(refs.get(0).toString()).forEach(id -> fieldIds.add(UUID.fromString(id.asText()))); }
                    catch (Exception ignored) { fieldIds.clear(); }
                }
                if (!fieldIds.isEmpty()) {
                    List<Object[]> fields = em.createNativeQuery("select id,name from logbook_protocol_field where instance_country_id=:tenant and id in (:ids) order by section,sort_order")
                        .setParameter("tenant", t).setParameter("ids", fieldIds).getResultList();
                    standards(out, StandardReferenceImage.LOGBOOK_FIELD, fields.stream().collect(Collectors.toMap(
                        row -> UUID.fromString(row[0].toString()), row -> row[1].toString(), (a, b) -> a, LinkedHashMap::new)));
                }
            }
            case TASK -> {
                List<Object[]> target = em.createNativeQuery("select target_type,target_id from task_execution where id=:event and instance_country_id=:tenant")
                    .setParameter("event", eventId).setParameter("tenant", t).getResultList();
                if (!target.isEmpty() && target.get(0)[1] != null)
                    standards(out, target.get(0)[0].toString(), Map.of(UUID.fromString(target.get(0)[1].toString()), "Foto estándar"));
            }
            default -> { }
        }
        return out;
    }

    /** Fotos estándar de varios destinos en una sola consulta, en el orden de los destinos y de su posición. */
    private void standards(List<Reference> out, String targetType, Map<UUID, String> labels) {
        if (labels.isEmpty()) return;
        List<StandardReferenceImage> images = StandardReferenceImage.list("instanceCountryId=?1 and targetType=?2 and targetId in ?3 order by position",
            tenant.instanceCountryId(), targetType, List.copyOf(labels.keySet()));
        labels.forEach((target, label) -> {
            List<StandardReferenceImage> own = images.stream().filter(img -> img.targetId.equals(target)).toList();
            for (StandardReferenceImage img : own)
                out.add(new Reference(img.id, "STANDARD", own.size() > 1 ? label + " · " + img.position : label));
        });
    }

    private static Response image(Object content, String contentType) {
        return Response.ok(content).type(contentType).header(HttpHeaders.CACHE_CONTROL, "private, max-age=300").build();
    }

    private UUID requireVisibleEvent(UUID eventId) {
        return visibleEvent(eventId).pointId();
    }

    /** Punto del registro y su tipo: el índice de la consulta que lo encontró (RELIEF, LOGBOOK, …, PATROL, TASK). */
    private VisibleEvent visibleEvent(UUID eventId) {
        String[] queries = {
            "select p.id,p.company_id from relief_event r join post po on po.id=r.post_id and po.instance_country_id=r.instance_country_id join point p on p.id=po.point_id and p.instance_country_id=r.instance_country_id where r.id=:event and r.instance_country_id=:tenant",
            "select p.id,p.company_id from operator_logbook_record r join point p on p.id=r.point_id and p.instance_country_id=r.instance_country_id where r.id=:event and r.instance_country_id=:tenant",
            "select p.id,p.company_id from operator_consignment_review_request r join point p on p.id=r.point_id and p.instance_country_id=r.instance_country_id where r.id=:event and r.instance_country_id=:tenant",
            "select p.id,p.company_id from operator_operational_report r join point p on p.id=r.point_id and p.instance_country_id=r.instance_country_id where r.id=:event and r.instance_country_id=:tenant",
            "select p.id,p.company_id from patrol_execution r join patrol_plan pl on pl.id=r.patrol_plan_id and pl.instance_country_id=r.instance_country_id join post po on po.id=pl.post_id and po.instance_country_id=r.instance_country_id join point p on p.id=po.point_id and p.instance_country_id=r.instance_country_id where r.id=:event and r.instance_country_id=:tenant",
            "select p.id,p.company_id from task_execution r join point p on p.id=r.point_id and p.instance_country_id=r.instance_country_id where r.id=:event and r.instance_country_id=:tenant"
        };
        for (int kind = 0; kind < queries.length; kind++) {
            @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery(queries[kind]).setParameter("event", eventId)
                .setParameter("tenant", tenant.instanceCountryId()).getResultList();
            if (rows.isEmpty()) continue;
            if (rows.get(0)[1] == null) throw new NotFoundException("Registro fuera de alcance");
            scope.requireCompany(UUID.fromString(rows.get(0)[1].toString()));
            return new VisibleEvent(UUID.fromString(rows.get(0)[0].toString()), kind);
        }
        throw new NotFoundException("Registro operativo no encontrado");
    }

    /** Solo metadatos: el contenido binario se lee únicamente al servir cada foto. */
    @SuppressWarnings("unchecked")
    private List<Object[]> reliefPhotos(UUID eventId) {
        return em.createNativeQuery("select id,purpose,content_type from operator_relief_evidence where instance_country_id=:tenant and event_id=:event order by created_at,purpose")
            .setParameter("tenant", tenant.instanceCountryId()).setParameter("event", eventId).getResultList();
    }

    @SuppressWarnings("unchecked")
    private Object[] reliefPhoto(UUID eventId, UUID photoId) {
        List<Object[]> rows = em.createNativeQuery("select content_type,content from operator_relief_evidence where instance_country_id=:tenant and event_id=:event and id=:photo")
            .setParameter("tenant", tenant.instanceCountryId()).setParameter("event", eventId).setParameter("photo", photoId).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> patrolPhotos(UUID eventId) {
        return em.createNativeQuery("select id,evidence_json from patrol_checkpoint_execution where instance_country_id=:tenant and patrol_execution_id=:event order by validated_at,id")
            .setParameter("tenant", tenant.instanceCountryId()).setParameter("event", eventId).getResultList();
    }

    @SuppressWarnings("unchecked")
    private String patrolPhotoJson(UUID eventId, UUID photoId) {
        List<String> rows = em.createNativeQuery("select evidence_json from patrol_checkpoint_execution where instance_country_id=:tenant and patrol_execution_id=:event and id=:photo")
            .setParameter("tenant", tenant.instanceCountryId()).setParameter("event", eventId).setParameter("photo", photoId).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Fotos de activos de inventario reportados en el relevo; con photoId solo esa foto. */
    @SuppressWarnings("unchecked")
    private List<Object[]> inventoryPhotos(UUID eventId, UUID photoId) {
        Query query = em.createNativeQuery("""
            select distinct e.id,e.content_type from evidence_object e
            join inventory_report_item_evidence link on link.evidence_id=e.id
            where e.instance_country_id=:tenant and e.event_id=:event and e.target_type='INVENTORY_ASSET' and e.status in ('STORED','ATTACHED')
            """ + (photoId == null ? "" : "and e.id=:photo ") + "order by e.id")
            .setParameter("tenant", tenant.instanceCountryId()).setParameter("event", eventId);
        if (photoId != null) query.setParameter("photo", photoId);
        return query.getResultList();
    }

    private List<Object[]> linkedPhotos(UUID eventId, UUID pointId) {
        return linkedPhotos(eventId, pointId, null);
    }

    /** Con photoId restringe la consulta a esa foto; sin él lista todas las del evento. */
    @SuppressWarnings("unchecked")
    private List<Object[]> linkedPhotos(UUID eventId, UUID pointId, UUID photoId) {
        Query query = em.createNativeQuery("""
            select distinct e.id,e.content_type from evidence_object e
            join task_execution_evidence link on link.evidence_id=e.id
            join task_execution task on task.id=link.task_execution_id and task.instance_country_id=e.instance_country_id
            where e.instance_country_id=:tenant and e.status in ('STORED','ATTACHED') and task.point_id=:point
              and (task.id=:event or task.group_id=:event or task.patrol_execution_id=:event)
            """ + (photoId == null ? "" : "and e.id=:photo ") + "order by e.id")
            .setParameter("tenant", tenant.instanceCountryId()).setParameter("event", eventId).setParameter("point", pointId);
        if (photoId != null) query.setParameter("photo", photoId);
        return query.getResultList();
    }

    private byte[] embeddedPhoto(String json) {
        if (json == null) return null;
        try {
            JsonNode data = mapper.readTree(json);
            String encoded = data.path("photoBase64").asText("");
            return encoded.isBlank() ? null : Base64.getDecoder().decode(encoded);
        } catch (Exception ignored) {
            return null;
        }
    }
}
