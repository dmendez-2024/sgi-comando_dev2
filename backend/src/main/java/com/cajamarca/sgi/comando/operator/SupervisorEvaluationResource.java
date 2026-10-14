package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.assignments.*;
import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.execution.EvidenceObject;
import com.cajamarca.sgi.comando.inventory.InventoryService;
import com.cajamarca.sgi.comando.operations.*;
import com.cajamarca.sgi.comando.storage.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Path("/api/v1/operator/supervision")
@RolesAllowed("SUPERVISOR_SEGURIDAD")
@Produces(MediaType.APPLICATION_JSON)
public class SupervisorEvaluationResource {
    private static final Set<String> LEVELS=Set.of("LOW","MEDIUM","HIGH","VERY_HIGH");
    private static final Set<String> INVENTORY_STATES=Set.of("COMPLETE","OBSERVATIONS","INCOMPLETE","CRITICAL");
    @Inject TenantContext tenant; @Inject EntityManager em; @Inject ObjectMapper mapper;
    @Inject SecurityIdentity identity; @Inject OperatorContext operator; @Inject InventoryService inventory;
    @Inject StorageService storage;
    @ConfigProperty(name="sgi.evidence.max-file-bytes") long maxFileBytes;

    @org.jboss.resteasy.reactive.server.ServerExceptionMapper
    public Response mapError(WebApplicationException e){return OperatorErrors.withMessage(e);}

    @GET @Path("/posts/{postId}/context")
    public ObjectNode context(@PathParam("postId") UUID postId,@QueryParam("assignmentId") UUID supervisorAssignmentId){
        Scope s=scope(supervisorAssignmentId,postId); PostEntity post=post(postId); PointEntity point=point(post.pointId);
        EmployeeOperationalSnapshot agent=EmployeeOperationalSnapshot.find("instanceCountryId=?1 and employeeId=?2",tenant.instanceCountryId(),s.agentEmployeeId).firstResult();
        ObjectNode out=mapper.createObjectNode().put("supervisorAssignmentId",s.supervisorAssignmentId.toString()).put("agentAssignmentId",s.agentAssignmentId.toString())
          .put("agentEmployeeId",s.agentEmployeeId.toString()).put("pointId",point.id.toString()).put("pointName",point.name)
          .put("clientName",point.clientName).put("postId",post.id.toString()).put("postCode",post.code).put("postName",post.name)
          .put("agentName",agent==null?"Agente asignado":agent.fullName).put("shiftStartsAt",s.startsAt.toString()).put("shiftEndsAt",s.endsAt.toString());
        if(agent!=null&&agent.idScore!=null)out.put("performanceIndex",agent.idScore.doubleValue());
        List<?> completed=em.createNativeQuery("select id from supervisor_evaluation where instance_country_id=:t and supervisor_assignment_id=:a and post_id=:p limit 1")
          .setParameter("t",tenant.instanceCountryId()).setParameter("a",s.supervisorAssignmentId).setParameter("p",postId).getResultList();
        out.put("evaluationCompleted",!completed.isEmpty());if(!completed.isEmpty())out.put("evaluationId",completed.get(0).toString());
        out.set("inventory",inventory.runtime(post.id)); out.set("novelties",novelties(s));
        return out;
    }

    @POST @Path("/evaluations") @Consumes(MediaType.MULTIPART_FORM_DATA) @Transactional
    public ObjectNode finish(@RestForm("metadata") String metadataJson,@RestForm("files") List<FileUpload> files,@HeaderParam("Idempotency-Key") String key) throws IOException{
        JsonNode body;try{body=mapper.readTree(metadataJson);}catch(Exception e){throw new BadRequestException("metadata inválida");}
        UUID evaluationId=uuid(body,"evaluationId"), supervisorAssignmentId=uuid(body,"supervisorAssignmentId"), postId=uuid(body,"postId");
        if(key==null||!evaluationId.toString().equals(key.trim()))throw new BadRequestException("Idempotency-Key debe coincidir con evaluationId");
        Scope s=scope(supervisorAssignmentId,postId); operator.lock(supervisorAssignmentId);
        String payloadHash=sha256(body.toString()); List<?> previous=em.createNativeQuery("select payload_hash from supervisor_evaluation where id=:id and instance_country_id=:t")
          .setParameter("id",evaluationId).setParameter("t",tenant.instanceCountryId()).getResultList();
        if(!previous.isEmpty()){if(!payloadHash.equals(previous.get(0).toString()))throw new ClientErrorException("La evaluación ya existe con otro contenido",409);return response(evaluationId,true);}
        List<?> completed=em.createNativeQuery("select id from supervisor_evaluation where instance_country_id=:t and supervisor_assignment_id=:a and post_id=:p limit 1")
          .setParameter("t",tenant.instanceCountryId()).setParameter("a",supervisorAssignmentId).setParameter("p",postId).getResultList();
        if(!completed.isEmpty())throw new ClientErrorException("Este Puesto ya fue supervisado en la asignación actual",409);
        String vigilance=choice(body,"vigilanceLevel",LEVELS),cleanliness=choice(body,"cleanlinessLevel",LEVELS),knowledge=choice(body,"consignmentKnowledgeLevel",LEVELS),situational=choice(body,"situationalLevel",LEVELS);
        String inventoryState=choice(body,"inventoryOverallState",INVENTORY_STATES); Instant captured=instant(body,"capturedAt");
        if(captured.isBefore(s.startsAt.minusSeconds(3600))||captured.isAfter(s.endsAt.plusSeconds(3600)))throw new BadRequestException("La evaluación está fuera del turno supervisado");
        List<FileUpload> uploads=files==null?List.of():files;if(uploads.size()!=1)throw new BadRequestException("La foto actual del Agente es obligatoria");
        FileUpload photo=uploads.get(0);if(photo.size()>maxFileBytes)throw new BadRequestException("La foto supera el tamaño permitido");String contentType=ImageSniffer.detect(photo.uploadedFile());if(contentType==null)throw new BadRequestException("Formato de foto no soportado");
        UUID evidenceId=storePhoto(evaluationId,supervisorAssignmentId,s.supervisorEmployeeId,postId,captured,photo,contentType);
        UUID correlation=optionalUuid(body.path("correlationId").asText(null));if(correlation==null)correlation=UUID.randomUUID();Instant now=Instant.now();
        em.createNativeQuery("""
          insert into supervisor_evaluation(id,instance_country_id,supervisor_assignment_id,agent_assignment_id,supervisor_employee_id,agent_employee_id,point_id,post_id,captured_at,vigilance_level,cleanliness_level,consignment_knowledge_level,situational_level,consignment_id,consignment_version,consignment_snapshot,agent_photo_evidence_id,inventory_overall_state,inventory_comments,supervisor_comments,point_post_notes,status,correlation_id,created_by,created_at,payload_hash)
          values(:id,:tenant,:sa,:aa,:se,:ae,:point,:post,:captured,:v,:c,:k,:s,:consignment,:version,:snapshot,:evidence,:inventory,:inventoryComments,:comments,:notes,'COMPLETED',:correlation,:username,:now,:hash)
          """).setParameter("id",evaluationId).setParameter("tenant",tenant.instanceCountryId()).setParameter("sa",s.supervisorAssignmentId).setParameter("aa",s.agentAssignmentId)
          .setParameter("se",s.supervisorEmployeeId).setParameter("ae",s.agentEmployeeId).setParameter("point",post(postId).pointId).setParameter("post",postId).setParameter("captured",captured)
          .setParameter("v",vigilance).setParameter("c",cleanliness).setParameter("k",knowledge).setParameter("s",situational)
          .setParameter("consignment",optionalUuid(body.path("consignmentId").asText(null))).setParameter("version",body.path("consignmentVersion").isInt()?body.path("consignmentVersion").asInt():null)
          .setParameter("snapshot",text(body,"consignmentSnapshot",1000)).setParameter("evidence",evidenceId).setParameter("inventory",inventoryState)
          .setParameter("inventoryComments",text(body,"inventoryComments",2000)).setParameter("comments",text(body,"supervisorComments",3000))
          .setParameter("notes",text(body,"pointPostNotes",3000))
          .setParameter("correlation",correlation).setParameter("username",operator.username()).setParameter("now",now).setParameter("hash",payloadHash).executeUpdate();
        saveInventory(evaluationId,body.path("inventoryItems"),postId);saveDecisions(evaluationId,body.path("noveltyDecisions"),s);
        return response(evaluationId,false);
    }

    private record Scope(UUID supervisorAssignmentId,UUID supervisorEmployeeId,UUID agentAssignmentId,UUID agentEmployeeId,Instant startsAt,Instant endsAt){}
    private Scope scope(UUID assignmentId,UUID postId){
        if(assignmentId==null)throw new BadRequestException("assignmentId es obligatorio");UUID supervisor=operator.employee();
        @SuppressWarnings("unchecked") List<Object[]> rows=em.createNativeQuery("""
          select aa.id,coalesce(aa.actual_employee_id,aa.employee_id),ss.starts_at,ss.ends_at
          from operational_assignment sa join shift_occurrence ss on ss.id=sa.shift_occurrence_id and ss.instance_country_id=sa.instance_country_id
          join assignment_plan sp on sp.id=sa.assignment_plan_id and sp.instance_country_id=sa.instance_country_id
          join supervision_route_point rp on rp.route_id=ss.supervision_route_id and rp.instance_country_id=ss.instance_country_id
          join post target on target.point_id=rp.point_id and target.instance_country_id=ss.instance_country_id and target.id=:post
          join shift_occurrence ash on ash.post_id=target.id and ash.instance_country_id=ss.instance_country_id and ash.starts_at<ss.ends_at and ash.ends_at>ss.starts_at
          join operational_assignment aa on aa.shift_occurrence_id=ash.id and aa.instance_country_id=ash.instance_country_id and aa.status<>'REMOVED'
          join assignment_plan ap on ap.id=aa.assignment_plan_id and ap.instance_country_id=aa.instance_country_id and ap.status in ('PUBLISHED','CLOSED')
          where sa.id=:assignment and sa.instance_country_id=:tenant and sa.status<>'REMOVED' and sp.status in ('PUBLISHED','CLOSED') and coalesce(sa.actual_employee_id,sa.employee_id)=:supervisor
          order by ash.starts_at desc limit 1
          """).setParameter("post",postId).setParameter("assignment",assignmentId).setParameter("tenant",tenant.instanceCountryId()).setParameter("supervisor",supervisor).getResultList();
        if(rows.isEmpty())throw new ForbiddenException("El Puesto o el Agente no pertenecen a tu asignación de supervisión");Object[] r=rows.get(0);
        return new Scope(assignmentId,supervisor,UUID.fromString(r[0].toString()),UUID.fromString(r[1].toString()),toInstant(r[2]),toInstant(r[3]));
    }

    private ArrayNode novelties(Scope s){ArrayNode out=mapper.createArrayNode();@SuppressWarnings("unchecked") List<Object[]> rows=em.createNativeQuery("select id,report_type,title,description,severity,occurred_at,status from operator_operational_report where instance_country_id=:t and assignment_id=:a and employee_id=:e order by occurred_at desc")
      .setParameter("t",tenant.instanceCountryId()).setParameter("a",s.agentAssignmentId).setParameter("e",s.agentEmployeeId).getResultList();for(Object[] r:rows)out.addObject().put("id",r[0].toString()).put("type",r[1].toString()).put("title",r[2].toString()).put("detail",r[3].toString()).put("severity",r[4].toString()).put("reportedAt",r[5].toString()).put("status",r[6].toString());return out;}
    private void saveInventory(UUID evaluation,JsonNode items,UUID postId){if(!items.isArray())return;for(JsonNode x:items){UUID asset=uuid(x,"assetId");if(!inventory.isExpectedAsset(postId,asset))throw new BadRequestException("Activo fuera del inventario vigente");em.createNativeQuery("insert into supervisor_evaluation_inventory_item(evaluation_id,asset_id,description_snapshot,observed_state,comment) values(:e,:a,:d,:s,:c)").setParameter("e",evaluation).setParameter("a",asset).setParameter("d",required(x,"description",500)).setParameter("s",choice(x,"observedState",Set.of("GOOD","DAMAGED","MISSING","NOT_VERIFIED"))).setParameter("c",text(x,"comment",1000)).executeUpdate();}}
    private void saveDecisions(UUID evaluation,JsonNode decisions,Scope s){if(!decisions.isArray())return;for(JsonNode x:decisions){UUID id=uuid(x,"noveltyId");String decision=choice(x,"decision",Set.of("CONFIRMED","DISCARDED"));List<?> allowed=em.createNativeQuery("select report_type from operator_operational_report where id=:id and instance_country_id=:t and assignment_id=:a and employee_id=:e").setParameter("id",id).setParameter("t",tenant.instanceCountryId()).setParameter("a",s.agentAssignmentId).setParameter("e",s.agentEmployeeId).getResultList();if(allowed.isEmpty())throw new BadRequestException("Novedad fuera del turno supervisado");em.createNativeQuery("insert into supervisor_evaluation_novelty_decision(evaluation_id,novelty_id,novelty_type,decision,decided_by,decided_at) values(:evaluation,:id,:type,:decision,:by,current_timestamp)").setParameter("evaluation",evaluation).setParameter("id",id).setParameter("type",allowed.get(0).toString()).setParameter("decision",decision).setParameter("by",operator.username()).executeUpdate();}}
    private UUID storePhoto(UUID event,UUID assignment,UUID employee,UUID post,Instant captured,FileUpload file,String type)throws IOException{UUID id=UUID.randomUUID();String ext=ImageSniffer.extension(type);String key="evidence/supervision/"+event+"/"+id+"."+ext;try(InputStream in=Files.newInputStream(file.uploadedFile())){storage.put(storage.evidenceBucket(),key,in,file.size(),type);}EvidenceObject e=new EvidenceObject();e.id=id;e.instanceCountryId=tenant.instanceCountryId();e.clientEvidenceId=UUID.randomUUID();e.uploadBatchId=event;e.eventId=event;e.assignmentId=assignment;e.employeeId=employee;e.username=operator.username();e.targetType="SUPERVISOR_AGENT_PHOTO";e.targetId=post;e.bucket=storage.evidenceBucket();e.objectKey=key;e.contentType=type;e.sizeBytes=file.size();e.sha256=sha256(file.uploadedFile());e.capturedAt=captured;e.source="CAMERA";e.status="STORED";e.receivedAt=Instant.now();e.flags="";e.persist();return id;}
    private ObjectNode response(UUID id,boolean duplicate){return mapper.createObjectNode().put("evaluationId",id.toString()).put("status","COMPLETED").put("duplicate",duplicate).put("message","Supervisión finalizada y registrada en SGI Comando.");}
    private PostEntity post(UUID id){PostEntity p=PostEntity.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(p==null)throw new NotFoundException("Puesto no encontrado");return p;}private PointEntity point(UUID id){return PointEntity.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();}
    private static String required(JsonNode n,String f,int max){String v=text(n,f,max);if(v==null)throw new BadRequestException(f+" es obligatorio");return v;}private static String text(JsonNode n,String f,int max){String v=n.path(f).asText("").trim();if(v.isBlank())return null;if(v.length()>max)throw new BadRequestException(f+" excede el máximo permitido");return v;}
    private static UUID uuid(JsonNode n,String f){try{return UUID.fromString(required(n,f,50));}catch(Exception e){throw new BadRequestException(f+" debe ser UUID");}}private static UUID optionalUuid(String v){try{return v==null||v.isBlank()?null:UUID.fromString(v);}catch(Exception e){throw new BadRequestException("UUID inválido");}}
    private static String choice(JsonNode n,String f,Set<String> values){String v=required(n,f,40).toUpperCase(Locale.ROOT);if(!values.contains(v))throw new BadRequestException(f+" inválido");return v;}private static Instant instant(JsonNode n,String f){try{return Instant.parse(required(n,f,80));}catch(Exception e){throw new BadRequestException(f+" debe ser ISO-8601");}}
    private static Instant toInstant(Object v){if(v instanceof Instant i)return i;if(v instanceof java.sql.Timestamp t)return t.toInstant();return Instant.parse(v.toString());}private static String sha256(String v){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}private static String sha256(java.nio.file.Path p){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}catch(Exception e){throw new IllegalStateException(e);}}
}
