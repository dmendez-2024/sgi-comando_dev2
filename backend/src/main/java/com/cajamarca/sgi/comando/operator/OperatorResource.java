package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.companies.Company;
import com.cajamarca.sgi.comando.security.AppUser;
import com.cajamarca.sgi.comando.assignments.*;
import com.cajamarca.sgi.comando.operations.*;
import com.cajamarca.sgi.comando.ats.AtsPointPackage;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.Instant;
import java.util.*;
import java.security.MessageDigest;
import static com.cajamarca.sgi.comando.operator.ReliefContract.*;

@Path("/api/v1/operator") @Authenticated @Produces(MediaType.APPLICATION_JSON)
public class OperatorResource {
    @Inject TenantContext tenant;
    @Inject SecurityIdentity identity;
    @Inject OperationalScopeService scope;
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;
    @ConfigProperty(name="sgi.operator.relief-uat-enabled",defaultValue="false") boolean enabled;
    @Inject OperatorPatrols patrols;
    @Inject PatrolExecutionService patrolExecutions;
    @Inject TaskEvidenceService taskEvidences;
    @Inject OperatorTasks tasks;
    @Inject ReliefStationReviews stationReviews;

    @org.jboss.resteasy.reactive.server.ServerExceptionMapper
    public Response mapError(WebApplicationException e) { return OperatorErrors.withMessage(e); }
    record AssignmentContext(OperationalAssignmentEntity assignment,ShiftOccurrenceEntity shift,PostEntity post,PointEntity point) {}

    private AppUser actor() {
        if(!enabled) throw new NotFoundException("Integración de relevo UAT deshabilitada");
        AppUser u=AppUser.find("username=?1 and instanceCountryId=?2",identity.getPrincipal().getName(),tenant.instanceCountryId()).firstResult();
        if(u==null || !u.active) throw new ForbiddenException("Usuario no habilitado en esta instancia");
        return u;
    }
    private UUID employee() {
        AppUser u=actor();
        if(!identity.hasRole("AGENTE_SEGURIDAD") && !identity.hasRole("SUPERVISOR_SEGURIDAD")) throw new ForbiddenException("Rol de operador requerido");
        List<?> rows=em.createNativeQuery("select employee_id from operator_employee_binding where instance_country_id=:tenant and username=:user and active=true")
            .setParameter("tenant",tenant.instanceCountryId()).setParameter("user",u.username).getResultList();
        if(rows.size()!=1) throw new ForbiddenException("Vínculo usuario empleado pendiente de configuración");
        return UUID.fromString(rows.get(0).toString());
    }
    private AssignmentContext assignment(UUID id,UUID employee) {
        OperationalAssignmentEntity a=OperationalAssignmentEntity.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();
        if(a==null || "REMOVED".equals(a.status) || !employee.equals(a.effectiveEmployeeId())) throw new ForbiddenException("Asignación no autorizada");
        ShiftOccurrenceEntity s=ShiftOccurrenceEntity.find("id=?1 and instanceCountryId=?2",a.shiftOccurrenceId,tenant.instanceCountryId()).firstResult();
        if(s==null) throw new NotFoundException("Turno no disponible");
        PostEntity post=PostEntity.find("id=?1 and instanceCountryId=?2",s.postId,tenant.instanceCountryId()).firstResult();
        PointEntity point=post==null?null:PointEntity.find("id=?1 and instanceCountryId=?2",post.pointId,tenant.instanceCountryId()).firstResult();
        if(point==null) throw new NotFoundException("Puesto o punto no disponible");
        return new AssignmentContext(a,s,post,point);
    }
    @SuppressWarnings("unchecked")
    private ArrayNode consignments(AssignmentContext c) {
        List<Object[]> rows=em.createNativeQuery("""
          select c.id,c.code,c.title,c.instruction,c.updated_at,p.version_no,p.code,p.name,
                 c.priority,c.application_type,c.application_days_json,c.application_time_from,c.application_time_to,
                 c.scope_type,c.acknowledgment_required,c.confirmation_required,c.evidence_required,c.gps_required,
                 c.observation_required,c.expected_location_mode,
                 (select cc.result from consignment_compliance cc where cc.instance_country_id=c.instance_country_id
                    and cc.assignment_id=:assignment and cc.consignment_id=c.id order by cc.confirmed_at desc limit 1),
                 (select cc.confirmed_at from consignment_compliance cc where cc.instance_country_id=c.instance_country_id
                    and cc.assignment_id=:assignment and cc.consignment_id=c.id order by cc.confirmed_at desc limit 1),
                 (select cc.username from consignment_compliance cc where cc.instance_country_id=c.instance_country_id
                    and cc.assignment_id=:assignment and cc.consignment_id=c.id order by cc.confirmed_at desc limit 1)
          from consignment c join consignment_protocol p on p.id=c.protocol_id and p.instance_country_id=c.instance_country_id
          where c.instance_country_id=:tenant and c.point_id=:point and c.status='VIGENTE' and p.status='ACTIVO'
          and (c.validity_from is null or c.validity_from<=current_timestamp)
          and (c.validity_until is null or c.validity_until>=current_timestamp)
          and (c.scope_type='POINT' or c.post_id=:post or exists
            (select 1 from consignment_post_scope x where x.consignment_id=c.id and x.post_id=:post and x.instance_country_id=c.instance_country_id))
          order by c.code
          """).setParameter("tenant",tenant.instanceCountryId()).setParameter("point",c.point.id).setParameter("post",c.post.id)
            .setParameter("assignment",c.assignment.id).getResultList();
        ArrayNode result=mapper.createArrayNode();
        for(Object[] r:rows) {
            ObjectNode item=result.addObject()
                .put("consignmentId",r[0].toString())
                .put("code",r[1].toString())
                .put("title",r[2].toString())
                .put("instruction",r[3].toString())
                .put("version",r[5].toString()+":"+r[4].toString())
                .put("protocolCode",r[6].toString())
                .put("protocolName",r[7].toString())
                .put("priority",r[8].toString())
                .put("applicationType",r[9].toString())
                .put("applicationDaysJson",r[10]==null?"[]":r[10].toString())
                .put("scopeType",r[13].toString())
                .put("acknowledgmentRequired",Boolean.TRUE.equals(r[14]))
                .put("confirmationRequired",Boolean.TRUE.equals(r[15]))
                .put("evidenceRequired",Boolean.TRUE.equals(r[16]))
                .put("gpsRequired",Boolean.TRUE.equals(r[17]))
                .put("observationRequired",Boolean.TRUE.equals(r[18]))
                .put("expectedLocationMode",r[19].toString());
            if(r[11]!=null) item.put("applicationTimeFrom",r[11].toString());
            if(r[12]!=null) item.put("applicationTimeTo",r[12].toString());
            if(r[20]!=null) item.put("lastComplianceResult",r[20].toString());
            if(r[21]!=null) item.put("lastComplianceAt",r[21].toString());
            if(r[22]!=null) item.put("lastComplianceUsername",r[22].toString());
        }
        return result;
    }
    @SuppressWarnings("unchecked")
    private ArrayNode bitacora(AssignmentContext c) {
        List<Object[]> protocols=em.createNativeQuery("""
          select p.id,p.code,p.name,p.object_type,p.application_type,p.version_no,p.last_published_at
          from logbook_protocol p
          join logbook_protocol_post_scope s on s.protocol_id=p.id and s.instance_country_id=p.instance_country_id
          where p.instance_country_id=:tenant and s.post_id=:post and p.status='ACTIVO'
          order by p.code
          """).setParameter("tenant",tenant.instanceCountryId()).setParameter("post",c.post.id).getResultList();
        ArrayNode result=mapper.createArrayNode();
        for(Object[] row:protocols) {
            UUID protocolId=UUID.fromString(row[0].toString());
            ObjectNode protocol=result.addObject()
                .put("protocolId",protocolId.toString())
                .put("code",row[1].toString())
                .put("name",row[2].toString())
                .put("objectType",row[3].toString())
                .put("applicationType",row[4].toString())
                .put("versionNo",((Number)row[5]).intValue())
                .put("status","ACTIVO");
            if(row[6]!=null) protocol.put("publishedAt",row[6].toString());
            ArrayNode accreditations=protocol.putArray("accreditations");
            List<Object[]> accreditationRows=em.createNativeQuery("""
              select id,code,name,description,identification_logic,verification_logic,
                     auth_preapproval,auth_client,auth_supervisor,capture_manual,capture_qr,
                     capture_barcode,capture_nfc,capture_automatic
              from logbook_accreditation
              where instance_country_id=:tenant and protocol_id=:protocol
              order by code
              """).setParameter("tenant",tenant.instanceCountryId()).setParameter("protocol",protocolId).getResultList();
            for(Object[] accreditationRow:accreditationRows) {
                UUID accreditationId=UUID.fromString(accreditationRow[0].toString());
                ObjectNode accreditation=accreditations.addObject()
                    .put("accreditationId",accreditationId.toString())
                    .put("code",accreditationRow[1].toString())
                    .put("name",accreditationRow[2].toString())
                    .put("description",accreditationRow[3].toString())
                    .put("identificationLogic",accreditationRow[4].toString())
                    .put("verificationLogic",accreditationRow[5].toString())
                    .put("authPreapproval",(Boolean)accreditationRow[6])
                    .put("authClient",(Boolean)accreditationRow[7])
                    .put("authSupervisor",(Boolean)accreditationRow[8])
                    .put("captureManual",(Boolean)accreditationRow[9])
                    .put("captureQr",(Boolean)accreditationRow[10])
                    .put("captureBarcode",(Boolean)accreditationRow[11])
                    .put("captureNfc",(Boolean)accreditationRow[12])
                    .put("captureAutomatic",(Boolean)accreditationRow[13]);
                ArrayNode fields=accreditation.putArray("fields");
                List<Object[]> fieldRows=em.createNativeQuery("""
                  select id,section,sort_order,name,description,field_type,required,evidence_required,
                         capture_mode,standard_image_version,standard_image_notes,visint_enabled
                  from logbook_protocol_field
                  where instance_country_id=:tenant and accreditation_id=:accreditation
                  order by section,sort_order,name
                  """).setParameter("tenant",tenant.instanceCountryId()).setParameter("accreditation",accreditationId).getResultList();
                for(Object[] fieldRow:fieldRows) fields.addObject()
                    .put("fieldId",fieldRow[0].toString())
                    .put("section",fieldRow[1].toString())
                    .put("sortOrder",((Number)fieldRow[2]).intValue())
                    .put("name",fieldRow[3].toString())
                    .put("description",fieldRow[4].toString())
                    .put("fieldType",fieldRow[5].toString())
                    .put("required",(Boolean)fieldRow[6])
                    .put("evidenceRequired",(Boolean)fieldRow[7])
                    .put("captureMode",fieldRow[8].toString())
                    .put("standardImageVersion",((Number)fieldRow[9]).intValue())
                    .put("standardImageNotes",fieldRow[10]==null?"":fieldRow[10].toString())
                    .put("visintEnabled",(Boolean)fieldRow[11]);
            }
        }
        return result;
    }
    @SuppressWarnings("unchecked")
    private ArrayNode patrols(AssignmentContext c) {
        List<Object[]> protocols=em.createNativeQuery("""
          select p.id,p.code,p.name,p.description,p.version_no,p.last_published_at
          from patrol_protocol p
          join patrol_protocol_post_scope s on s.protocol_id=p.id and s.instance_country_id=p.instance_country_id
          where p.instance_country_id=:tenant and s.post_id=:post and p.status='ACTIVO'
          order by p.code
          """).setParameter("tenant",tenant.instanceCountryId()).setParameter("post",c.post.id).getResultList();
        ArrayNode result=mapper.createArrayNode();
        for(Object[] row:protocols) {
            UUID protocolId=UUID.fromString(row[0].toString());
            ObjectNode protocol=result.addObject().put("protocolId",protocolId.toString()).put("code",row[1].toString())
                .put("name",row[2].toString()).put("description",row[3].toString()).put("versionNo",((Number)row[4]).intValue()).put("status","ACTIVO");
            if(row[5]!=null) protocol.put("publishedAt",row[5].toString());
            ArrayNode definitions=protocol.putArray("patrols");
            List<Object[]> patrolRows=em.createNativeQuery("""
              select id,code,name,description,structure_type,schedule_type,sequence_type,window_start,window_end,repetitions
              from patrol_definition where instance_country_id=:tenant and protocol_id=:protocol and status='ACTIVO' order by code
              """).setParameter("tenant",tenant.instanceCountryId()).setParameter("protocol",protocolId).getResultList();
            for(Object[] patrolRow:patrolRows) {
                UUID patrolId=UUID.fromString(patrolRow[0].toString());
                ObjectNode patrol=definitions.addObject().put("patrolId",patrolId.toString()).put("code",patrolRow[1].toString())
                    .put("name",patrolRow[2].toString()).put("description",patrolRow[3].toString())
                    .put("structureType",patrolRow[4].toString()).put("scheduleType",patrolRow[5].toString())
                    .put("repetitions",((Number)patrolRow[9]).intValue());
                if(patrolRow[6]!=null) patrol.put("sequenceType",patrolRow[6].toString());
                if(patrolRow[7]!=null) patrol.put("windowStart",patrolRow[7].toString());
                if(patrolRow[8]!=null) patrol.put("windowEnd",patrolRow[8].toString());
                ArrayNode checkpoints=patrol.putArray("checkpoints");
                List<Object[]> checkpointRows=em.createNativeQuery("""
                  select id,sequence_no,code,name,description,origin_mode,control_type,requires_evidence,latitude,longitude,gps_accuracy_m,standard_image_version,standard_image_notes,visint_enabled,ats_x,ats_y,ats_package_id
                  from patrol_checkpoint where instance_country_id=:tenant and patrol_definition_id=:patrol order by sequence_no
                  """).setParameter("tenant",tenant.instanceCountryId()).setParameter("patrol",patrolId).getResultList();
                for(Object[] cp:checkpointRows) {
                    ObjectNode checkpoint=checkpoints.addObject().put("checkpointId",cp[0].toString()).put("sortOrder",((Number)cp[1]).intValue())
                        .put("code",cp[2].toString()).put("name",cp[3].toString()).put("description",cp[4].toString())
                        .put("originMode",cp[5].toString()).put("controlType",cp[6].toString()).put("requiresEvidence",(Boolean)cp[7])
                        .put("standardImageVersion",((Number)cp[11]).intValue()).put("standardImageNotes",cp[12]==null?"":cp[12].toString()).put("visintEnabled",(Boolean)cp[13]);
                    if(cp[8]!=null) checkpoint.put("latitude",((Number)cp[8]).doubleValue());
                    if(cp[9]!=null) checkpoint.put("longitude",((Number)cp[9]).doubleValue());
                    if(cp[10]!=null) checkpoint.put("gpsAccuracyM",((Number)cp[10]).doubleValue());
                    if(cp[14]!=null) checkpoint.put("atsX",((Number)cp[14]).doubleValue());
                    if(cp[15]!=null) checkpoint.put("atsY",((Number)cp[15]).doubleValue());
                    if(cp[16]!=null) checkpoint.put("atsPackageId",cp[16].toString());
                    ArrayNode rules=checkpoint.putArray("rules");
                    List<Object[]> ruleRows=em.createNativeQuery("select sort_order,rule_type,required,evidence_required from patrol_checkpoint_rule where instance_country_id=:tenant and checkpoint_id=:checkpoint order by sort_order")
                        .setParameter("tenant",tenant.instanceCountryId()).setParameter("checkpoint",UUID.fromString(cp[0].toString())).getResultList();
                    for(Object[] rule:ruleRows) rules.addObject().put("sortOrder",((Number)rule[0]).intValue()).put("ruleType",rule[1].toString()).put("required",(Boolean)rule[2]).put("evidenceRequired",(Boolean)rule[3]);
                }
            }
        }
        return result;
    }
    @SuppressWarnings("unchecked")
    private List<Object[]> employeeProfile(UUID employeeId) {
        return em.createNativeQuery("select full_name,persona_id,role_code from employee_operational_snapshot where instance_country_id=:tenant and employee_id=:employee and employment_status='ACTIVE' limit 1")
            .setParameter("tenant",tenant.instanceCountryId()).setParameter("employee",employeeId).getResultList();
    }
    private ObjectNode context(AssignmentContext c) {
        ObjectNode n=mapper.createObjectNode();
        n.put("assignmentId",c.assignment.id.toString()).put("shiftOccurrenceId",c.shift.id.toString()).put("postId",c.post.id.toString())
            .put("pointId",c.point.id.toString()).put("postName",c.post.name).put("pointName",c.point.name).put("clientName",c.point.clientName)
            .put("incomingEmployeeId",c.assignment.effectiveEmployeeId().toString()).put("plannedAt",c.shift.startsAt.toString())
            .put("shiftStartsAt",c.shift.startsAt.toString()).put("shiftEndsAt",c.shift.endsAt.toString())
            .put("inventoryStatus","PENDING_SOURCE").put("noveltiesStatus","PENDING_SOURCE").put("validationStatus","PENDING_REVIEW");
        List<Object[]> employeeProfile=employeeProfile(c.assignment.effectiveEmployeeId());
        if(!employeeProfile.isEmpty()) {
            Object[] employee=employeeProfile.get(0);
            if(employee[0]!=null) n.put("incomingEmployeeName",employee[0].toString());
            if(employee[1]!=null) n.put("incomingEmployeePersonaId",employee[1].toString());
            if(employee[2]!=null) n.put("incomingEmployeeRole",employee[2].toString());
        }
        @SuppressWarnings("unchecked") List<Object[]> registeredReliefs=em.createNativeQuery("select s.id,s.received_at,r.executed_at,r.unilateral from operator_relief_submission s join relief_event r on r.id=s.id and r.instance_country_id=s.instance_country_id where s.instance_country_id=:tenant and s.assignment_id=:assignment order by s.received_at desc limit 1")
            .setParameter("tenant",tenant.instanceCountryId()).setParameter("assignment",c.assignment.id).getResultList();
        boolean reliefAlreadyRegistered=!registeredReliefs.isEmpty();
        n.put("reliefAlreadyRegistered",reliefAlreadyRegistered);
        if(reliefAlreadyRegistered) {
            Object[] relief=registeredReliefs.get(0);
            n.put("reliefId",relief[0].toString()).put("reliefReceivedAt",relief[1].toString()).put("reliefExecutedAt",relief[2].toString()).put("reliefUnilateral",Boolean.TRUE.equals(relief[3]));
        }
        List<?> previous=em.createNativeQuery("select coalesce(a.actual_employee_id,a.employee_id),e.full_name,s.ends_at from operational_assignment a join shift_occurrence s on s.id=a.shift_occurrence_id and s.instance_country_id=a.instance_country_id left join employee_operational_snapshot e on e.employee_id=coalesce(a.actual_employee_id,a.employee_id) and e.instance_country_id=a.instance_country_id where a.instance_country_id=:t and s.post_id=:p and a.status<>'REMOVED' and s.ends_at<=:start order by s.ends_at desc limit 1")
            .setParameter("t",tenant.instanceCountryId()).setParameter("p",c.post.id).setParameter("start",c.shift.startsAt).getResultList();
        if(!previous.isEmpty()) {
            Object[] row=(Object[])previous.get(0);
            if(row[0]!=null) n.put("expectedOutgoingEmployeeId",row[0].toString());
            if(row[1]!=null) n.put("expectedOutgoingEmployeeName",row[1].toString());
            if(row[2]!=null) n.put("expectedOutgoingShiftEndsAt",row[2].toString());
        }
        n.set("consignments",consignments(c));
        n.set("bitacoraProtocols",bitacora(c));
        n.set("patrolProtocols",patrols(c));
        n.putArray("stationPhotos").add("Vista general del puesto").add("Área de trabajo o garita").add("Acceso principal");
        n.put("configurationVersion",hash(n.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        // Fuera de configurationVersion: activar VISINT en el Puesto no invalida un relevo en curso.
        ObjectNode visint=n.putObject("stationVisint").put("enabled",stationReviews.enabled(tenant.instanceCountryId(),c.post.id));
        ArrayNode images=visint.putArray("standardImageIds");
        for(var i:com.cajamarca.sgi.comando.storage.StandardReferenceImage.of(com.cajamarca.sgi.comando.storage.StandardReferenceImage.POST_CONFIG,c.post.id)) images.add(i.id.toString());
        // Ubicación del Puesto: el relevo envía latitude/longitude/accuracyM y fuera del radio queda como aviso (nunca bloquea).
        double[] loc=patrols.locationSettings().postReference(tenant.instanceCountryId(),c.post.id);
        if(loc!=null) n.putObject("postLocation").put("latitude",loc[0]).put("longitude",loc[1]).put("radiusM",(int)loc[2]);
        return n;
    }
    @POST @Path("/logbook-records") @Consumes(MediaType.APPLICATION_JSON) @Transactional
    public ObjectNode submitLogbookRecord(@HeaderParam("Idempotency-Key") String idempotencyKey, JsonNode event) {
        UUID employee=employee(), recordId=uuid(event,"recordId"), assignmentId=uuid(event,"assignmentId");
        String key=idempotencyKey==null?"":idempotencyKey.trim();
        if(!recordId.toString().equals(key)) throw new BadRequestException("Idempotency-Key debe coincidir con recordId");
        AssignmentContext c=assignment(assignmentId,employee); lock(assignmentId);
        UUID protocolId=uuid(event,"protocolId"), accreditationId=uuid(event,"accreditationId"), correlationId=uuid(event,"correlationId");
        int protocolVersion=event.path("protocolVersion").asInt(0);
        if(protocolVersion<1) throw new BadRequestException("protocolVersion inválida");
        Instant capturedAt=time(event,"capturedAt");
        if(capturedAt.isAfter(Instant.now().plusSeconds(300)) || capturedAt.isBefore(c.shift.startsAt.minusSeconds(43200)) || capturedAt.isAfter(c.shift.endsAt.plusSeconds(43200)))
            throw new BadRequestException("Fecha del registro fuera de la ventana operativa");
        if(!c.point.id.equals(uuid(event,"pointId")) || !c.post.id.equals(uuid(event,"postId"))) throw new BadRequestException("Punto o Puesto no coincide con la asignación");
        @SuppressWarnings("unchecked") List<Object[]> configuration=em.createNativeQuery("""
          select p.version_no,a.id from logbook_protocol p
          join logbook_protocol_post_scope s on s.protocol_id=p.id and s.instance_country_id=p.instance_country_id
          join logbook_accreditation a on a.protocol_id=p.id and a.instance_country_id=p.instance_country_id
          where p.id=:protocol and a.id=:accreditation and p.instance_country_id=:tenant
            and p.status='ACTIVO' and s.post_id=:post
          """).setParameter("protocol",protocolId).setParameter("accreditation",accreditationId)
            .setParameter("tenant",tenant.instanceCountryId()).setParameter("post",c.post.id).getResultList();
        if(configuration.isEmpty() || ((Number)configuration.get(0)[0]).intValue()!=protocolVersion) throw new ClientErrorException("El Protocolo de Bitácora cambió; actualiza antes de registrar",409);
        JsonNode values=event.path("fieldValues");
        if(!values.isObject()) throw new BadRequestException("fieldValues debe ser un objeto");
        @SuppressWarnings("unchecked") List<Object[]> fields=em.createNativeQuery("select id,required,evidence_required,field_type from logbook_protocol_field where instance_country_id=:tenant and accreditation_id=:accreditation")
            .setParameter("tenant",tenant.instanceCountryId()).setParameter("accreditation",accreditationId).getResultList();
        Set<String> allowedFields=new HashSet<>(), requiredEvidence=new HashSet<>();
        for(Object[] field:fields) {
            String fieldId=field[0].toString(); allowedFields.add(fieldId);
            boolean evidenceField=Boolean.TRUE.equals(field[2]) || "IMAGEN".equals(field[3].toString());
            if(Boolean.TRUE.equals(field[1]) && !evidenceField && (!values.has(fieldId) || values.path(fieldId).asText("").isBlank())) throw new BadRequestException("Falta un campo obligatorio de la acreditación");
            if(Boolean.TRUE.equals(field[1]) && evidenceField) requiredEvidence.add(fieldId);
        }
        Iterator<String> names=values.fieldNames(); while(names.hasNext()) if(!allowedFields.contains(names.next())) throw new BadRequestException("Campo no pertenece a la acreditación vigente");
        JsonNode evidence=event.path("evidenceFieldIds"); if(!evidence.isArray()) throw new BadRequestException("evidenceFieldIds debe ser un arreglo");
        Set<String> evidenceIds=new HashSet<>(); for(JsonNode item:evidence) { String id=item.asText(); if(!allowedFields.contains(id) || !evidenceIds.add(id)) throw new BadRequestException("Evidencia inválida o duplicada"); }
        if(!evidenceIds.containsAll(requiredEvidence)) throw new BadRequestException("Faltan evidencias obligatorias");
        String comments=event.path("comments").asText("").trim(); if(comments.length()>500) throw new BadRequestException("Comentarios superan 500 caracteres");
        String payloadHash=hash(canonical(event).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        @SuppressWarnings("unchecked") List<Object[]> old=em.createNativeQuery("select payload_hash,username from operator_logbook_record where instance_country_id=:tenant and id=:id")
            .setParameter("tenant",tenant.instanceCountryId()).setParameter("id",recordId).getResultList();
        if(!old.isEmpty()) {
            Object[] row=old.get(0); if(!identity.getPrincipal().getName().equals(row[1].toString())) throw new ForbiddenException();
            if(!payloadHash.equals(row[0].toString())) throw new ClientErrorException("recordId reutilizado con contenido diferente",409);
            return mapper.createObjectNode().put("recordId",recordId.toString()).put("status","REGISTERED").put("idempotentReplay",true);
        }
        em.createNativeQuery("insert into operator_logbook_record(id,instance_country_id,assignment_id,point_id,post_id,employee_id,username,protocol_id,protocol_version,accreditation_id,captured_at,received_at,status,comments,field_values_json,evidence_refs_json,payload_hash,correlation_id) values(:id,:tenant,:assignment,:point,:post,:employee,:username,:protocol,:version,:accreditation,:captured,current_timestamp,'REGISTERED',:comments,:fields,:evidence,:hash,:correlation)")
            .setParameter("id",recordId).setParameter("tenant",tenant.instanceCountryId()).setParameter("assignment",assignmentId)
            .setParameter("point",c.point.id).setParameter("post",c.post.id).setParameter("employee",employee).setParameter("username",identity.getPrincipal().getName())
            .setParameter("protocol",protocolId).setParameter("version",protocolVersion).setParameter("accreditation",accreditationId).setParameter("captured",capturedAt)
            .setParameter("comments",comments.isBlank()?null:comments).setParameter("fields",values.toString()).setParameter("evidence",evidence.toString())
            .setParameter("hash",payloadHash).setParameter("correlation",correlationId).executeUpdate();
        return mapper.createObjectNode().put("recordId",recordId.toString()).put("status","REGISTERED").put("receivedAt",Instant.now().toString()).put("idempotentReplay",false);
    }
    @GET @Path("/logbook-records")
    public ArrayNode listLogbookRecords() {
        actor(); boolean operator=identity.hasRole("AGENTE_SEGURIDAD"); UUID operatorEmployee=operator?employee():null;
        @SuppressWarnings("unchecked") List<Object[]> rows=em.createNativeQuery("""
          select r.id,p.object_type,a.name,r.field_values_json,r.captured_at,r.status,r.comments,r.employee_id,e.full_name,
                 pt.client_name,pt.city,pt.name,po.name,c.code,c.name,pt.company_id,lp.code,lp.name,r.protocol_version,r.username
          from operator_logbook_record r
          join logbook_protocol lp on lp.id=r.protocol_id and lp.instance_country_id=r.instance_country_id
          join logbook_accreditation a on a.id=r.accreditation_id and a.instance_country_id=r.instance_country_id
          join post po on po.id=r.post_id and po.instance_country_id=r.instance_country_id
          join point pt on pt.id=r.point_id and pt.instance_country_id=r.instance_country_id
          left join company c on c.id=pt.company_id and c.instance_country_id=r.instance_country_id
          left join employee_operational_snapshot e on e.employee_id=r.employee_id and e.instance_country_id=r.instance_country_id
          join logbook_protocol p on p.id=r.protocol_id and p.instance_country_id=r.instance_country_id
          where r.instance_country_id=:tenant order by r.captured_at desc limit 500
          """).setParameter("tenant",tenant.instanceCountryId()).getResultList();
        ArrayNode result=mapper.createArrayNode();
        for(Object[] row:rows) {
            if(operator && !operatorEmployee.toString().equals(row[7].toString())) continue;
            UUID companyId=row[15]==null?null:UUID.fromString(row[15].toString()); if(!operator && !scope.canAccessCompany(companyId)) continue;
            JsonNode fieldValues; try { fieldValues=mapper.readTree(row[3].toString()); } catch(Exception ex) { fieldValues=mapper.createObjectNode(); }
            String identifier=""; Iterator<JsonNode> values=fieldValues.elements(); while(values.hasNext() && identifier.isBlank()) identifier=values.next().asText("");
            String objectType=row[1].toString().toUpperCase(Locale.ROOT); String type=objectType.contains("VEH")?"VEHICLE":objectType.contains("CONT")?"CONTAINER":"PERSON";
            ObjectNode item=result.addObject().put("id",row[0].toString()).put("type",type).put("identifier",identifier)
                .put("label",row[2].toString()+(identifier.isBlank()?"":" · "+identifier)).put("client",row[9]==null?"—":row[9].toString())
                .put("company",row[14]==null?"—":row[14].toString()).put("companyCode",row[13]==null?"":row[13].toString())
                .put("city",row[10]==null?"—":row[10].toString()).put("point",row[11].toString()+" · "+row[12].toString())
                .put("region","").put("zone","").put("status",row[5].toString()).put("at",row[4].toString())
                .put("detailTag",row[2].toString()).put("subtitle",identifier).put("submittedBy",row[8]==null?row[19].toString():row[8].toString())
                .put("protocolCode",row[16].toString()).put("protocolName",row[17].toString()).put("protocolVersion",((Number)row[18]).intValue())
                .put("comments",row[6]==null?"":row[6].toString());
            item.set("fieldValues",fieldValues); item.putArray("timeline").addObject().put("id","received").put("at",row[4].toString()).put("title","Registro recibido desde SGI Operador").put("place",row[11].toString()+" · "+row[12].toString());
        }
        return result;
    }
    @POST @Path("/patrol-executions") @Consumes(MediaType.APPLICATION_JSON) @Transactional
    public ObjectNode submitPatrol(JsonNode event) {
        UUID employee=employee(), assignmentId=uuid(event,"assignmentId"), executionId=uuid(event,"executionId"), patrolId=uuid(event,"patrolId");
        AssignmentContext c=assignment(assignmentId,employee); lock(assignmentId);
        List<?> allowed=em.createNativeQuery("""
          select d.id from patrol_definition d join patrol_protocol p on p.id=d.protocol_id and p.instance_country_id=d.instance_country_id
          join patrol_protocol_post_scope s on s.protocol_id=p.id and s.instance_country_id=p.instance_country_id
          where d.id=:patrol and d.instance_country_id=:tenant and d.status='ACTIVO' and p.status='ACTIVO' and s.post_id=:post
          """).setParameter("patrol",patrolId).setParameter("tenant",tenant.instanceCountryId()).setParameter("post",c.post.id).getResultList();
        if(allowed.isEmpty()) throw new BadRequestException("Patrulla no activa para el puesto asignado");
        List<?> plans=em.createNativeQuery("select id from patrol_plan where instance_country_id=:tenant and patrol_definition_id=:patrol and post_id=:post and active=true limit 1")
            .setParameter("tenant",tenant.instanceCountryId()).setParameter("patrol",patrolId).setParameter("post",c.post.id).getResultList();
        UUID planId;
        if(plans.isEmpty()) {
            planId=UUID.randomUUID();
            em.createNativeQuery("insert into patrol_plan(id,instance_country_id,patrol_definition_id,post_id,schedule_type,schedule_json,active,created_at,updated_at) select :id,:tenant,id,:post,schedule_type,'{\"source\":\"SGI_OPR_UAT\"}',true,current_timestamp,current_timestamp from patrol_definition where id=:patrol")
                .setParameter("id",planId).setParameter("tenant",tenant.instanceCountryId()).setParameter("post",c.post.id).setParameter("patrol",patrolId).executeUpdate();
        } else planId=UUID.fromString(plans.get(0).toString());
        String action=event.path("action").asText("").trim().toUpperCase(Locale.ROOT);
        List<?> old=em.createNativeQuery("select id,started_at,finished_at,result from patrol_execution where id=:id and instance_country_id=:tenant")
            .setParameter("id",executionId).setParameter("tenant",tenant.instanceCountryId()).getResultList();
        if("START".equals(action)) {
            if(!old.isEmpty()) return executionState((Object[])old.get(0));
            List<?> active=em.createNativeQuery("""
              select pe.id,pe.started_at,pe.finished_at,pe.result from patrol_execution pe
              join patrol_plan pp on pp.id=pe.patrol_plan_id and pp.instance_country_id=pe.instance_country_id
              where pe.instance_country_id=:tenant and pe.employee_id=:employee and pe.assignment_id=:assignment and pp.patrol_definition_id=:patrol
                and pp.post_id=:post and pe.finished_at is null order by pe.started_at desc limit 1
              """).setParameter("tenant",tenant.instanceCountryId()).setParameter("employee",employee).setParameter("assignment",assignmentId)
                .setParameter("patrol",patrolId).setParameter("post",c.post.id).getResultList();
            if(!active.isEmpty()) return executionState((Object[])active.get(0));
            Instant started=Instant.now();
            if(started.isBefore(c.shift.startsAt.minusSeconds(43200)) || started.isAfter(c.shift.endsAt.plusSeconds(43200))) throw new BadRequestException("Inicio de patrulla fuera de la ventana UAT del turno");
            em.createNativeQuery("insert into patrol_execution(id,instance_country_id,patrol_plan_id,assignment_id,employee_id,started_at,finished_at,result,created_at,updated_at) values(:id,:tenant,:plan,:assignment,:employee,:started,null,null,current_timestamp,current_timestamp)")
                .setParameter("id",executionId).setParameter("tenant",tenant.instanceCountryId()).setParameter("plan",planId).setParameter("assignment",assignmentId).setParameter("employee",employee).setParameter("started",started).executeUpdate();
            return mapper.createObjectNode().put("executionId",executionId.toString()).put("status","IN_PROGRESS").put("startedAt",started.toString()).put("completedCheckpoints",0);
        }
        boolean finishing="FINISH".equals(action);
        if(!finishing && !old.isEmpty()) return executionState((Object[])old.get(0));
        if(finishing && old.isEmpty()) throw new NotFoundException("La ejecución de patrulla no fue iniciada");
        Instant started=finishing?Instant.parse(((Object[])old.get(0))[1].toString()):time(event,"startedAt"), finished=Instant.now();
        if(!finishing) finished=time(event,"finishedAt");
        if(finished.isBefore(started) || started.isBefore(c.shift.startsAt.minusSeconds(43200)) || finished.isAfter(c.shift.endsAt.plusSeconds(43200))) throw new BadRequestException("Fechas de patrulla fuera de la ventana UAT del turno");
        JsonNode results=event.path("checkpointResults"); if(!results.isArray()) throw new BadRequestException("Resultados de hitos obligatorios");
        Set<UUID> seen=new HashSet<>(); boolean complete=true;
        for(JsonNode item:results) {
            UUID checkpointId=uuid(item,"checkpointId"); if(!seen.add(checkpointId)) throw new BadRequestException("Hito duplicado");
            List<?> valid=em.createNativeQuery("select id from patrol_checkpoint where id=:id and instance_country_id=:tenant and patrol_definition_id=:patrol")
                .setParameter("id",checkpointId).setParameter("tenant",tenant.instanceCountryId()).setParameter("patrol",patrolId).getResultList();
            if(valid.isEmpty()) throw new BadRequestException("Hito no pertenece a la patrulla");
            String result=text(item,"result"); if(!Set.of("CUMPLIDO","NO_CUMPLIDO").contains(result)) throw new BadRequestException("Resultado de hito inválido");
            if(!"CUMPLIDO".equals(result)) complete=false;
        }
        Number required=(Number)em.createNativeQuery("select count(*) from patrol_checkpoint where instance_country_id=:tenant and patrol_definition_id=:patrol")
            .setParameter("tenant",tenant.instanceCountryId()).setParameter("patrol",patrolId).getSingleResult();
        if(required.intValue()!=seen.size()) complete=false;
        String finalResult=complete?"COMPLETA":"INCOMPLETA";
        if(finishing) {
            int updated=em.createNativeQuery("update patrol_execution set finished_at=:finished,result=:result,updated_at=current_timestamp where id=:id and instance_country_id=:tenant and assignment_id=:assignment and employee_id=:employee and patrol_plan_id=:plan and finished_at is null")
                .setParameter("finished",finished).setParameter("result",finalResult).setParameter("id",executionId).setParameter("tenant",tenant.instanceCountryId()).setParameter("assignment",assignmentId).setParameter("employee",employee).setParameter("plan",planId).executeUpdate();
            if(updated!=1) throw new ClientErrorException("La patrulla ya fue finalizada o no corresponde al contexto activo",409);
        } else em.createNativeQuery("insert into patrol_execution(id,instance_country_id,patrol_plan_id,assignment_id,employee_id,started_at,finished_at,result,created_at,updated_at) values(:id,:tenant,:plan,:assignment,:employee,:started,:finished,:result,current_timestamp,current_timestamp)")
            .setParameter("id",executionId).setParameter("tenant",tenant.instanceCountryId()).setParameter("plan",planId).setParameter("assignment",assignmentId).setParameter("employee",employee).setParameter("started",started).setParameter("finished",finished).setParameter("result",finalResult).executeUpdate();
        for(JsonNode item:results) em.createNativeQuery("insert into patrol_checkpoint_execution(id,instance_country_id,patrol_execution_id,checkpoint_id,result,validated_at,evidence_json,created_at) values(:id,:tenant,:execution,:checkpoint,:result,:validated,:evidence,current_timestamp)")
            .setParameter("id",UUID.randomUUID()).setParameter("tenant",tenant.instanceCountryId()).setParameter("execution",executionId).setParameter("checkpoint",uuid(item,"checkpointId"))
            .setParameter("result",text(item,"result")).setParameter("validated",time(item,"validatedAt")).setParameter("evidence",item.path("evidence").toString()).executeUpdate();
        ObjectNode response=mapper.createObjectNode().put("executionId",executionId.toString()).put("status","RECEIVED").put("result",finalResult); response.put("persistedCheckpointCount",seen.size()); return response;
    }
    private ObjectNode executionState(Object[] row) {
        ObjectNode state=mapper.createObjectNode().put("executionId",row[0].toString()).put("startedAt",row[1].toString());
        if(row[2]==null) return state.put("status","IN_PROGRESS");
        return state.put("status","COMPLETED").put("finishedAt",row[2].toString()).put("result",row[3]==null?"INCOMPLETA":row[3].toString());
    }
    @POST @Path("/consignment-compliances") @Consumes(MediaType.APPLICATION_JSON) @Transactional
    public ObjectNode submitConsignmentCompliance(JsonNode event) {
        UUID employee=employee(), assignmentId=uuid(event,"assignmentId"), executionId=uuid(event,"executionId"), consignmentId=uuid(event,"consignmentId");
        AssignmentContext c=assignment(assignmentId,employee); lock(assignmentId);
        String username=identity.getPrincipal().getName();
        String result=text(event,"result").trim().toUpperCase(Locale.ROOT);
        if(!Set.of("CUMPLIDA","NO_CUMPLIDA","CON_NOVEDAD","NO_APLICA").contains(result)) throw new BadRequestException("Resultado de Consigna inválido");
        String comment=event.path("comment").asText("").trim();
        if(comment.length()>2000) throw new BadRequestException("La observación supera 2000 caracteres");
        int photoCount=event.path("photoCount").asInt(0);
        if(photoCount<0 || photoCount>5) throw new BadRequestException("Cantidad de fotografías inválida");
        String payloadHash=hash(canonical(event).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        List<?> existing=em.createNativeQuery("select assignment_id,employee_id,consignment_id,payload_hash,result,confirmed_at from consignment_compliance where id=:id and instance_country_id=:tenant")
            .setParameter("id",executionId).setParameter("tenant",tenant.instanceCountryId()).getResultList();
        if(!existing.isEmpty()) {
            Object[] old=(Object[])existing.get(0);
            if(!assignmentId.toString().equals(old[0].toString()) || !employee.toString().equals(old[1].toString()) || !consignmentId.toString().equals(old[2].toString())) throw new ForbiddenException();
            if(!payloadHash.equals(old[3].toString())) throw new ClientErrorException("Identificador reutilizado con contenido diferente",409);
            return mapper.createObjectNode().put("executionId",executionId.toString()).put("status","RECEIVED").put("result",old[4].toString()).put("confirmedAt",old[5].toString());
        }
        @SuppressWarnings("unchecked") List<Object[]> allowed=em.createNativeQuery("""
          select c.protocol_id,c.evidence_required,c.gps_required,c.observation_required
          from consignment c join consignment_protocol p on p.id=c.protocol_id and p.instance_country_id=c.instance_country_id
          where c.id=:consignment and c.instance_country_id=:tenant and c.point_id=:point and c.status='VIGENTE' and p.status='ACTIVO'
            and (c.validity_from is null or c.validity_from<=current_timestamp)
            and (c.validity_until is null or c.validity_until>=current_timestamp)
            and (c.scope_type='POINT' or c.post_id=:post or exists
              (select 1 from consignment_post_scope x where x.consignment_id=c.id and x.post_id=:post and x.instance_country_id=c.instance_country_id))
          """).setParameter("consignment",consignmentId).setParameter("tenant",tenant.instanceCountryId()).setParameter("point",c.point.id).setParameter("post",c.post.id).getResultList();
        if(allowed.isEmpty()) throw new BadRequestException("La Consigna no está vigente o no aplica al puesto asignado");
        Object[] rules=allowed.get(0);
        if("CUMPLIDA".equals(result) && Boolean.TRUE.equals(rules[1]) && photoCount==0) throw new BadRequestException("La Consigna requiere evidencia fotográfica");
        if("CUMPLIDA".equals(result) && Boolean.TRUE.equals(rules[3]) && comment.isBlank()) throw new BadRequestException("La Consigna requiere una observación");
        List<?> alreadyConfirmed=em.createNativeQuery("select id,result,confirmed_at from consignment_compliance where instance_country_id=:tenant and assignment_id=:assignment and consignment_id=:consignment")
            .setParameter("tenant",tenant.instanceCountryId()).setParameter("assignment",assignmentId).setParameter("consignment",consignmentId).getResultList();
        if(!alreadyConfirmed.isEmpty()) {
            Object[] old=(Object[])alreadyConfirmed.get(0);
            return mapper.createObjectNode().put("executionId",old[0].toString()).put("status","RECEIVED").put("result",old[1].toString()).put("confirmedAt",old[2].toString());
        }
        Instant confirmedAt=Instant.now();
        em.createNativeQuery("insert into consignment_compliance(id,instance_country_id,protocol_id,consignment_id,assignment_id,employee_id,username,result,comment,photo_count,gps_required,payload_hash,payload_json,confirmed_at,created_at) values(:id,:tenant,:protocol,:consignment,:assignment,:employee,:username,:result,:comment,:photos,:gps,:hash,:payload,:confirmed,current_timestamp)")
            .setParameter("id",executionId).setParameter("tenant",tenant.instanceCountryId()).setParameter("protocol",UUID.fromString(rules[0].toString()))
            .setParameter("consignment",consignmentId).setParameter("assignment",assignmentId).setParameter("employee",employee).setParameter("username",username)
            .setParameter("result",result).setParameter("comment",comment).setParameter("photos",photoCount).setParameter("gps",Boolean.TRUE.equals(rules[2]))
            .setParameter("hash",payloadHash).setParameter("payload",event.toString()).setParameter("confirmed",confirmedAt).executeUpdate();
        return mapper.createObjectNode().put("executionId",executionId.toString()).put("status","RECEIVED").put("result",result).put("confirmedAt",confirmedAt.toString());
    }
    @GET @Path("/patrol-executions/current")
    public ObjectNode currentPatrol(@QueryParam("assignmentId") UUID assignmentId,@QueryParam("patrolId") UUID patrolId) {
        UUID employee=employee(); AssignmentContext c=assignment(assignmentId,employee);
        if(patrolId==null) throw new BadRequestException("patrolId obligatorio");
        @SuppressWarnings("unchecked") List<Object[]> rows=em.createNativeQuery("""
          select pe.id,pe.started_at,pe.finished_at,pe.result,
                 (select count(*) from patrol_checkpoint_execution pce where pce.instance_country_id=pe.instance_country_id and pce.patrol_execution_id=pe.id)
          from patrol_execution pe join patrol_plan pp on pp.id=pe.patrol_plan_id and pp.instance_country_id=pe.instance_country_id
          where pe.instance_country_id=:tenant and pe.assignment_id=:assignment and pe.employee_id=:employee and pp.patrol_definition_id=:patrol and pp.post_id=:post
          order by pe.started_at desc limit 1
          """).setParameter("tenant",tenant.instanceCountryId()).setParameter("assignment",assignmentId).setParameter("employee",employee).setParameter("patrol",patrolId)
            .setParameter("post",c.post.id).getResultList();
        if(rows.isEmpty()) return mapper.createObjectNode().put("status","NOT_STARTED").put("completedCheckpoints",0);
        Object[] row=rows.get(0); return executionState(row).put("completedCheckpoints",((Number)row[4]).intValue());
    }
    @GET @Path("/patrol-executions")
    public ArrayNode listPatrolExecutions() {
        actor();
        boolean operator=identity.hasRole("AGENTE_SEGURIDAD"); UUID employee=operator?employee():null;
        @SuppressWarnings("unchecked") List<Object[]> rows=em.createNativeQuery("""
          select pe.id,d.code,d.name,pr.code,pr.name,pe.employee_id,pe.started_at,pe.finished_at,pe.result,
                 e.full_name,pt.client_name,pt.city,pt.name,po.name,c.code,c.name,pt.company_id,
                 count(pce.id),(select count(*) from patrol_checkpoint pc where pc.instance_country_id=pe.instance_country_id and pc.patrol_definition_id=d.id)
          from patrol_execution pe
          join patrol_plan pl on pl.id=pe.patrol_plan_id and pl.instance_country_id=pe.instance_country_id
          join patrol_definition d on d.id=pl.patrol_definition_id and d.instance_country_id=pe.instance_country_id
          join patrol_protocol pr on pr.id=d.protocol_id and pr.instance_country_id=pe.instance_country_id
          join post po on po.id=pl.post_id and po.instance_country_id=pe.instance_country_id
          join point pt on pt.id=po.point_id and pt.instance_country_id=pe.instance_country_id
          left join company c on c.id=pt.company_id and c.instance_country_id=pe.instance_country_id
          left join employee_operational_snapshot e on e.employee_id=pe.employee_id and e.instance_country_id=pe.instance_country_id
          left join patrol_checkpoint_execution pce on pce.patrol_execution_id=pe.id and pce.instance_country_id=pe.instance_country_id
          where pe.instance_country_id=:tenant
          group by pe.id,d.id,d.code,d.name,pr.code,pr.name,pe.employee_id,pe.started_at,pe.finished_at,pe.result,
                   e.full_name,pt.client_name,pt.city,pt.name,po.name,c.code,c.name,pt.company_id
          order by pe.started_at desc
          """).setParameter("tenant",tenant.instanceCountryId()).setMaxResults(200).getResultList();
        ArrayNode result=mapper.createArrayNode();
        for(Object[] row:rows) {
            if(operator && !employee.toString().equals(row[5].toString())) continue;
            UUID companyId=row[16]==null?null:UUID.fromString(row[16].toString());
            if(!operator && !scope.canAccessCompany(companyId)) continue;
            UUID executionId=UUID.fromString(row[0].toString());
            ObjectNode item=result.addObject().put("id",executionId.toString()).put("patrolCode",row[1].toString()).put("patrolName",row[2].toString())
                .put("protocolCode",row[3].toString()).put("protocolName",row[4].toString()).put("employeeId",row[5].toString())
                .put("startedAt",row[6].toString()).put("finishedAt",row[7]==null?"":row[7].toString()).put("result",row[8]==null?"EN_CURSO":row[8].toString())
                .put("submittedBy",row[9]==null?"Operador":row[9].toString()).put("clientName",row[10].toString()).put("city",row[11].toString())
                .put("pointName",row[12].toString()).put("postName",row[13].toString()).put("companyCode",row[14]==null?"":row[14].toString())
                .put("companyName",row[15]==null?"—":row[15].toString()).put("completedCheckpoints",((Number)row[17]).intValue()).put("totalCheckpoints",((Number)row[18]).intValue());
            ArrayNode events=item.putArray("checkpointEvents");
            @SuppressWarnings("unchecked") List<Object[]> checkpoints=em.createNativeQuery("""
              select pc.name,pce.result,pce.validated_at
              from patrol_checkpoint_execution pce join patrol_checkpoint pc on pc.id=pce.checkpoint_id and pc.instance_country_id=pce.instance_country_id
              where pce.instance_country_id=:tenant and pce.patrol_execution_id=:execution order by pc.sequence_no
              """).setParameter("tenant",tenant.instanceCountryId()).setParameter("execution",executionId).getResultList();
            for(Object[] checkpoint:checkpoints) events.addObject().put("name",checkpoint[0].toString()).put("result",checkpoint[1].toString()).put("validatedAt",checkpoint[2].toString());
        }
        return result;
    }
    @GET @Path("/runtime")
    public ObjectNode runtime(@QueryParam("assignmentId") UUID assignmentId,@QueryParam("employeeId") UUID requestedEmployee) {
        UUID employee=employee();
        if(requestedEmployee!=null && !requestedEmployee.equals(employee)) throw new ForbiddenException("Empleado incorrecto");
        ObjectNode response=mapper.createObjectNode().put("instanceCountryId",tenant.instanceCountryId().toString()).put("employeeId",employee.toString());
        Instant now=Instant.now();
        if(assignmentId!=null) {
            AssignmentContext selected=assignment(assignmentId,employee);
            var candidate=new OperatorAssignmentWindow.Candidate<>(selected,selected.shift.startsAt,selected.shift.endsAt);
            if(OperatorAssignmentWindow.select(List.of(candidate),now).isEmpty()) throw new ForbiddenException("Asignación fuera de la ventana operativa");
            response.set("relief",context(selected));
            // Tareas con foto del Puesto: Hitos de patrulla, evidencias de Consigna y campos de Bitácora.
            response.set("patrols",patrols.runtime(selected.post.id));
            response.set("consignmentTasks",tasks.consignmentTasks(selected.post));
            response.set("logbookTasks",tasks.logbookTasks(selected.post));
            return response;
        }
        ArrayNode choices=response.putArray("assignments");
        @SuppressWarnings("unchecked")
        List<Object> assignmentIds=em.createNativeQuery("""
          select a.id from operational_assignment a
          join shift_occurrence s on s.id=a.shift_occurrence_id and s.instance_country_id=a.instance_country_id
          where a.instance_country_id=:tenant and a.status<>'REMOVED'
          and coalesce(a.actual_employee_id,a.employee_id)=:employee
          and s.ends_at>:now and s.starts_at<=:latestStart
          order by s.starts_at,a.id
          """).setParameter("tenant",tenant.instanceCountryId()).setParameter("employee",employee)
            .setParameter("now",now).setParameter("latestStart",now.plus(OperatorAssignmentWindow.EARLY_ENTRY)).getResultList();
        List<AssignmentContext> contexts=assignmentIds.stream().map(id->assignment(UUID.fromString(id.toString()),employee)).toList();
        List<OperatorAssignmentWindow.Candidate<AssignmentContext>> candidates=contexts.stream()
            .map(c->new OperatorAssignmentWindow.Candidate<>(c,c.shift.startsAt,c.shift.endsAt)).toList();
        for(var c:OperatorAssignmentWindow.select(candidates,now)) choices.addObject()
            .put("assignmentId",c.assignment.id.toString()).put("postName",c.post.name)
            .put("startsAt",c.shift.startsAt.toString()).put("endsAt",c.shift.endsAt.toString())
            .put("accessMode",now.isBefore(c.shift.startsAt)?"EARLY_ENTRY":"CURRENT_SHIFT");
        return response;
    }
    @GET @Path("/patrol-map") @Produces({"image/png","image/jpeg","image/webp"})
    public Response patrolMap(@QueryParam("assignmentId") UUID assignmentId) {
        if(assignmentId==null) throw new BadRequestException("assignmentId obligatorio");
        UUID employee=employee();
        AssignmentContext c=assignment(assignmentId,employee);
        AtsPointPackage pkg=AtsPointPackage.find("instanceCountryId=?1 and pointId=?2 and current=true",tenant.instanceCountryId(),c.point.id).firstResult();
        if(pkg==null || pkg.planImage==null || pkg.planImage.length==0) throw new NotFoundException("El punto no tiene un plano ATS vigente");
        return Response.ok(pkg.planImage,pkg.planContentType)
            .header(HttpHeaders.CACHE_CONTROL,"no-store")
            .header("X-ATS-Revision",pkg.revisionNo)
            .header("X-ATS-Level",pkg.planLevelName==null?"":pkg.planLevelName)
            .build();
    }
    static String hash(byte[] bytes) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); } catch(Exception e) { throw new IllegalStateException(e); } }
    private void lock(UUID assignmentId) {
        em.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:key,0))").setParameter("key",tenant.instanceCountryId()+":"+assignmentId).getSingleResult();
    }
    @PUT @Path("/relief-evidence/{eventId}/{purpose}") @Consumes("image/jpeg") @Transactional
    public Map<String,Object> upload(@PathParam("eventId") UUID eventId,@PathParam("purpose") String purpose,@QueryParam("assignmentId") UUID assignmentId,byte[] bytes) {
        UUID employee=employee(); assignment(assignmentId,employee);
        if(!PURPOSES.contains(purpose) || bytes==null || bytes.length<4 || bytes.length>5242880 || (bytes[0]&255)!=255 || (bytes[1]&255)!=216 || (bytes[bytes.length-2]&255)!=255 || (bytes[bytes.length-1]&255)!=217) throw new BadRequestException("Fotografía JPEG inválida o superior a 5 MB");
        lock(assignmentId); String digest=hash(bytes); String user=identity.getPrincipal().getName();
        List<?> existing=em.createNativeQuery("select id,sha256,username,assignment_id from operator_relief_evidence where instance_country_id=:t and event_id=:e and purpose=:p")
            .setParameter("t",tenant.instanceCountryId()).setParameter("e",eventId).setParameter("p",purpose).getResultList();
        if(!existing.isEmpty()) { Object[] row=(Object[])existing.get(0); if(!user.equals(row[2].toString()) || !assignmentId.toString().equals(row[3].toString())) throw new ForbiddenException();
            if(!digest.equals(row[1].toString())) throw new ClientErrorException("La evidencia ya fue cargada con otro contenido",409);
            return Map.of("evidenceId",row[0].toString(),"sha256",digest); }
        if(!em.createNativeQuery("select id from relief_event where id=:id").setParameter("id",eventId).getResultList().isEmpty()) throw new ClientErrorException("El relevo ya fue recibido",409);
        UUID id=UUID.randomUUID();
        em.createNativeQuery("insert into operator_relief_evidence(id,instance_country_id,event_id,assignment_id,username,purpose,sha256,content_type,content,created_at) values(:id,:t,:e,:a,:u,:p,:h,'image/jpeg',:b,current_timestamp)")
            .setParameter("id",id).setParameter("t",tenant.instanceCountryId()).setParameter("e",eventId).setParameter("a",assignmentId).setParameter("u",user).setParameter("p",purpose).setParameter("h",digest).setParameter("b",bytes).executeUpdate();
        return Map.of("evidenceId",id,"sha256",digest);
    }
    @GET @Path("/relief-evidence/{eventId}/{purpose}") @Produces("image/jpeg")
    public Response evidence(@PathParam("eventId") UUID eventId,@PathParam("purpose") String purpose,@QueryParam("assignmentId") UUID assignmentId) {
        UUID employee=employee();
        if(!PURPOSES.contains(purpose) || assignmentId==null) throw new BadRequestException("Evidencia o asignación inválida");
        assignment(assignmentId,employee);
        List<?> rows=em.createNativeQuery("select content,content_type from operator_relief_evidence where instance_country_id=:t and event_id=:e and assignment_id=:a and username=:u and purpose=:p")
            .setParameter("t",tenant.instanceCountryId()).setParameter("e",eventId).setParameter("a",assignmentId)
            .setParameter("u",identity.getPrincipal().getName()).setParameter("p",purpose).getResultList();
        if(rows.size()!=1) throw new NotFoundException("Evidencia no disponible");
        Object[] row=(Object[])rows.get(0);
        return Response.ok((byte[])row[0]).type(row[1].toString()).header(HttpHeaders.CACHE_CONTROL,"no-store").build();
    }
    @POST @Path("/executions") @Consumes(MediaType.APPLICATION_JSON) @Transactional
    public ObjectNode submit(JsonNode batch) {
        // Hito de patrulla y tarea con foto (Consigna/Bitácora) tienen su propio servicio; el resto es el relevo.
        if(PatrolExecutionContract.TYPE.equals(batch.path("events").path(0).path("type").asText())) return patrolExecutions.submit(batch);
        if(TaskEvidenceService.TYPE.equals(batch.path("events").path(0).path("type").asText())) return taskEvidences.submit(batch);
        UUID employee=employee(); validate(batch,employee,tenant.instanceCountryId(),Instant.now());
        JsonNode event=batch.path("events").get(0); UUID eventId=uuid(event,"eventId"), assignmentId=uuid(event,"assignmentId");
        AssignmentContext c=assignment(assignmentId,employee); lock(assignmentId);
        if(!c.post.id.equals(uuid(event,"postId")) || !c.shift.id.equals(uuid(event,"shiftOccurrenceId"))) throw new BadRequestException("Puesto o turno no coincide");
        String digest=hash(canonical(event).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        List<?> old=em.createNativeQuery("select payload_hash,username from operator_relief_submission where id=:id and instance_country_id=:t")
            .setParameter("id",eventId).setParameter("t",tenant.instanceCountryId()).getResultList();
        if(!old.isEmpty()) { Object[] r=(Object[])old.get(0); if(!r[1].equals(identity.getPrincipal().getName())) throw new ForbiddenException();
            if(!r[0].equals(digest)) throw new ClientErrorException("Identificador reutilizado con datos diferentes",409);
            boolean reviewed=!em.createNativeQuery("select 1 from visual_review v join task_execution t on t.id=v.task_execution_id where t.instance_country_id=:t and t.group_id=:e and t.execution_type=:x")
                .setParameter("t",tenant.instanceCountryId()).setParameter("e",eventId).setParameter("x",ReliefStationReviews.TYPE).setMaxResults(1).getResultList().isEmpty();
            return ack(eventId,reviewed?"QUEUED_FOR_VISINT":"NOT_REQUESTED"); }
        if(!em.createNativeQuery("select id from operator_relief_submission where instance_country_id=:t and assignment_id=:a").setParameter("t",tenant.instanceCountryId()).setParameter("a",assignmentId).getResultList().isEmpty()) throw new ClientErrorException("La asignación ya tiene un relevo recibido",409);
        Instant execution=time(event,"executedAt");
        if(execution.isBefore(c.shift.startsAt.minusSeconds(43200)) || execution.isAfter(c.shift.endsAt.plusSeconds(43200))) throw new BadRequestException("Fecha fuera de ventana UAT del turno");
        if(!event.path("unilateral").asBoolean()) {
            UUID outgoing=uuid(event,"outgoingEmployeeId");
            List<?> previous=em.createNativeQuery("select coalesce(a.actual_employee_id,a.employee_id) from operational_assignment a join shift_occurrence s on s.id=a.shift_occurrence_id and s.instance_country_id=a.instance_country_id where a.instance_country_id=:t and s.post_id=:p and a.status<>'REMOVED' and s.ends_at<=:start order by s.ends_at desc limit 1")
                .setParameter("t",tenant.instanceCountryId()).setParameter("p",c.post.id).setParameter("start",c.shift.startsAt).getResultList();
            if(previous.isEmpty() || previous.get(0)==null || !previous.get(0).toString().equals(outgoing.toString())) throw new BadRequestException("Saliente no coincide con la asignación anterior");
        }
        ObjectNode snapshot=context(c);
        if(!text(event,"configurationVersion").equals(snapshot.path("configurationVersion").asText())) throw new ClientErrorException("El contexto cambió; actualiza y vuelve a confirmar las lecturas",409);
        Map<String,String> required=new TreeMap<>(), actual=new TreeMap<>();
        for(JsonNode item:snapshot.path("consignments")) required.put(item.path("consignmentId").asText(),item.path("version").asText());
        for(JsonNode item:event.path("consignmentReadings")) { String id=uuid(item,"consignmentId").toString(); if(actual.put(id,text(item,"version"))!=null) throw new BadRequestException("Lectura duplicada");
            if(time(item,"confirmedAt").isAfter(Instant.now().plusSeconds(300))) throw new BadRequestException("Lectura futura"); }
        if(!required.equals(actual)) throw new BadRequestException("Debes confirmar todas las consignas vigentes con su versión");
        for(JsonNode photo:event.path("evidence")) {
            List<?> rows=em.createNativeQuery("select id from operator_relief_evidence where id=:id and instance_country_id=:t and event_id=:e and assignment_id=:a and username=:u and purpose=:p")
                .setParameter("id",uuid(photo,"evidenceId")).setParameter("t",tenant.instanceCountryId()).setParameter("e",eventId).setParameter("a",assignmentId).setParameter("u",identity.getPrincipal().getName()).setParameter("p",text(photo,"purpose")).getResultList();
            if(rows.isEmpty()) throw new BadRequestException("Evidencia no autorizada o no cargada");
        }
        em.createNativeQuery("insert into relief_event(id,instance_country_id,post_id,shift_occurrence_id,outgoing_employee_id,incoming_employee_id,status,planned_at,executed_at,unilateral,inventory_result,consignments_confirmed,created_at,updated_at) values(:id,:t,:p,:s,:o,:i,'PENDIENTE',:planned,:executed,:unilateral,'PENDING_SOURCE',true,current_timestamp,current_timestamp)")
            .setParameter("id",eventId).setParameter("t",tenant.instanceCountryId()).setParameter("p",c.post.id).setParameter("s",c.shift.id)
            .setParameter("o",event.path("unilateral").asBoolean()?null:uuid(event,"outgoingEmployeeId")).setParameter("i",employee).setParameter("planned",c.shift.startsAt)
            .setParameter("executed",execution).setParameter("unilateral",event.path("unilateral").asBoolean()).executeUpdate();
        em.createNativeQuery("insert into operator_relief_submission(id,instance_country_id,assignment_id,employee_id,username,device_id,batch_id,correlation_id,payload_hash,payload_json,context_json,received_at) values(:id,:t,:a,:e,:u,:d,:b,:c,:h,:payload,:context,current_timestamp)")
            .setParameter("id",eventId).setParameter("t",tenant.instanceCountryId()).setParameter("a",assignmentId).setParameter("e",employee).setParameter("u",identity.getPrincipal().getName())
            .setParameter("d",text(batch,"deviceId")).setParameter("b",uuid(batch,"batchId")).setParameter("c",uuid(batch,"correlationId"))
            .setParameter("h",digest).setParameter("payload",event.toString()).setParameter("context",snapshot.toString()).executeUpdate();
        // VISINT opcional: las fotos del puesto se comparan con las fotos estándar del Puesto; el resultado no bloquea el relevo.
        String stationVisint=stationReviews.enqueue(tenant.instanceCountryId(),eventId,assignmentId,c.shift.id,c.point.id,c.post.id,employee,identity.getPrincipal().getName(),execution,batch);
        return ack(eventId,stationVisint);
    }
    private JsonNode canonical(JsonNode n) {
        if(n.isObject()) { ObjectNode out=mapper.createObjectNode(); TreeSet<String> names=new TreeSet<>(); n.fieldNames().forEachRemaining(names::add); for(String name:names) out.set(name,canonical(n.get(name))); return out; }
        if(n.isArray()) { ArrayNode out=mapper.createArrayNode(); for(JsonNode v:n) out.add(canonical(v)); return out; } return n;
    }
    /** stationVisintStatus: QUEUED_FOR_VISINT si las fotos del puesto se validan con VISINT, NOT_REQUESTED si el Puesto no lo tiene activado. */
    private ObjectNode ack(UUID id,String stationVisintStatus) {
        ObjectNode n=mapper.createObjectNode().put("serverVersion","relief-uat-v1"); n.putArray("acknowledgedEventIds").add(id.toString()); n.putArray("rejectedEvents");
        n.putArray("pendingMessages").add("Inventario y novedades pendientes de fuente; revisión visual pendiente");
        n.putArray("results").addObject().put("eventId",id.toString()).put("reliefId",id.toString()).put("status","PENDIENTE").put("inventoryStatus","PENDING_SOURCE").put("validationStatus","PENDING_REVIEW").put("stationVisintStatus",stationVisintStatus); return n;
    }
    @POST @Path("/consignment-review-requests") @Consumes(MediaType.APPLICATION_JSON) @Transactional
    public ObjectNode submitConsignmentReviewRequest(@HeaderParam("Idempotency-Key") String idempotencyKey, JsonNode request) {
        UUID employee=employee();
        UUID requestId=uuid(request,"requestId"), assignmentId=uuid(request,"assignmentId");
        String key=idempotencyKey==null?"":idempotencyKey.trim();
        if(key.isEmpty() || key.length()>160) throw new BadRequestException("Idempotency-Key es obligatorio y debe tener máximo 160 caracteres");
        if(!key.equals(requestId.toString())) throw new BadRequestException("Idempotency-Key debe coincidir con requestId");
        String username=identity.getPrincipal().getName();
        String payloadHash=hash(canonical(request).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        List<?> existing=em.createNativeQuery("select payload_hash,employee_id,username from operator_consignment_review_request where id=:id and instance_country_id=:t")
            .setParameter("id",requestId).setParameter("t",tenant.instanceCountryId()).getResultList();
        if(!existing.isEmpty()) {
            Object[] row=(Object[])existing.get(0);
            if(!employee.toString().equals(row[1].toString()) || !username.equals(row[2].toString())) throw new ForbiddenException();
            if(row[0]==null || !payloadHash.equals(row[0].toString())) {
                throw new WebApplicationException("Idempotency-Key ya fue recibido con contenido distinto", Response.status(422).build());
            }
            return consignmentReviewAck(requestId);
        }
        AssignmentContext c=assignment(assignmentId,employee);
        String title=requiredText(request,"title",200), instruction=requiredText(request,"instruction",4000);
        String scope=choice(request,"scope",Set.of("Punto","Puesto"));
        String character=choice(request,"character",Set.of("Permanente","Temporal"));
        String endDate=optionalText(request,"endDate",10);
        if("Temporal".equals(character) && endDate==null) throw new BadRequestException("La consigna temporal requiere fecha de fin");
        String schedule=choice(request,"schedule",Set.of("Todo el tiempo","Horario"));
        String days=optionalText(request,"scheduleDays",40), start=optionalText(request,"startTime",5), end=optionalText(request,"endTime",5);
        if("Horario".equals(schedule) && (days==null || start==null || end==null)) throw new BadRequestException("El horario requiere días y horas");
        String priority=choice(request,"priority",Set.of("Normal","Alta","Crítica"));
        int inserted=em.createNativeQuery("""
            insert into operator_consignment_review_request(id,instance_country_id,assignment_id,employee_id,username,point_id,post_id,title,instruction,scope,character_type,end_date,schedule,schedule_days,start_time,end_time,priority,has_coordinates,has_photo,payload_hash,status,submitted_at,created_at,updated_at)
            values(:id,:t,:a,:e,:u,:point,:post,:title,:instruction,:scope,:character,:endDate,:schedule,:days,:start,:end,:priority,:coordinates,:photo,:payloadHash,'PENDING',current_timestamp,current_timestamp,current_timestamp)
            on conflict (id) do nothing
            """)
            .setParameter("id",requestId).setParameter("t",tenant.instanceCountryId()).setParameter("a",assignmentId).setParameter("e",employee).setParameter("u",username)
            .setParameter("point",c.point.id).setParameter("post",c.post.id).setParameter("title",title).setParameter("instruction",instruction).setParameter("scope",scope).setParameter("character",character)
            .setParameter("endDate",endDate).setParameter("schedule",schedule).setParameter("days",days).setParameter("start",start).setParameter("end",end).setParameter("priority",priority)
            .setParameter("coordinates",request.path("hasCoordinates").asBoolean(false)).setParameter("photo",request.path("hasPhoto").asBoolean(false)).setParameter("payloadHash",payloadHash).executeUpdate();
        if(inserted==0) {
            Object[] row=(Object[])em.createNativeQuery("select payload_hash,employee_id,username from operator_consignment_review_request where id=:id and instance_country_id=:t")
                .setParameter("id",requestId).setParameter("t",tenant.instanceCountryId()).getSingleResult();
            if(!employee.toString().equals(row[1].toString()) || !username.equals(row[2].toString())) throw new ForbiddenException();
            if(!payloadHash.equals(row[0].toString())) throw new WebApplicationException("Idempotency-Key ya fue recibido con contenido distinto", Response.status(422).build());
        }
        return consignmentReviewAck(requestId);
    }
    @GET @Path("/consignment-review-requests")
    public ArrayNode listConsignmentReviewRequests(@QueryParam("status") @DefaultValue("PENDING") String status) {
        actor();
        if(!Set.of("PENDING","APPROVED","DISCARDED").contains(status)) throw new BadRequestException("Estado de revisión inválido");
        boolean operator=identity.hasRole("AGENTE_SEGURIDAD"); UUID employee=operator?employee():null;
        @SuppressWarnings("unchecked") List<Object[]> rows=em.createNativeQuery("""
            select id,point_id,post_id,employee_id,username,title,instruction,priority,submitted_at,schedule,start_time,end_time,status
            from operator_consignment_review_request where instance_country_id=:t and status=:status order by submitted_at desc
            """).setParameter("t",tenant.instanceCountryId()).setParameter("status",status).setMaxResults(200).getResultList();
        ArrayNode result=mapper.createArrayNode();
        for(Object[] row:rows) {
            if(operator && !employee.toString().equals(row[3].toString())) continue;
            PostEntity post=PostEntity.find("id=?1 and instanceCountryId=?2",row[2],tenant.instanceCountryId()).firstResult();
            PointEntity point=PointEntity.find("id=?1 and instanceCountryId=?2",row[1],tenant.instanceCountryId()).firstResult();
            if(post==null || point==null || (!operator && !scope.canAccessCompany(point.companyId))) continue;
            Company company=point.companyId==null?null:Company.find("id=?1 and instanceCountryId=?2",point.companyId,tenant.instanceCountryId()).firstResult();
            ObjectNode item=result.addObject().put("id",row[0].toString()).put("title",row[5].toString()).put("instruction",row[6].toString()).put("priority",row[7].toString())
                .put("submittedAt",row[8].toString()).put("schedule",row[9].toString()).put("status",row[12].toString()).put("pointName",point.name).put("postName",post.name)
                .put("clientName",point.clientName).put("city",point.city).put("submittedBy",row[4].toString());
            if(row[10]!=null) item.put("startTime",row[10].toString());
            if(row[11]!=null) item.put("endTime",row[11].toString());
            if(company!=null) { item.put("companyName",company.name); item.put("companyCode",company.code); }
        }
        return result;
    }
    private ObjectNode consignmentReviewAck(UUID requestId) {
        return mapper.createObjectNode().put("requestId",requestId.toString()).put("status","PENDING").put("message","Consigna recibida para revisión");
    }
    private String requiredText(JsonNode node,String field,int max) {
        String value=optionalText(node,field,max); if(value==null) throw new BadRequestException("Campo obligatorio: "+field); return value;
    }
    private String optionalText(JsonNode node,String field,int max) {
        String value=node.path(field).asText("").trim(); if(value.isEmpty()) return null; if(value.length()>max) throw new BadRequestException("Campo excede el máximo permitido: "+field); return value;
    }
    private String choice(JsonNode node,String field,Set<String> allowed) {
        String value=requiredText(node,field,30); if(!allowed.contains(value)) throw new BadRequestException("Valor inválido para "+field); return value;
    }
    @GET @Path("/reliefs")
    public ArrayNode list(@QueryParam("limit") @DefaultValue("50") int limit) {
        actor(); boolean operator=identity.hasRole("AGENTE_SEGURIDAD");
        UUID employee=operator?employee():null;
        @SuppressWarnings("unchecked") List<Object[]> rows=em.createNativeQuery("select r.id,r.post_id,s.employee_id,s.received_at,s.context_json,s.payload_json,s.username from operator_relief_submission s join relief_event r on r.id=s.id where s.instance_country_id=:t order by s.received_at desc")
            .setParameter("t",tenant.instanceCountryId()).setMaxResults(500).getResultList();
        ArrayNode result=mapper.createArrayNode();
        for(Object[] row:rows) {
            PostEntity post=PostEntity.find("id=?1 and instanceCountryId=?2",row[1],tenant.instanceCountryId()).firstResult();
            PointEntity point=post==null?null:PointEntity.find("id=?1 and instanceCountryId=?2",post.pointId,tenant.instanceCountryId()).firstResult();
            if(point==null || (operator?!employee.toString().equals(row[2].toString()):!scope.canAccessCompany(point.companyId))) continue;
            ObjectNode n=result.addObject().put("reliefId",row[0].toString()).put("postName",post.name).put("pointName",point.name).put("employeeId",row[2].toString())
                .put("receivedAt",row[3].toString()).put("status","PENDIENTE").put("inventoryStatus","PENDING_SOURCE").put("validationStatus","PENDING_REVIEW");
            try { n.set("event",mapper.readTree(row[5].toString())); } catch(Exception e) { throw new InternalServerErrorException(); }
            if(result.size()>=Math.max(1,Math.min(limit,100))) break;
        } return result;
    }
}
