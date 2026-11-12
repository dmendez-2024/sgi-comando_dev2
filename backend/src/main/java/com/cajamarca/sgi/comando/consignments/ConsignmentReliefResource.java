package com.cajamarca.sgi.comando.consignments;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.inventory.InventoryService;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

/** Lightweight read model for the Consignas inbox; full payloads are loaded only by detail. */
@Path("/api/consignments/executions/reliefs")
@RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","MONITOR"})
@Produces(MediaType.APPLICATION_JSON)
public class ConsignmentReliefResource {
    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;
    @Inject InventoryService inventory;

    private static final String COLUMNS="""
        select r.id,po.name,pt.name,s.employee_id,s.received_at,coalesce(employee.full_name,s.username),
               r.inventory_result,pt.client_name,pt.city,c.code,c.name,
               s.context_json::jsonb ->> 'incomingEmployeeName',
               r.executed_at,r.unilateral,r.relief_mode,
               (select count(*) from operator_relief_evidence evidence
                 where evidence.instance_country_id=s.instance_country_id and evidence.event_id=s.id)
        """;
    private static final String FROM="""
         from operator_relief_submission s
         join relief_event r on r.id=s.id and r.instance_country_id=s.instance_country_id
         join post po on po.id=r.post_id and po.instance_country_id=s.instance_country_id
         join point pt on pt.id=po.point_id and pt.instance_country_id=s.instance_country_id
         left join company c on c.id=pt.company_id and c.instance_country_id=s.instance_country_id
         left join employee_operational_snapshot employee
           on employee.employee_id=s.employee_id and employee.instance_country_id=s.instance_country_id
         where s.instance_country_id=:tenant and pt.company_id in (:companies)
        """;

    @GET
    public ArrayNode list(@QueryParam("limit") @DefaultValue("100") int limit) {
        Set<UUID> companies=scope.allowedCompanyIdsForCompanyRoles();
        ArrayNode result=mapper.createArrayNode();
        if(companies.isEmpty()) return result;
        for(Object[] row:rows(companies,null,Math.max(1,Math.min(limit,100)))) result.add(summary(row));
        return result;
    }

    @GET @Path("/{id}")
    public ObjectNode detail(@PathParam("id") UUID id) throws JsonProcessingException {
        Set<UUID> companies=scope.allowedCompanyIdsForCompanyRoles();
        if(companies.isEmpty()) throw new NotFoundException("Relevo no encontrado en tu alcance.");
        List<Object[]> records=rows(companies,id,1);
        if(records.isEmpty()) throw new NotFoundException("Relevo no encontrado en tu alcance.");
        Object[] row=records.getFirst();
        ObjectNode result=summary(row);
        result.set("context",mapper.readTree(row[16].toString()));
        result.set("event",mapper.readTree(row[17].toString()));
        result.put("inventoryDeliveryStatus",inventory.reportStatus(id));
        result.putObject("inventoryReport").set("items",inventory.reportItems(id));
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> rows(Set<UUID> companies,UUID id,int limit) {
        String sql=COLUMNS+(id==null?"":",s.context_json,s.payload_json")+FROM
            +(id==null?"":" and s.id=:id")+" order by s.received_at desc,s.id";
        var query=em.createNativeQuery(sql).setParameter("tenant",tenant.instanceCountryId())
            .setParameter("companies",companies).setMaxResults(limit);
        if(id!=null) query.setParameter("id",id);
        return query.getResultList();
    }

    private ObjectNode summary(Object[] r) {
        ObjectNode item=mapper.createObjectNode().put("reliefId",text(r[0])).put("postName",text(r[1]))
            .put("pointName",text(r[2])).put("employeeId",text(r[3])).put("receivedAt",time(r[4]))
            .put("submittedBy",text(r[5])).put("inventoryStatus",r[6]==null?"PENDING_SOURCE":text(r[6]))
            .put("clientName",text(r[7])).put("city",text(r[8])).put("companyCode",text(r[9]))
            .put("companyName",r[10]==null?"—":text(r[10])).put("photoCount",((Number)r[15]).intValue());
        if(r[11]!=null) item.put("incomingEmployeeName",text(r[11]));
        ObjectNode event=item.putObject("event");
        event.put("executedAt",time(r[12]==null?r[4]:r[12])).put("unilateral",Boolean.TRUE.equals(r[13]));
        if(r[14]!=null) event.put("reliefMode",text(r[14]));
        return item;
    }

    private static String text(Object value){return value==null?"":value.toString();}
    private static String time(Object value){
        if(value instanceof Timestamp timestamp)return timestamp.toInstant().toString();
        if(value instanceof OffsetDateTime offset)return offset.toInstant().toString();
        if(value instanceof Instant instant)return instant.toString();
        return value.toString();
    }
}
