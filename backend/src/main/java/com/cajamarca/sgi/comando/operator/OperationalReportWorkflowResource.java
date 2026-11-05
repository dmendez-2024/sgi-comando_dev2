package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.time.Instant;
import java.util.*;

/** Decisiones de Comando sobre una Novedad previamente registrada desde SGI Operador. */
@Path("/api/operational-reports")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OperationalReportWorkflowResource {
    @Inject EntityManager em;
    @Inject TenantContext tenant;
    @Inject SecurityIdentity identity;
    @Inject OperationalScopeService scope;

    public record DecisionRequest(String comment) {}
    public record DecisionResponse(UUID reportId,String status,String message) {}
    record Report(UUID id,UUID pointId,String status) {}

    @POST
    @Path("/{id}/approve")
    @RolesAllowed({"COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    @Transactional
    public DecisionResponse approve(@PathParam("id") UUID id,DecisionRequest body) {
        Report report=locked(id); authorize(report.pointId());
        if("APPROVED".equals(report.status())) return new DecisionResponse(id,"APPROVED","La novedad ya había sido aprobada.");
        if(!"CONFIRMED".equals(report.status()))
            throw new ClientErrorException("Solo una novedad confirmada puede aprobarse.",409);
        transition(report,"APPROVE","APPROVED",clean(body==null?null:body.comment()),null,null,null);
        return new DecisionResponse(id,"APPROVED","Novedad aprobada correctamente.");
    }

    @POST
    @Path("/{id}/discard")
    @RolesAllowed({"COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    @Transactional
    public DecisionResponse discard(@PathParam("id") UUID id,DecisionRequest body) {
        String reason=required(body==null?null:body.comment(),"Indique el motivo para descartar la novedad.");
        Report report=locked(id); authorize(report.pointId());
        if("DISCARDED".equals(report.status())) return new DecisionResponse(id,"DISCARDED","La novedad ya había sido descartada.");
        if(!Set.of("PENDING","CONFIRMED").contains(report.status()))
            throw new ClientErrorException("La novedad ya no admite descarte.",409);
        transition(report,"DISCARD","DISCARDED",reason,null,null,null);
        return new DecisionResponse(id,"DISCARDED","Novedad descartada. Se conserva en el historial.");
    }

    @GET
    @Path("/{id}/history")
    @RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","MONITOR"})
    public List<Map<String,Object>> history(@PathParam("id") UUID id) {
        Report report=find(id,false); authorizeRead(report.pointId());
        @SuppressWarnings("unchecked") List<Object[]> rows=em.createNativeQuery("""
            select id,action,previous_status,resulting_status,actor_username,actor_role,comment,created_at,
                   assignment_id,shift_occurrence_id
              from operator_operational_report_decision
             where instance_country_id=:tenant and report_id=:report
             order by created_at,id
            """).setParameter("tenant",tenant.instanceCountryId()).setParameter("report",id).getResultList();
        List<Map<String,Object>> out=new ArrayList<>();
        for(Object[] row:rows){Map<String,Object> x=new LinkedHashMap<>();x.put("id",row[0]);x.put("action",row[1]);x.put("previousStatus",row[2]);x.put("resultingStatus",row[3]);x.put("actor",row[4]);x.put("actorRole",row[5]);x.put("comment",row[6]);x.put("createdAt",row[7]);x.put("assignmentId",row[8]);x.put("shiftOccurrenceId",row[9]);out.add(x);}
        return out;
    }

    void transition(Report report,String action,String resulting,String comment,UUID employeeId,UUID assignmentId,UUID shiftId) {
        int updated=em.createNativeQuery("""
            update operator_operational_report set status=:status,updated_at=current_timestamp
             where id=:id and instance_country_id=:tenant and status=:previous
            """).setParameter("status",resulting).setParameter("id",report.id())
            .setParameter("tenant",tenant.instanceCountryId()).setParameter("previous",report.status()).executeUpdate();
        if(updated!=1) throw new ClientErrorException("La novedad fue procesada por otro usuario. Actualice la consulta.",409);
        em.createNativeQuery("""
            insert into operator_operational_report_decision
              (id,instance_country_id,report_id,action,previous_status,resulting_status,actor_username,actor_role,
               actor_employee_id,assignment_id,shift_occurrence_id,comment,created_at)
            values (:id,:tenant,:report,:action,:previous,:resulting,:username,:role,:employee,:assignment,:shift,:comment,:at)
            """).setParameter("id",UUID.randomUUID()).setParameter("tenant",tenant.instanceCountryId())
            .setParameter("report",report.id()).setParameter("action",action).setParameter("previous",report.status())
            .setParameter("resulting",resulting).setParameter("username",identity.getPrincipal().getName())
            .setParameter("role",actorRole()).setParameter("employee",employeeId).setParameter("assignment",assignmentId)
            .setParameter("shift",shiftId).setParameter("comment",comment).setParameter("at",Instant.now()).executeUpdate();
    }

    Report locked(UUID id){return find(id,true);}
    Report find(UUID id,boolean lock){
        String sql="select id,point_id,status from operator_operational_report where id=:id and instance_country_id=:tenant"+(lock?" for update":"");
        @SuppressWarnings("unchecked") List<Object[]> rows=em.createNativeQuery(sql).setParameter("id",id).setParameter("tenant",tenant.instanceCountryId()).getResultList();
        if(rows.isEmpty())throw new NotFoundException("Novedad no encontrada.");Object[] row=rows.get(0);
        return new Report(uuid(row[0]),uuid(row[1]),row[2].toString());
    }
    private void authorize(UUID pointId){PointEntity point=PointEntity.find("id=?1 and instanceCountryId=?2",pointId,tenant.instanceCountryId()).firstResult();if(point==null)throw new NotFoundException("Punto no encontrado.");scope.requireCompanyForCompanyRoles(point.companyId);}
    private void authorizeRead(UUID pointId){PointEntity point=PointEntity.find("id=?1 and instanceCountryId=?2",pointId,tenant.instanceCountryId()).firstResult();if(point==null)throw new NotFoundException("Punto no encontrado.");scope.requireCompany(point.companyId);}
    private String actorRole(){
        if(identity.hasRole("COORDINADOR_COMPANIA"))return "COORDINADOR_COMPANIA";
        if(identity.hasRole("ASISTENTE_COORDINACION"))return "ASISTENTE_COORDINACION";
        if(identity.hasRole("SUPERVISOR_SEGURIDAD"))return "SUPERVISOR_SEGURIDAD";
        if(identity.hasRole("AGENTE_SEGURIDAD"))return "AGENTE_SEGURIDAD";
        return "OPERADOR";
    }
    private static UUID uuid(Object value){return value instanceof UUID x?x:UUID.fromString(value.toString());}
    private static String clean(String value){return value==null||value.isBlank()?null:value.trim();}
    private static String required(String value,String message){if(value==null||value.isBlank())throw new BadRequestException(message);return value.trim().substring(0,Math.min(1000,value.trim().length()));}
}
