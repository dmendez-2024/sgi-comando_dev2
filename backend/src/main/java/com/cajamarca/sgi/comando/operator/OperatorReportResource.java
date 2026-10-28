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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Contrato temporal local; CORE podrá sustituir el binding sin cambiar el payload v1. */
@Path("/api/v1/operator/reports")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class OperatorReportResource {
    private static final Set<String> TYPES=Set.of("INCIDENT","FINDING","VULNERABILITY");
    private static final Set<String> SEVERITIES=Set.of("LOW","MEDIUM","HIGH","CRITICAL");
    private static final Map<String,Set<String>> CATEGORIES=Map.of(
      "INCIDENT",Set.of("Servicio","Seguridad","Administrativo"),
      "FINDING",Set.of("Orden","Limpieza","Mantenimiento","Estacionamiento","Componente de seguridad"),
      "VULNERABILITY",Set.of("Acceso","Perímetro","Interno")
    );
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
        String category=required(body,"category"), title=required(body,"title"), description=required(body,"description");
        String subcategory=optional(body,"subcategory");
        int photoCount=body.path("photoCount").asInt(0);
        if(photoCount<0||photoCount>1) throw new BadRequestException("Cantidad de fotografías inválida");
        if(!CATEGORIES.get(type).contains(category)) throw new BadRequestException("La categoría no corresponde al tipo de reporte");
        if(title.length()>200 || category.length()>80 || (subcategory!=null && subcategory.length()>120) || description.length()>2000)
            throw new BadRequestException("El reporte supera la longitud permitida");
        OperatorContext.Assignment assignment=ctx.assignment(assignmentId,employee);
        AssignmentPlanEntity plan=AssignmentPlanEntity.find("id=?1 and instanceCountryId=?2",assignment.assignment().assignmentPlanId,tenant.instanceCountryId()).firstResult();
        if(plan==null || !("PUBLISHED".equals(plan.status)||"CLOSED".equals(plan.status)))
            throw new ForbiddenException("Tu asignación aún no ha sido publicada en SGI COMANDO. Comunícate con tu supervisor.");
        Instant occurred=instant(body,"occurredAt");
        if(occurred.isAfter(Instant.now().plusSeconds(300))) throw new BadRequestException("La fecha del reporte no puede estar en el futuro");
        if(occurred.isBefore(assignment.shift().startsAt.minusSeconds(43200)) || occurred.isAfter(assignment.shift().endsAt.plusSeconds(43200)))
            throw new BadRequestException("La fecha del reporte está fuera de la ventana de la asignación");
        String payloadHash=hash(reportId+"|"+assignmentId+"|"+type+"|"+category+"|"+Objects.toString(subcategory,"")+"|"+title+"|"+description+"|"+severity+"|"+occurred+"|"+photoCount);
        Object[] existing=find(reportId);
        if(existing!=null) {
            if(!employee.toString().equals(existing[1].toString())) throw new ClientErrorException("El identificador del reporte ya está en uso",409);
            if(existing[22]!=null && !payloadHash.equals(existing[22].toString())) throw new ClientErrorException("El reporte ya existe con un contenido diferente",409);
            return response(existing,true);
        }
        String routingTarget=("INCIDENT".equals(type)||"VULNERABILITY".equals(type))?"STC":"NONE";
        String routingStatus="NONE".equals(routingTarget)?"NOT_APPLICABLE":"PENDING_INTEGRATION";
        Instant now=Instant.now();
        em.createNativeQuery("""
          insert into operator_operational_report
          (id,instance_country_id,assignment_id,employee_id,username,point_id,post_id,report_type,category,subcategory,title,description,severity,status,occurred_at,source,created_at,updated_at,payload_hash,routing_target,routing_status,photo_count)
          values (:id,:tenant,:assignment,:employee,:username,:point,:post,:type,:category,:subcategory,:title,:description,:severity,'REPORTED',:occurred,'SGI_OPERADOR_LOCAL',:now,:now,:hash,:routingTarget,:routingStatus,:photoCount)
          """).setParameter("id",reportId).setParameter("tenant",tenant.instanceCountryId())
          .setParameter("assignment",assignmentId).setParameter("employee",employee).setParameter("username",ctx.username())
          .setParameter("point",assignment.point().id).setParameter("post",assignment.post().id).setParameter("type",type)
          .setParameter("category",category).setParameter("subcategory",subcategory).setParameter("title",title)
          .setParameter("description",description).setParameter("severity",severity).setParameter("occurred",occurred)
          .setParameter("hash",payloadHash).setParameter("routingTarget",routingTarget).setParameter("routingStatus",routingStatus)
          .setParameter("photoCount",photoCount)
          .setParameter("now",now).executeUpdate();
        return response(find(reportId),false);
    }

    @GET
    public ArrayNode list(@QueryParam("type") String type) {
        UUID employee=ctx.employee();
        String selected=normalizeType(type);
        return rows(employee,selected);
    }

    ArrayNode rows(UUID employee,String type) { return rows(employee,type,null); }

    /** Las coordenadas no viven en el reporte: se toman de su fotografía (evidence_object), la primera con ubicación. */
    private static final String SELECT="""
          select r.id,r.employee_id,r.report_type,r.category,r.subcategory,r.title,r.description,r.severity,r.status,
                 r.occurred_at,r.created_at,r.assignment_id,r.point_id,r.post_id,coalesce(employee.full_name,r.username),p.name,coalesce(po.name,'—'),
                 p.client_name,p.city,p.company_id,co.code,co.name,r.payload_hash,r.routing_target,r.routing_status,r.routing_reference_id,r.photo_count,
                 ev.latitude,ev.longitude,ev.accuracy_m
          from operator_operational_report r join point p on p.id=r.point_id and p.instance_country_id=r.instance_country_id
          left join employee_operational_snapshot employee on employee.employee_id=r.employee_id and employee.instance_country_id=r.instance_country_id
          left join post po on po.id=r.post_id and po.instance_country_id=r.instance_country_id
          left join company co on co.id=p.company_id and co.instance_country_id=r.instance_country_id
          left join lateral (
            select e.latitude,e.longitude,e.accuracy_m from evidence_object e
            where e.instance_country_id=r.instance_country_id and e.event_id=r.id and e.target_id=r.id and e.target_type='OPERATIONAL_REPORT'
              and e.status in ('STORED','ATTACHED') and e.latitude is not null and e.longitude is not null
            order by e.captured_at,e.id limit 1
          ) ev on true
          where r.instance_country_id=:tenant""";

    ArrayNode rows(UUID employee,String type,Set<UUID> companyIds) {
        if(companyIds!=null && companyIds.isEmpty()) return mapper.createArrayNode();
        String filters=(employee==null?"":" and r.employee_id=:employee")+(type==null?"":" and r.report_type=:type")+(companyIds==null?"":" and p.company_id in (:companyIds)");
        String sql=SELECT+filters+" order by r.occurred_at desc limit 200";
        var query=em.createNativeQuery(sql).setParameter("tenant",tenant.instanceCountryId());
        if(employee!=null) query.setParameter("employee",employee);
        if(type!=null) query.setParameter("type",type);
        if(companyIds!=null) query.setParameter("companyIds",companyIds);
        @SuppressWarnings("unchecked") List<Object[]> data=query.getResultList();
        ArrayNode out=mapper.createArrayNode(); data.forEach(row->out.add(response(row,false))); return out;
    }

    private Object[] find(UUID id) {
        @SuppressWarnings("unchecked") List<Object[]> rows=em.createNativeQuery(SELECT+" and r.id=:id")
          .setParameter("tenant",tenant.instanceCountryId()).setParameter("id",id).getResultList();
        return rows.isEmpty()?null:rows.get(0);
    }
    ObjectNode response(Object[] r,boolean duplicate) {
        ObjectNode x=mapper.createObjectNode().put("reportId",r[0].toString()).put("type",r[2].toString())
          .put("category",r[3].toString()).put("title",r[5].toString()).put("description",r[6].toString())
          .put("severity",r[7].toString()).put("status",r[8].toString()).put("occurredAt",r[9].toString())
          .put("createdAt",r[10].toString()).put("assignmentId",r[11].toString()).put("pointId",r[12].toString())
          .put("username",r[14].toString()).put("pointName",r[15].toString()).put("postName",r[16].toString())
          .put("acknowledged",true).put("duplicate",duplicate);
        if(r[17]!=null)x.put("clientName",r[17].toString()); if(r[18]!=null)x.put("city",r[18].toString());
        if(r[19]!=null)x.put("companyId",r[19].toString()); if(r[20]!=null)x.put("companyCode",r[20].toString()); if(r[21]!=null)x.put("companyName",r[21].toString());
        if(r[23]!=null)x.put("routingTarget",r[23].toString()); if(r[24]!=null)x.put("routingStatus",r[24].toString()); if(r[25]!=null)x.put("routingReferenceId",r[25].toString());
        if(r[26]!=null)x.put("photoCount",((Number)r[26]).intValue());
        if(r[27]!=null&&r[28]!=null)x.put("latitude",((Number)r[27]).doubleValue()).put("longitude",((Number)r[28]).doubleValue());
        if(r[29]!=null)x.put("accuracyM",((Number)r[29]).doubleValue());
        if(r[4]!=null)x.put("subcategory",r[4].toString()); if(r[13]!=null)x.put("postId",r[13].toString()); return x;
    }
    static String normalizeType(String value) { if(value==null||value.isBlank())return null; String x=value.trim().toUpperCase(Locale.ROOT); if(!TYPES.contains(x))throw new BadRequestException("Tipo de reporte inválido"); return x; }
    static String required(JsonNode n,String field){ String x=optional(n,field); if(x==null)throw new BadRequestException(field+" es obligatorio"); return x; }
    static String optional(JsonNode n,String field){ JsonNode v=n==null?null:n.get(field); if(v==null||v.isNull()||v.asText().isBlank())return null; return v.asText().trim(); }
    static UUID uuid(JsonNode n,String field){ try{return UUID.fromString(required(n,field));}catch(IllegalArgumentException e){throw new BadRequestException(field+" debe ser UUID");} }
    static Instant instant(JsonNode n,String field){ try{return Instant.parse(required(n,field));}catch(Exception e){throw new BadRequestException(field+" debe ser ISO-8601");} }
    static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch(Exception e) { throw new IllegalStateException(e); } }
}
