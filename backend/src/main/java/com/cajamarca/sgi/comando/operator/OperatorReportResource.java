package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.assignments.AssignmentPlanEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.time.Instant;
import java.util.*;

/** Contrato temporal local; CORE podrá sustituir el binding sin cambiar el payload v1. */
@Path("/api/v1/operator/reports")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class OperatorReportResource {
    private static final Set<String> TYPES=Set.of("INCIDENT","FINDING","VULNERABILITY");
    private static final Set<String> SEVERITIES=Set.of("LOW","MEDIUM","HIGH","CRITICAL");
    @Inject OperatorContext ctx;
    @Inject TenantContext tenant;
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;

    @org.jboss.resteasy.reactive.server.ServerExceptionMapper
    public Response mapError(WebApplicationException e) { return OperatorErrors.withMessage(e); }

    @POST @Consumes(MediaType.APPLICATION_JSON) @Transactional
    public ObjectNode create(JsonNode body, @HeaderParam("Idempotency-Key") String key) {
        UUID employee=ctx.employee();
        UUID reportId=uuid(body,"reportId"), assignmentId=uuid(body,"assignmentId");
        if(key==null || !reportId.toString().equals(key.trim())) throw new BadRequestException("Idempotency-Key debe coincidir con reportId");
        String type=required(body,"type").toUpperCase(Locale.ROOT);
        String severity=required(body,"severity").toUpperCase(Locale.ROOT);
        if(!TYPES.contains(type)) throw new BadRequestException("Tipo de reporte inválido");
        if(!SEVERITIES.contains(severity)) throw new BadRequestException("Severidad inválida");
        OperatorContext.Assignment assignment=ctx.assignment(assignmentId,employee);
        AssignmentPlanEntity plan=AssignmentPlanEntity.find("id=?1 and instanceCountryId=?2",assignment.assignment().assignmentPlanId,tenant.instanceCountryId()).firstResult();
        if(plan==null || !("PUBLISHED".equals(plan.status)||"CLOSED".equals(plan.status)))
            throw new ForbiddenException("Tu asignación aún no ha sido publicada en SGI COMANDO. Comunícate con tu supervisor.");
        Instant occurred=instant(body,"occurredAt");
        if(occurred.isAfter(Instant.now().plusSeconds(300))) throw new BadRequestException("La fecha del reporte no puede estar en el futuro");
        Object[] existing=find(reportId);
        if(existing!=null) {
            if(!employee.toString().equals(existing[1].toString())) throw new ClientErrorException("El identificador del reporte ya está en uso",409);
            return response(existing,true);
        }
        String category=required(body,"category"), title=required(body,"title"), description=required(body,"description");
        String subcategory=optional(body,"subcategory");
        if(title.length()>200 || category.length()>80 || (subcategory!=null && subcategory.length()>120) || description.length()>2000)
            throw new BadRequestException("El reporte supera la longitud permitida");
        Instant now=Instant.now();
        em.createNativeQuery("""
          insert into operator_operational_report
          (id,instance_country_id,assignment_id,employee_id,username,point_id,post_id,report_type,category,subcategory,title,description,severity,status,occurred_at,source,created_at,updated_at)
          values (:id,:tenant,:assignment,:employee,:username,:point,:post,:type,:category,:subcategory,:title,:description,:severity,'REPORTED',:occurred,'SGI_OPERADOR_LOCAL',:now,:now)
          """).setParameter("id",reportId).setParameter("tenant",tenant.instanceCountryId())
          .setParameter("assignment",assignmentId).setParameter("employee",employee).setParameter("username",ctx.username())
          .setParameter("point",assignment.point().id).setParameter("post",assignment.post().id).setParameter("type",type)
          .setParameter("category",category).setParameter("subcategory",subcategory).setParameter("title",title)
          .setParameter("description",description).setParameter("severity",severity).setParameter("occurred",occurred)
          .setParameter("now",now).executeUpdate();
        return response(find(reportId),false);
    }

    @GET
    public ArrayNode list(@QueryParam("type") String type) {
        UUID employee=ctx.employee();
        String selected=normalizeType(type);
        return rows(employee,selected);
    }

    ArrayNode rows(UUID employee,String type) {
        String sql="""
          select r.id,r.employee_id,r.report_type,r.category,r.subcategory,r.title,r.description,r.severity,r.status,
                 r.occurred_at,r.created_at,r.assignment_id,r.point_id,r.post_id,r.username,p.name,coalesce(po.name,'—')
          from operator_operational_report r join point p on p.id=r.point_id left join post po on po.id=r.post_id
          where r.instance_country_id=:tenant order by r.occurred_at desc limit 200
          """;
        String filters=(employee==null?"":" and r.employee_id=:employee")+(type==null?"":" and r.report_type=:type");
        sql=sql.replace(" order by",filters+" order by");
        var query=em.createNativeQuery(sql).setParameter("tenant",tenant.instanceCountryId());
        if(employee!=null) query.setParameter("employee",employee);
        if(type!=null) query.setParameter("type",type);
        @SuppressWarnings("unchecked") List<Object[]> data=query.getResultList();
        ArrayNode out=mapper.createArrayNode(); data.forEach(row->out.add(response(row,false))); return out;
    }

    private Object[] find(UUID id) {
        @SuppressWarnings("unchecked") List<Object[]> rows=em.createNativeQuery("""
          select r.id,r.employee_id,r.report_type,r.category,r.subcategory,r.title,r.description,r.severity,r.status,
                 r.occurred_at,r.created_at,r.assignment_id,r.point_id,r.post_id,r.username,p.name,coalesce(po.name,'—')
          from operator_operational_report r join point p on p.id=r.point_id left join post po on po.id=r.post_id
          where r.instance_country_id=:tenant and r.id=:id
          """).setParameter("tenant",tenant.instanceCountryId()).setParameter("id",id).getResultList();
        return rows.isEmpty()?null:rows.get(0);
    }
    ObjectNode response(Object[] r,boolean duplicate) {
        ObjectNode x=mapper.createObjectNode().put("reportId",r[0].toString()).put("type",r[2].toString())
          .put("category",r[3].toString()).put("title",r[5].toString()).put("description",r[6].toString())
          .put("severity",r[7].toString()).put("status",r[8].toString()).put("occurredAt",r[9].toString())
          .put("createdAt",r[10].toString()).put("assignmentId",r[11].toString()).put("pointId",r[12].toString())
          .put("username",r[14].toString()).put("pointName",r[15].toString()).put("postName",r[16].toString())
          .put("acknowledged",true).put("duplicate",duplicate);
        if(r[4]!=null)x.put("subcategory",r[4].toString()); if(r[13]!=null)x.put("postId",r[13].toString()); return x;
    }
    static String normalizeType(String value) { if(value==null||value.isBlank())return null; String x=value.trim().toUpperCase(Locale.ROOT); if(!TYPES.contains(x))throw new BadRequestException("Tipo de reporte inválido"); return x; }
    static String required(JsonNode n,String field){ String x=optional(n,field); if(x==null)throw new BadRequestException(field+" es obligatorio"); return x; }
    static String optional(JsonNode n,String field){ JsonNode v=n==null?null:n.get(field); if(v==null||v.isNull()||v.asText().isBlank())return null; return v.asText().trim(); }
    static UUID uuid(JsonNode n,String field){ try{return UUID.fromString(required(n,field));}catch(IllegalArgumentException e){throw new BadRequestException(field+" debe ser UUID");} }
    static Instant instant(JsonNode n,String field){ try{return Instant.parse(required(n,field));}catch(Exception e){throw new BadRequestException(field+" debe ser ISO-8601");} }
}
