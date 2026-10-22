package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.bitacora.*;
import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.consignments.*;
import com.cajamarca.sgi.comando.operations.PostEntity;
import com.cajamarca.sgi.comando.inventory.InventoryService;
import com.cajamarca.sgi.comando.patrols.PatrolCheckpoint;
import com.cajamarca.sgi.comando.storage.StandardReferenceImage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import java.time.Instant;
import java.util.*;

/**
 * Destinos a los que el agente puede enviar una foto desde su Puesto: Hitos de patrulla, evidencias tipo Foto de Consignas
 * vigentes y campos con evidencia de Bitácoras activas. Resuelve el destino y dice si VISINT lo valida.
 */
@ApplicationScoped
public class OperatorTasks {
    /** Destino resuelto. groupScoped: la ejecución se agrupa por turno (Consigna) o por registro (Bitácora); en Patrullas por ronda. */
    public record Target(String type, UUID id, UUID protocolId, int protocolVersion, boolean requiresEvidence, boolean visintEnabled,
                         int standardImageVersion, Double latitude, Double longitude, Double radiusM) {}

    @Inject OperatorPatrols patrols;
    @Inject TenantContext tenant;
    @Inject com.cajamarca.sgi.comando.settings.EvidenceLocationSettings locationSettings;
    @Inject ObjectMapper mapper;
    @Inject InventoryService inventory;
    @Inject EntityManager em;

    /** Foto de referencia de una consigna nueva creada por el agente (solicitud de revisión); destino = id de la solicitud. */
    public static final String CONSIGNMENT_REQUEST = "CONSIGNMENT_REQUEST";
    /** Foto de un reporte operativo (Incidente, Hallazgo, Vulnerabilidad) creado por el agente; destino = id del reporte. */
    public static final String OPERATIONAL_REPORT = "OPERATIONAL_REPORT";

    public Target require(String type, UUID id, PostEntity post) {
        if (type == null || id == null) throw new BadRequestException("Destino obligatorio");
        return switch (type) {
            case StandardReferenceImage.PATROL_CHECKPOINT -> {
                var a = patrols.requireCheckpoint(id, post.id);
                PatrolCheckpoint cp = a.checkpoint();
                yield new Target(type, cp.id, a.protocol().id, a.protocol().versionNo, cp.takesPhoto(), cp.visintEnabled, cp.standardImageVersion,
                    cp.latitude, cp.longitude, cp.radiusM == null ? (double) patrols.defaultRadius() : cp.radiusM);
            }
            case StandardReferenceImage.CONSIGNMENT_EVIDENCE -> {
                ConsignmentEvidence e = ConsignmentEvidence.find("id=?1 and instanceCountryId=?2", id, tenant.instanceCountryId()).firstResult();
                Consignment c = e == null ? null : Consignment.findById(e.consignmentId);
                ConsignmentProtocol p = c == null ? null : ConsignmentProtocol.findById(c.protocolId);
                if (c == null || p == null || !applies(c, p, post)) throw new BadRequestException("La consigna no está vigente en este Puesto");
                if (!"PHOTO".equals(e.evidenceType)) throw new BadRequestException("Esta evidencia de la consigna no es una foto");
                boolean gps = "GPS".equals(c.expectedLocationMode);
                yield new Target(type, e.id, p.id, p.versionNo, true, e.visintEnabled, e.standardImageVersion,
                    gps ? c.expectedLatitude : null, gps ? c.expectedLongitude : null, (double) locationSettings.radiusFor(c.expectedRadiusM, tenant.instanceCountryId()));
            }
            case StandardReferenceImage.LOGBOOK_FIELD -> {
                LogbookProtocolField f = LogbookProtocolField.find("id=?1 and instanceCountryId=?2", id, tenant.instanceCountryId()).firstResult();
                LogbookProtocol p = f == null ? null : LogbookProtocol.findById(f.protocolId);
                if (f == null || p == null || !applies(p, post)) throw new BadRequestException("La bitácora no está activa en este Puesto");
                if (!takesPhoto(f)) throw new BadRequestException("Este campo de la bitácora no lleva foto");
                double[] loc = locationSettings.postReference(tenant.instanceCountryId(), post.id);
                yield new Target(type, f.id, p.id, p.versionNo, true, f.visintEnabled, f.standardImageVersion,
                    loc == null ? null : loc[0], loc == null ? null : loc[1], loc == null ? null : loc[2]);
            }
            case CONSIGNMENT_REQUEST -> {
                // La solicitud la envía antes el outbox de la app (POST /consignment-review-requests); si aún no llegó, 409 para que reintente.
                @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery("select post_id,has_photo from operator_consignment_review_request where id=:id and instance_country_id=:t")
                    .setParameter("id", id).setParameter("t", tenant.instanceCountryId()).getResultList();
                if (rows.isEmpty()) throw new ClientErrorException("La solicitud de consigna " + id + " aún no llega a Comando; reintente después de enviarla", 409);
                if (!post.id.toString().equals(String.valueOf(rows.get(0)[0]))) throw new BadRequestException("La solicitud de consigna no pertenece a este Puesto");
                if (!Boolean.TRUE.equals(rows.get(0)[1])) throw new BadRequestException("La solicitud de consigna no declara foto (hasPhoto=false)");
                double[] loc = locationSettings.postReference(tenant.instanceCountryId(), post.id);
                yield new Target(type, id, id, 1, true, false, 0, loc == null ? null : loc[0], loc == null ? null : loc[1], loc == null ? null : loc[2]);
            }
            case OPERATIONAL_REPORT -> {
                // El reporte lo envía antes el outbox de la app (POST /operator/reports); si aún no llegó, 409 para que reintente.
                @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery("select post_id,photo_count from operator_operational_report where id=:id and instance_country_id=:t")
                    .setParameter("id", id).setParameter("t", tenant.instanceCountryId()).getResultList();
                if (rows.isEmpty()) throw new ClientErrorException("El reporte " + id + " aún no llega a Comando; reintente después de enviarlo", 409);
                if (!post.id.toString().equals(String.valueOf(rows.get(0)[0]))) throw new BadRequestException("El reporte no pertenece a este Puesto");
                if (!(rows.get(0)[1] instanceof Number n) || n.intValue() < 1) throw new BadRequestException("El reporte no declara foto (photoCount=0)");
                double[] loc = locationSettings.postReference(tenant.instanceCountryId(), post.id);
                yield new Target(type, id, id, 1, true, false, 0, loc == null ? null : loc[0], loc == null ? null : loc[1], loc == null ? null : loc[2]);
            }
            case InventoryService.EVIDENCE_TARGET -> {
                if(!inventory.isExpectedAsset(post.id,id)) throw new BadRequestException("El activo no pertenece al inventario vigente del Puesto");
                yield new Target(type,id,id,1,true,false,0,null,null,null);
            }
            default -> throw new BadRequestException("Tipo de destino no soportado: " + type);
        };
    }

    /** Consignas vigentes del Puesto con sus evidencias tipo Foto (una foto por evidencia y turno). */
    public ArrayNode consignmentTasks(PostEntity post) {
        ArrayNode out = mapper.createArrayNode();
        for (Consignment c : Consignment.<Consignment>list("instanceCountryId=?1 and pointId=?2 and status='VIGENTE' order by code", tenant.instanceCountryId(), post.pointId)) {
            ConsignmentProtocol p = ConsignmentProtocol.findById(c.protocolId);
            if (p == null || !applies(c, p, post)) continue;
            List<ConsignmentEvidence> photos = ConsignmentEvidence.list("consignmentId=?1 and evidenceType='PHOTO' order by sortOrder", c.id);
            if (photos.isEmpty()) continue;
            ObjectNode n = out.addObject().put("consignmentId", c.id.toString()).put("code", c.code).put("title", c.title).put("instruction", c.instruction)
                .put("protocolCode", p.code).put("protocolVersion", p.versionNo).put("expectedLocationMode", c.expectedLocationMode);
            // Ubicación esperada (solo en modo GPS): la foto se compara con ella; fuera del radio es un aviso, nunca bloquea.
            if ("GPS".equals(c.expectedLocationMode) && c.expectedLatitude != null && c.expectedLongitude != null)
                n.put("latitude", c.expectedLatitude).put("longitude", c.expectedLongitude).put("radiusM", locationSettings.radiusFor(c.expectedRadiusM, tenant.instanceCountryId()));
            ArrayNode ev = n.putArray("evidences");
            for (ConsignmentEvidence e : photos) {
                ObjectNode x = ev.addObject().put("evidenceId", e.id.toString()).put("name", e.name).put("description", e.description).put("required", e.required)
                    .put("visintEnabled", e.visintEnabled).put("standardImageNotes", e.standardImageNotes);
                images(x, StandardReferenceImage.CONSIGNMENT_EVIDENCE, e.id);
            }
        }
        return out;
    }

    /** Bitácoras activas del Puesto con los campos que llevan foto (cada registro de visitante envía una foto por campo). */
    public ArrayNode logbookTasks(PostEntity post) {
        ArrayNode out = mapper.createArrayNode();
        for (LogbookProtocol p : LogbookProtocol.<LogbookProtocol>list("instanceCountryId=?1 and status='ACTIVO' order by code", tenant.instanceCountryId())) {
            if (!applies(p, post)) continue;
            ObjectNode n = out.addObject().put("protocolId", p.id.toString()).put("code", p.code).put("name", p.name).put("objectType", p.objectType)
                .put("applicationType", p.applicationType).put("protocolVersion", p.versionNo);
            // Las fotos de la Bitácora se comparan con la ubicación del Puesto (solo aviso).
            double[] loc = locationSettings.postReference(tenant.instanceCountryId(), post.id);
            if (loc != null) n.put("latitude", loc[0]).put("longitude", loc[1]).put("radiusM", (int) loc[2]);
            ArrayNode fields = n.putArray("fields");
            for (LogbookProtocolField f : LogbookProtocolField.<LogbookProtocolField>list("protocolId=?1 and (evidenceRequired=true or fieldType='IMAGEN') order by section, sortOrder, name", p.id)) {
                LogbookAccreditation a = LogbookAccreditation.findById(f.accreditationId);
                ObjectNode x = fields.addObject().put("fieldId", f.id.toString()).put("accreditationCode", a == null ? null : a.code).put("section", f.section)
                    .put("name", f.name).put("description", f.description).put("fieldType", f.fieldType).put("required", f.required)
                    .put("visintEnabled", f.visintEnabled).put("standardImageNotes", f.standardImageNotes);
                images(x, StandardReferenceImage.LOGBOOK_FIELD, f.id);
            }
        }
        return out;
    }

    private void images(ObjectNode n, String type, UUID id) {
        ArrayNode std = n.putArray("standardImages");
        StandardReferenceImage.of(type, id).forEach(i -> std.addObject().put("id", i.id.toString()).put("position", i.position));
    }

    static boolean applies(Consignment c, ConsignmentProtocol p, PostEntity post) {
        Instant now = Instant.now();
        if (!"ACTIVO".equals(p.status) || !"VIGENTE".equals(c.status) || !c.pointId.equals(post.pointId)) return false;
        if (c.validityFrom != null && c.validityFrom.isAfter(now)) return false;
        if (c.validityUntil != null && c.validityUntil.isBefore(now)) return false;
        return "POINT".equals(c.scopeType) || post.id.equals(c.postId) || ConsignmentPostScope.count("consignmentId=?1 and postId=?2", c.id, post.id) > 0;
    }

    /** Un campo Imagen es en sí una foto (p. ej. Rostro), aunque no tenga marcado "requiere evidencia"; así lo trata también el registro. */
    static boolean takesPhoto(LogbookProtocolField f) {
        return f.evidenceRequired || "IMAGEN".equals(f.fieldType);
    }

    /** Solo el agente que creó la consigna nueva puede adjuntarle su foto, aunque otro agente comparta el Puesto. */
    public void requireRequestAuthor(UUID requestId, UUID employee) {
        List<?> rows = em.createNativeQuery("select employee_id from operator_consignment_review_request where id=:id and instance_country_id=:t")
            .setParameter("id", requestId).setParameter("t", tenant.instanceCountryId()).getResultList();
        if (!rows.isEmpty() && !employee.toString().equals(String.valueOf(rows.get(0)))) throw new jakarta.ws.rs.ForbiddenException("La solicitud de consigna pertenece a otro agente");
    }

    public void requireReportAuthor(UUID reportId, UUID employee) {
        List<?> rows = em.createNativeQuery("select employee_id from operator_operational_report where id=:id and instance_country_id=:t")
            .setParameter("id", reportId).setParameter("t", tenant.instanceCountryId()).getResultList();
        if (!rows.isEmpty() && !employee.toString().equals(String.valueOf(rows.get(0)))) throw new jakarta.ws.rs.ForbiddenException("El reporte pertenece a otro agente");
    }

    static boolean applies(LogbookProtocol p, PostEntity post) {
        return "ACTIVO".equals(p.status) && LogbookProtocolPostScope.count("protocolId=?1 and postId=?2", p.id, post.id) > 0;
    }
}
