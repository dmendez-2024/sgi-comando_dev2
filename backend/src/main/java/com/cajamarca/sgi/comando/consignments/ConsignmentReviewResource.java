package com.cajamarca.sgi.comando.consignments;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.time.*;
import java.util.*;

/** Decisión operativa sobre propuestas de consignas recibidas desde SGI Operador. */
@Path("/api/consignment-review-requests")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
public class ConsignmentReviewResource {
    @Inject EntityManager em;
    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject SecurityIdentity identity;
    @Inject ConsignmentResource consignments;
    @Inject ObjectMapper mapper;

    public record ApproveRequest(UUID protocolId,String title,String instruction,String comment){}
    public record DiscardRequest(String reason){}
    public record DecisionResponse(UUID requestId,String status,UUID protocolId,Integer protocolVersion,
                                   UUID consignmentId,String consignmentCode,String message){}

    private record Review(UUID id,UUID pointId,UUID postId,String title,String instruction,String scope,
                          String characterType,String endDate,String schedule,String scheduleDays,
                          String startTime,String endTime,String priority,String status,
                          UUID targetProtocolId,UUID approvedConsignmentId){}

    @POST
    @Path("/{id}/approve")
    @Transactional
    public DecisionResponse approve(@PathParam("id") UUID id,ApproveRequest body){
        if(body==null||body.protocolId()==null)throw new BadRequestException("Seleccione el protocolo al que se incorporará la consigna.");
        Review review=locked(id);authorize(review.pointId());
        if("DISCARDED".equals(review.status()))throw new ClientErrorException("La propuesta ya fue descartada y no puede aprobarse.",409);
        if("APPROVED".equals(review.status()))return approvedResponse(review,"La propuesta ya había sido aprobada.");

        ConsignmentProtocol selected=ConsignmentProtocol.find("id=?1 and instanceCountryId=?2",body.protocolId(),tenant.instanceCountryId()).firstResult();
        if(selected==null)throw new NotFoundException("Protocolo no encontrado.");
        if(!selected.pointId.equals(review.pointId()))throw new BadRequestException("El protocolo seleccionado no pertenece al punto de la propuesta.");

        ConsignmentProtocol draft="BORRADOR".equals(selected.status)?selected:protocol(consignments.fork(selected.id).id());
        long count=Consignment.count("instanceCountryId=?1 and protocolId=?2",tenant.instanceCountryId(),draft.id);
        if(count>=20)throw new ClientErrorException("El borrador seleccionado ya alcanzó el máximo de 20 consignas.",409);

        String title=required(body.title(),review.title(),"El título revisado es obligatorio.");
        String instruction=required(body.instruction(),review.instruction(),"La instrucción revisada es obligatoria.");
        var created=consignments.createConsignment(draft.id,new ConsignmentResource.CreateConsignmentRequest(title));
        var saved=consignments.saveConsignment(created.id(),new ConsignmentResource.SaveConsignmentRequest(
            title,instruction,priority(review.priority()),scopeType(review.scope()),
            "Puesto".equals(review.scope())?List.of(review.postId()):List.of(),
            validityType(review.characterType()),validityFrom(review),validityUntil(review),
            applicationType(review.schedule()),daysJson(review.scheduleDays()),review.startTime(),review.endTime(),
            false,true,false,false,false,"NONE",null,null,null,null,null,null));

        int updated=em.createNativeQuery("""
            update operator_consignment_review_request
               set status='APPROVED',reviewed_at=current_timestamp,reviewed_by=:by,review_comment=:comment,
                   target_protocol_id=:protocol,approved_consignment_id=:item,updated_at=current_timestamp
             where id=:id and instance_country_id=:tenant and status='PENDING'
            """).setParameter("by",user()).setParameter("comment",cleanComment(body.comment()))
            .setParameter("protocol",draft.id).setParameter("item",saved.id()).setParameter("id",id)
            .setParameter("tenant",tenant.instanceCountryId()).executeUpdate();
        if(updated!=1)throw new ClientErrorException("La propuesta fue procesada por otro usuario. Actualice la consulta.",409);
        return new DecisionResponse(id,"APPROVED",draft.id,draft.versionNo,saved.id(),saved.code(),
            "Propuesta aprobada e incorporada al borrador "+draft.code+" v"+draft.versionNo+". Aún no está vigente.");
    }

    @POST
    @Path("/{id}/discard")
    @Transactional
    public DecisionResponse discard(@PathParam("id") UUID id,DiscardRequest body){
        String reason=body==null?null:body.reason();
        if(reason==null||reason.isBlank())throw new BadRequestException("Indique el motivo para descartar la propuesta.");
        Review review=locked(id);authorize(review.pointId());
        if("APPROVED".equals(review.status()))throw new ClientErrorException("La propuesta ya fue aprobada y no puede descartarse.",409);
        if("DISCARDED".equals(review.status()))return new DecisionResponse(id,"DISCARDED",null,null,null,null,"La propuesta ya había sido descartada.");
        int updated=em.createNativeQuery("""
            update operator_consignment_review_request
               set status='DISCARDED',reviewed_at=current_timestamp,reviewed_by=:by,review_comment=:comment,updated_at=current_timestamp
             where id=:id and instance_country_id=:tenant and status='PENDING'
            """).setParameter("by",user()).setParameter("comment",limited(reason,500))
            .setParameter("id",id).setParameter("tenant",tenant.instanceCountryId()).executeUpdate();
        if(updated!=1)throw new ClientErrorException("La propuesta fue procesada por otro usuario. Actualice la consulta.",409);
        return new DecisionResponse(id,"DISCARDED",null,null,null,null,"Propuesta descartada. Se conserva en el historial.");
    }

    private Review locked(UUID id){
        @SuppressWarnings("unchecked") List<Object[]> rows=em.createNativeQuery("""
            select id,point_id,post_id,title,instruction,scope,character_type,end_date,schedule,schedule_days,
                   start_time,end_time,priority,status,target_protocol_id,approved_consignment_id
              from operator_consignment_review_request
             where id=:id and instance_country_id=:tenant for update
            """).setParameter("id",id).setParameter("tenant",tenant.instanceCountryId()).getResultList();
        if(rows.isEmpty())throw new NotFoundException("Propuesta de consigna no encontrada.");Object[] r=rows.get(0);
        return new Review(uuid(r[0]),uuid(r[1]),uuid(r[2]),str(r[3]),str(r[4]),str(r[5]),str(r[6]),str(r[7]),
            str(r[8]),str(r[9]),str(r[10]),str(r[11]),str(r[12]),str(r[13]),uuid(r[14]),uuid(r[15]));
    }
    private DecisionResponse approvedResponse(Review r,String message){Consignment item=r.approvedConsignmentId()==null?null:Consignment.findById(r.approvedConsignmentId());ConsignmentProtocol p=r.targetProtocolId()==null?null:protocol(r.targetProtocolId());return new DecisionResponse(r.id(),"APPROVED",r.targetProtocolId(),p==null?null:p.versionNo,r.approvedConsignmentId(),item==null?null:item.code,message);}
    private void authorize(UUID pointId){PointEntity p=PointEntity.find("id=?1 and instanceCountryId=?2",pointId,tenant.instanceCountryId()).firstResult();if(p==null)throw new NotFoundException("Punto no encontrado.");scope.requireCompanyForCompanyRoles(p.companyId);}
    private ConsignmentProtocol protocol(UUID id){return id==null?null:ConsignmentProtocol.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();}
    private String user(){return identity.getPrincipal().getName();}
    private static UUID uuid(Object v){return v==null?null:(v instanceof UUID u?u:UUID.fromString(v.toString()));}
    private static String str(Object v){return v==null?null:v.toString();}
    private static String required(String preferred,String fallback,String message){String value=preferred==null||preferred.isBlank()?fallback:preferred;if(value==null||value.isBlank())throw new BadRequestException(message);return value.trim();}
    private static String cleanComment(String value){return value==null||value.isBlank()?null:limited(value,500);}
    private static String limited(String value,int max){String out=value.trim();return out.length()>max?out.substring(0,max):out;}
    private static String priority(String value){return "Crítica".equals(value)?"CRITICAL":"Alta".equals(value)?"HIGH":"MEDIUM";}
    private static String scopeType(String value){return "Puesto".equals(value)?"POSTS":"POINT";}
    private static String validityType(String value){return "Temporal".equals(value)?"TEMPORARY":"PERMANENT";}
    private static String applicationType(String value){return "Horario".equals(value)?"CALENDAR":"ALL_TIME";}
    private static Instant validityFrom(Review r){return "Temporal".equals(r.characterType())?Instant.now():null;}
    private static Instant validityUntil(Review r){if(!"Temporal".equals(r.characterType())||r.endDate()==null)return null;try{return LocalDate.parse(r.endDate()).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();}catch(Exception e){throw new BadRequestException("La fecha final de la propuesta no es válida.");}}
    private String daysJson(String value){if(value==null||value.isBlank())return "[]";Map<String,String> map=Map.ofEntries(Map.entry("LUN","MON"),Map.entry("MAR","TUE"),Map.entry("MIE","WED"),Map.entry("JUE","THU"),Map.entry("VIE","FRI"),Map.entry("SAB","SAT"),Map.entry("DOM","SUN"));List<String> days=Arrays.stream(value.toUpperCase(Locale.ROOT).replace("É","E").replace("Á","A").split("[^A-Z]+" )).map(map::get).filter(Objects::nonNull).distinct().toList();try{return mapper.writeValueAsString(days);}catch(Exception e){throw new BadRequestException("No fue posible interpretar los días de aplicación.");}}
}
