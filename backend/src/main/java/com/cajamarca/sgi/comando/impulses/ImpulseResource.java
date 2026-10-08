package com.cajamarca.sgi.comando.impulses;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.math.BigDecimal;
import java.util.*;

/** Impulsos para los roles de SGI: Comando: reglas vigentes, evaluaciones y saldo de un Operador. */
@Produces(MediaType.APPLICATION_JSON)
@Path("/api/impulses")
public class ImpulseResource {
    @Inject TenantContext tenant;
    @Inject ImpulseLedger ledger;
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;

    @GET @Path("/employees/{employeeId}")
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
    public ObjectNode employee(@PathParam("employeeId") UUID employeeId) {
        return ledger.summary(tenant.instanceCountryId(), employeeId);
    }

    @GET @Path("/rules")
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
    @SuppressWarnings("unchecked")
    public ArrayNode rules() {
        ArrayNode out = mapper.createArrayNode();
        for (Object[] r : (List<Object[]>) em.createNativeQuery("""
              select code,version_no,skill_code,action_code,description,source_type,requires_visint_pass,probability,amount_min,amount_max,reference_average,params_json,active,effective_from
              from impulse_rule where instance_country_id=:t order by skill_code,code,version_no desc""")
            .setParameter("t", tenant.instanceCountryId()).getResultList()) {
            out.addObject().put("code", r[0].toString()).put("version", ((Number) r[1]).intValue()).put("skillCode", r[2].toString())
                .put("skillName", ImpulseSkills.name(r[2].toString())).put("actionCode", r[3].toString()).put("description", r[4].toString())
                .put("sourceType", r[5].toString()).put("requiresVisintPass", Boolean.TRUE.equals(r[6])).put("probability", ((BigDecimal) r[7]).doubleValue())
                .put("amountMin", ((BigDecimal) r[8]).doubleValue()).put("amountMax", ((BigDecimal) r[9]).doubleValue())
                .put("referenceAverage", r[10] == null ? null : ((BigDecimal) r[10]).doubleValue()).put("params", r[11].toString())
                .put("active", Boolean.TRUE.equals(r[12])).put("effectiveFrom", r[13].toString());
        }
        return out;
    }

    @GET @Path("/evaluations")
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
    @SuppressWarnings("unchecked")
    public ArrayNode evaluations(@QueryParam("employeeId") UUID employeeId, @QueryParam("limit") @DefaultValue("100") int limit) {
        ArrayNode out = mapper.createArrayNode();
        var query = em.createNativeQuery("""
              select e.id,e.employee_id,s.full_name,e.skill_code,e.rule_code,e.rule_version,r.description,e.source_type,e.source_id,e.decision,e.reason,
                     e.draw,e.amount,e.visint_status,e.occurred_at,e.evaluated_at
              from impulse_evaluation e join impulse_rule r on r.id=e.rule_id
              left join employee_operational_snapshot s on s.employee_id=e.employee_id and s.instance_country_id=e.instance_country_id
              where e.instance_country_id=:t and (cast(:employee as uuid) is null or e.employee_id=cast(:employee as uuid))
              order by e.evaluated_at desc""").setParameter("t", tenant.instanceCountryId()).setParameter("employee", employeeId == null ? null : employeeId.toString());
        for (Object[] r : (List<Object[]>) query.setMaxResults(Math.max(1, Math.min(500, limit))).getResultList()) {
            out.addObject().put("evaluationId", r[0].toString()).put("employeeId", r[1].toString()).put("employeeName", r[2] == null ? "" : r[2].toString())
                .put("skillCode", r[3].toString()).put("skillName", ImpulseSkills.name(r[3].toString())).put("ruleCode", r[4].toString())
                .put("ruleVersion", ((Number) r[5]).intValue()).put("description", r[6].toString()).put("sourceType", r[7].toString())
                .put("sourceId", r[8].toString()).put("decision", r[9].toString()).put("reason", r[10] == null ? "" : r[10].toString())
                .put("draw", r[11] == null ? null : ((BigDecimal) r[11]).doubleValue()).put("amount", ((BigDecimal) r[12]).doubleValue())
                .put("visintStatus", r[13] == null ? "" : r[13].toString()).put("occurredAt", r[14].toString()).put("evaluatedAt", r[15].toString());
        }
        return out;
    }
}
