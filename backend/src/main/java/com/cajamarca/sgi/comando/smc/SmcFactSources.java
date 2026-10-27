package com.cajamarca.sgi.comando.smc;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Solo lectura. El resultado CUMPLIDO procede del cierre operativo existente. */
@ApplicationScoped
public class SmcFactSources {
    @Inject EntityManager em;

    public List<SmcFact> candidates(UUID tenant, Instant since, int limit) {
        List<SmcFact> facts = new ArrayList<>();
        List<?> patrols = em.createNativeQuery("""
            select pe.id, pe.employee_id, pe.finished_at, pd.point_id,
                   coalesce(oa.post_id, pp.post_id), oa.shift_occurrence_id,
                   (select count(*) from patrol_checkpoint k
                    where k.patrol_definition_id=pd.id and k.instance_country_id=pe.instance_country_id),
                   (select count(distinct x.checkpoint_id) from patrol_checkpoint_execution x
                    join patrol_checkpoint k on k.id=x.checkpoint_id and k.instance_country_id=x.instance_country_id
                    where x.patrol_execution_id=pe.id and x.instance_country_id=pe.instance_country_id
                      and k.patrol_definition_id=pd.id and x.result='CUMPLIDO'), pd.version_no, employee.persona_id
            from patrol_execution pe
            left join patrol_plan pp on pp.id=pe.patrol_plan_id and pp.instance_country_id=pe.instance_country_id
            join patrol_definition pd on pd.id=coalesce(pe.patrol_definition_id, pp.patrol_definition_id)
                and pd.instance_country_id=pe.instance_country_id
            left join operational_assignment oa on oa.id=pe.assignment_id and oa.instance_country_id=pe.instance_country_id
            left join employee_operational_snapshot employee on employee.employee_id=pe.employee_id
                and employee.instance_country_id=pe.instance_country_id
            where pe.instance_country_id=:tenant and pe.finished_at>=:since and pe.finished_at<=current_timestamp
              and pe.result in ('COMPLETA','INCOMPLETA') and pe.employee_id is not null
              and pd.structure_type='CLOSED' and pd.schedule_type='PROGRAMMED'
              and exists(select 1 from patrol_checkpoint k where k.patrol_definition_id=pd.id
                         and k.instance_country_id=pe.instance_country_id)
              and not exists(select 1 from smc_fact_delivery d where d.instance_country_id=pe.instance_country_id
                  and d.source_type='PATROL_EXECUTION' and d.source_id=pe.id)
            order by pe.finished_at, pe.id
            """).setParameter("tenant", tenant).setParameter("since", since).setMaxResults(limit).getResultList();
        for (Object row : patrols) {
            Object[] r = (Object[]) row;
            long total = ((Number) r[6]).longValue(), completed = ((Number) r[7]).longValue();
            facts.add(new SmcFact(tenant, "PATROL_EXECUTION", uuid(r[0]), uuid(r[1]), "ROUTE_COMPLETION",
                instant(r[2]), SmcFact.percentage(completed, total), uuid(r[3]), uuid(r[4]), uuid(r[5]),
                Map.of("source", "patrol_execution", "sourceId", r[0].toString(),
                    "hitosCumplidos", completed, "hitosTotales", total, "protocolVersion", r[8]), personaId(r[9])));
        }
        List<?> consignments = em.createNativeQuery("""
            select cc.id, cc.employee_id, cc.confirmed_at, c.point_id,
                   coalesce(oa.post_id,c.post_id), oa.shift_occurrence_id, cc.consignment_id, employee.persona_id
            from consignment_compliance cc
            join consignment c on c.id=cc.consignment_id and c.instance_country_id=cc.instance_country_id
            left join operational_assignment oa on oa.id=cc.assignment_id and oa.instance_country_id=cc.instance_country_id
            left join employee_operational_snapshot employee on employee.employee_id=cc.employee_id
                and employee.instance_country_id=cc.instance_country_id
            where cc.instance_country_id=:tenant and cc.confirmed_at>=:since and cc.confirmed_at<=current_timestamp
              and cc.result='NO_CUMPLIDA' and cc.employee_id is not null
              and not exists(select 1 from smc_fact_delivery d where d.instance_country_id=cc.instance_country_id
                  and d.source_type='CONSIGNMENT_COMPLIANCE' and d.source_id=cc.id)
            order by cc.confirmed_at, cc.id
            """).setParameter("tenant", tenant).setParameter("since", since).setMaxResults(limit).getResultList();
        for (Object row : consignments) {
            Object[] r = (Object[]) row;
            facts.add(new SmcFact(tenant, "CONSIGNMENT_COMPLIANCE", uuid(r[0]), uuid(r[1]), "CONSIGNMENT_NOT_DONE",
                instant(r[2]), BigDecimal.ONE, uuid(r[3]), uuid(r[4]), uuid(r[5]),
                Map.of("source", "consignment_compliance", "sourceId", r[0].toString(),
                    "consignmentId", r[6].toString(), "result", "NO_CUMPLIDA"), personaId(r[7])));
        }
        return facts;
    }

    private static Long personaId(Object value) { return value == null ? null : ((Number) value).longValue(); }

    static UUID uuid(Object value) { return value == null ? null : UUID.fromString(value.toString()); }
    static Instant instant(Object value) {
        if (value instanceof Instant i) return i;
        if (value instanceof OffsetDateTime t) return t.toInstant();
        if (value instanceof Timestamp t) return t.toInstant();
        return Instant.parse(value.toString());
    }
}
