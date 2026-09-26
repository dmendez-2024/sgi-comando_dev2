package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.security.AppUser;
import com.cajamarca.sgi.comando.assignments.*;
import com.cajamarca.sgi.comando.operations.*;
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
          select c.id,c.title,c.instruction,c.updated_at,p.version_no
          from consignment c join consignment_protocol p on p.id=c.protocol_id and p.instance_country_id=c.instance_country_id
          where c.instance_country_id=:tenant and c.point_id=:point and c.status='VIGENTE' and p.status='ACTIVO'
          and (c.validity_from is null or c.validity_from<=current_timestamp)
          and (c.validity_until is null or c.validity_until>=current_timestamp)
          and (c.scope_type='POINT' or c.post_id=:post or exists
            (select 1 from consignment_post_scope x where x.consignment_id=c.id and x.post_id=:post and x.instance_country_id=c.instance_country_id))
          order by c.id
          """).setParameter("tenant",tenant.instanceCountryId()).setParameter("point",c.point.id).setParameter("post",c.post.id).getResultList();
        ArrayNode result=mapper.createArrayNode();
        for(Object[] r:rows) result.addObject().put("consignmentId",r[0].toString()).put("title",r[1].toString()).put("instruction",r[2].toString())
            .put("version",r[4].toString()+":"+r[3].toString());
        return result;
    }
    private ObjectNode context(AssignmentContext c) {
        ObjectNode n=mapper.createObjectNode();
        n.put("assignmentId",c.assignment.id.toString()).put("shiftOccurrenceId",c.shift.id.toString()).put("postId",c.post.id.toString())
            .put("pointId",c.point.id.toString()).put("postName",c.post.name).put("pointName",c.point.name)
            .put("incomingEmployeeId",c.assignment.effectiveEmployeeId().toString()).put("plannedAt",c.shift.startsAt.toString())
            .put("inventoryStatus","PENDING_SOURCE").put("noveltiesStatus","PENDING_SOURCE").put("validationStatus","PENDING_REVIEW");
        List<?> previous=em.createNativeQuery("select coalesce(a.actual_employee_id,a.employee_id),e.full_name,s.ends_at from operational_assignment a join shift_occurrence s on s.id=a.shift_occurrence_id and s.instance_country_id=a.instance_country_id left join employee_operational_snapshot e on e.employee_id=coalesce(a.actual_employee_id,a.employee_id) and e.instance_country_id=a.instance_country_id where a.instance_country_id=:t and s.post_id=:p and a.status<>'REMOVED' and s.ends_at<=:start order by s.ends_at desc limit 1")
            .setParameter("t",tenant.instanceCountryId()).setParameter("p",c.post.id).setParameter("start",c.shift.startsAt).getResultList();
        if(!previous.isEmpty()) {
            Object[] row=(Object[])previous.get(0);
            if(row[0]!=null) n.put("expectedOutgoingEmployeeId",row[0].toString());
            if(row[1]!=null) n.put("expectedOutgoingEmployeeName",row[1].toString());
            if(row[2]!=null) n.put("expectedOutgoingShiftEndsAt",row[2].toString());
        }
        n.set("consignments",consignments(c));
        n.putArray("stationPhotos").add("Vista general del puesto").add("Área de trabajo o garita").add("Acceso principal");
        n.put("configurationVersion",hash(n.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        return n;
    }
    @GET @Path("/runtime")
    public ObjectNode runtime(@QueryParam("assignmentId") UUID assignmentId,@QueryParam("employeeId") UUID requestedEmployee) {
        UUID employee=employee();
        if(requestedEmployee!=null && !requestedEmployee.equals(employee)) throw new ForbiddenException("Empleado incorrecto");
        ObjectNode response=mapper.createObjectNode().put("instanceCountryId",tenant.instanceCountryId().toString()).put("employeeId",employee.toString());
        if(assignmentId!=null) { response.set("relief",context(assignment(assignmentId,employee))); return response; }
        ArrayNode choices=response.putArray("assignments");
        List<OperationalAssignmentEntity> assignments=OperationalAssignmentEntity.list("instanceCountryId=?1 and status<>'REMOVED' and (actualEmployeeId=?2 or (actualEmployeeId is null and employeeId=?2))",tenant.instanceCountryId(),employee);
        Instant now=Instant.now();
        for(var a:assignments) { var c=assignment(a.id,employee); if(c.shift.endsAt.isBefore(now.minusSeconds(86400)) || c.shift.startsAt.isAfter(now.plusSeconds(86400))) continue;
            choices.addObject().put("assignmentId",a.id.toString()).put("postName",c.post.name).put("startsAt",c.shift.startsAt.toString()); }
        return response;
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
    @POST @Path("/executions") @Consumes(MediaType.APPLICATION_JSON) @Transactional
    public ObjectNode submit(JsonNode batch) {
        UUID employee=employee(); validate(batch,employee,tenant.instanceCountryId(),Instant.now());
        JsonNode event=batch.path("events").get(0); UUID eventId=uuid(event,"eventId"), assignmentId=uuid(event,"assignmentId");
        AssignmentContext c=assignment(assignmentId,employee); lock(assignmentId);
        if(!c.post.id.equals(uuid(event,"postId")) || !c.shift.id.equals(uuid(event,"shiftOccurrenceId"))) throw new BadRequestException("Puesto o turno no coincide");
        String digest=hash(canonical(event).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        List<?> old=em.createNativeQuery("select payload_hash,username from operator_relief_submission where id=:id and instance_country_id=:t")
            .setParameter("id",eventId).setParameter("t",tenant.instanceCountryId()).getResultList();
        if(!old.isEmpty()) { Object[] r=(Object[])old.get(0); if(!r[1].equals(identity.getPrincipal().getName())) throw new ForbiddenException();
            if(!r[0].equals(digest)) throw new ClientErrorException("Identificador reutilizado con datos diferentes",409); return ack(eventId); }
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
        return ack(eventId);
    }
    private JsonNode canonical(JsonNode n) {
        if(n.isObject()) { ObjectNode out=mapper.createObjectNode(); TreeSet<String> names=new TreeSet<>(); n.fieldNames().forEachRemaining(names::add); for(String name:names) out.set(name,canonical(n.get(name))); return out; }
        if(n.isArray()) { ArrayNode out=mapper.createArrayNode(); for(JsonNode v:n) out.add(canonical(v)); return out; } return n;
    }
    private ObjectNode ack(UUID id) {
        ObjectNode n=mapper.createObjectNode().put("serverVersion","relief-uat-v1"); n.putArray("acknowledgedEventIds").add(id.toString()); n.putArray("rejectedEvents");
        n.putArray("pendingMessages").add("Inventario y novedades pendientes de fuente; revisión visual pendiente");
        n.putArray("results").addObject().put("eventId",id.toString()).put("reliefId",id.toString()).put("status","PENDIENTE").put("inventoryStatus","PENDING_SOURCE").put("validationStatus","PENDING_REVIEW"); return n;
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
