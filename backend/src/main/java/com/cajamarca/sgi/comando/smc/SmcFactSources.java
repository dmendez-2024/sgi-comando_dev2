package com.cajamarca.sgi.comando.smc;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import com.cajamarca.sgi.comando.patrols.PatrolDistanceCalculator;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Fuentes operacionales de solo lectura; no adjudica premios ni altera aprobaciones. */
@ApplicationScoped
public class SmcFactSources {
    @Inject EntityManager em;
    static final List<String> TYPES = List.of("PATROL_EXECUTION", "CONSIGNMENT_COMPLIANCE", "IMPULSE_EVALUATION", "PATROL_DISTANCE", "NOVELTY_APPROVAL", "PATROL_NOT_EXECUTED", "DAY_OFF_WORKED", "POINT_RESPONSIBILITY", "SHIFT_PATROL_OMISSION");
    public record Candidate(SmcFact fact, Instant changedAt) {}

    // Una evaluación local puede haberse apoyado en VISINT mock durante UAT.
    // Conservarla en SGI no la convierte en un resultado real exportable a SMC.
    private static final String SIMULATED_IMPULSE_EVIDENCE = """
        exists(select 1 from task_execution tx
            join visual_review vr on vr.task_execution_id=tx.id and vr.instance_country_id=tx.instance_country_id
            left join consignment_evidence ce on ce.id=tx.target_id and ce.instance_country_id=tx.instance_country_id
            left join consignment_compliance cc on cc.id=e.source_id and cc.instance_country_id=e.instance_country_id
            where tx.instance_country_id=e.instance_country_id and vr.simulated
              and ((e.source_type='PATROL' and tx.patrol_execution_id=e.source_id)
                or (e.source_type='LOGBOOK' and tx.target_type='LOGBOOK_FIELD' and tx.group_id=e.source_id)
                or (e.source_type='CONSIGNMENT' and tx.target_type='CONSIGNMENT_EVIDENCE'
                    and tx.group_id=e.assignment_id and ce.consignment_id=cc.consignment_id)))
        """;
    private static final String SIMULATED_PATROL_EVIDENCE = """
        exists(select 1 from task_execution tx
            join visual_review vr on vr.task_execution_id=tx.id and vr.instance_country_id=tx.instance_country_id
            where tx.instance_country_id=pe.instance_country_id and tx.patrol_execution_id=pe.id and vr.simulated)
        """;

    /** También protege entregas pendientes encoladas antes de introducir el filtro. */
    boolean hasSimulatedEvidence(UUID tenant,String sourceType,UUID sourceId) {
        String sql=switch(sourceType) {
            case "IMPULSE_EVALUATION" -> "select exists(select 1 from impulse_evaluation e where e.instance_country_id=:tenant and e.id=:id and " + SIMULATED_IMPULSE_EVIDENCE + ")";
            case "PATROL_EXECUTION" -> "select exists(select 1 from patrol_execution pe where pe.instance_country_id=:tenant and pe.id=:id and " + SIMULATED_PATROL_EVIDENCE + ")";
            default -> null;
        };
        return sql!=null && Boolean.TRUE.equals(em.createNativeQuery(sql).setParameter("tenant",tenant)
            .setParameter("id",sourceId).getSingleResult());
    }

    public List<Candidate> candidates(UUID tenant, String sourceType, Instant since, UUID afterId, Instant until, int limit) {
        if (!TYPES.contains(sourceType)) throw new IllegalArgumentException("Fuente SMC desconocida");
        List<Candidate> facts = new ArrayList<>();
        UUID after = afterId == null ? new UUID(0,0) : afterId;
        if("SHIFT_PATROL_OMISSION".equals(sourceType)) {
            List<?> rows=em.createNativeQuery("""
                select o.id,o.employee_id,o.window_end,o.point_id,o.post_id,o.shift_id,o.created_at,
                       employee.persona_id,o.patrol_definition_id,o.window_start
                from operator_shift_patrol_omission o
                join operator_shift_sync s on s.instance_country_id=o.instance_country_id and s.assignment_id=o.assignment_id
                left join employee_operational_snapshot employee on employee.employee_id=o.employee_id and employee.instance_country_id=o.instance_country_id
                where o.instance_country_id=:tenant and s.completed_at is not null
                  and o.created_at>=:since and o.created_at<:until
                  and (o.created_at>:since or :includeBoundary or o.id>:after)
                  and not exists(select 1 from smc_fact_delivery d where d.instance_country_id=o.instance_country_id
                    and d.source_type='SHIFT_PATROL_OMISSION' and d.source_id=o.id)
                order by o.created_at,o.id
                """).setParameter("tenant",tenant).setParameter("since",since).setParameter("until",until)
                .setParameter("includeBoundary",afterId==null).setParameter("after",after).setMaxResults(limit).getResultList();
            for(Object raw:rows) { Object[] r=(Object[])raw;
                facts.add(new Candidate(new SmcFact(tenant,sourceType,uuid(r[0]),uuid(r[1]),"ROUTE_COMPLETION",instant(r[2]),
                    BigDecimal.ZERO,uuid(r[3]),uuid(r[4]),uuid(r[5]),Map.of("source","operator_shift_patrol_omission",
                    "sourceId",r[0].toString(),"result","NO_EJECUTADA","patrolDefinitionId",r[8].toString(),"windowStart",instant(r[9]).toString()),personaId(r[7])),instant(r[6])));
            }
            return facts;
        }
        if ("POINT_RESPONSIBILITY".equals(sourceType)) {
            List<?> rows=em.createNativeQuery("""
                select pr.id,pr.employee_id,pr.ends_at,pr.point_id,employee.persona_id,
                       attendance.agent_count,attendance.ready_at
                from point_responsibility pr
                join assignment_plan ap on ap.id=pr.assignment_plan_id and ap.instance_country_id=pr.instance_country_id
                    and ap.status in ('PUBLISHED','CLOSED')
                left join employee_operational_snapshot employee on employee.employee_id=pr.employee_id
                    and employee.instance_country_id=pr.instance_country_id
                join lateral (
                    select count(distinct a.id) expected,
                           count(distinct case when re.id is not null then s.assignment_id end) received,
                           count(distinct case when re.id is not null and s.employee_id<>pr.employee_id then s.employee_id end) agent_count,
                           greatest(pr.ends_at,max(s.received_at) filter (where re.id is not null)) ready_at
                    from operational_assignment a
                    join shift_occurrence sh on sh.id=a.shift_occurrence_id and sh.instance_country_id=a.instance_country_id
                    join post p on p.id=sh.post_id and p.instance_country_id=sh.instance_country_id and p.point_id=pr.point_id
                    join employee_operational_snapshot es on es.employee_id=coalesce(a.actual_employee_id,a.employee_id)
                        and es.instance_country_id=a.instance_country_id
                    left join operator_relief_submission s on s.assignment_id=a.id and s.instance_country_id=a.instance_country_id
                        and s.employee_id=coalesce(a.actual_employee_id,a.employee_id)
                    left join relief_event re on re.id=s.id and re.instance_country_id=s.instance_country_id
                        and re.incoming_employee_id=s.employee_id and re.executed_at is not null
                        and re.executed_at>=sh.starts_at and re.executed_at<sh.ends_at
                    where a.instance_country_id=pr.instance_country_id and a.assignment_plan_id=pr.assignment_plan_id
                      and a.status<>'REMOVED' and sh.starts_at<pr.ends_at and sh.ends_at>pr.starts_at
                      and upper(replace(es.role_code,' ','_')) in ('AGENTE_DE_SEGURIDAD','AGENTE_SEGURIDAD')
                ) attendance on attendance.expected>0 and attendance.expected=attendance.received and attendance.agent_count>=1
                where pr.instance_country_id=:tenant and pr.employee_id is not null and pr.ends_at<=:until
                  and attendance.ready_at>=:since and attendance.ready_at<:until
                  and (attendance.ready_at>:since or :includeBoundary or pr.id>:after)
                  and not exists(select 1 from smc_fact_delivery d where d.instance_country_id=pr.instance_country_id
                    and d.source_type='POINT_RESPONSIBILITY' and d.source_id=pr.id)
                order by attendance.ready_at,pr.id
                """).setParameter("tenant",tenant).setParameter("since",since).setParameter("until",until)
                .setParameter("includeBoundary",afterId==null).setParameter("after",after).setMaxResults(limit).getResultList();
            for(Object row:rows) { Object[] r=(Object[])row;
                facts.add(new Candidate(new SmcFact(tenant,sourceType,uuid(r[0]),uuid(r[1]),"POINT_RESPONSIBLE",
                    instant(r[2]),new BigDecimal(r[5].toString()),uuid(r[3]),null,null,
                    Map.of("source","point_responsibility","sourceId",r[0].toString()),personaId(r[4])),instant(r[6])));
            }
            return facts;
        }
        if ("DAY_OFF_WORKED".equals(sourceType)) {
            List<?> rows=em.createNativeQuery("""
                select a.id,a.employee_id,a.occurred_at,a.point_id,a.post_id,a.shift_id,
                       employee.persona_id,a.accredited_at,a.assignment_id,a.rest_date
                from day_off_worked_accreditation a
                left join employee_operational_snapshot employee on employee.employee_id=a.employee_id
                    and employee.instance_country_id=a.instance_country_id
                where a.instance_country_id=:tenant and a.accredited_at>=:since and a.accredited_at<:until
                  and (a.accredited_at>:since or :includeBoundary or a.id>:after)
                  and not exists(select 1 from smc_fact_delivery d where d.instance_country_id=a.instance_country_id
                      and d.source_type='DAY_OFF_WORKED' and d.source_id=a.id)
                order by a.accredited_at,a.id
                """).setParameter("tenant",tenant).setParameter("since",since).setParameter("until",until)
                .setParameter("includeBoundary",afterId==null).setParameter("after",after).setMaxResults(limit).getResultList();
            for(Object row:rows) { Object[] r=(Object[])row;
                facts.add(new Candidate(new SmcFact(tenant,sourceType,uuid(r[0]),uuid(r[1]),"DAY_OFF_WORKED",
                    instant(r[2]),BigDecimal.ONE,uuid(r[3]),uuid(r[4]),uuid(r[5]),
                    Map.of("source","day_off_worked_accreditation","sourceId",r[0].toString(),
                        "assignmentId",r[8].toString(),"restDate",r[9].toString()),personaId(r[6])),instant(r[7])));
            }
            return facts;
        }
        if ("PATROL_NOT_EXECUTED".equals(sourceType)) {
            List<?> rows=em.createNativeQuery("""
                select o.id,responsible.employee_id,o.window_end,pd.point_id,pp.post_id,
                       responsible.shift_occurrence_id,employee.persona_id,o.updated_at,pd.version_no,
                       o.window_start,responsible.id
                from patrol_occurrence o
                join patrol_plan pp on pp.id=o.patrol_plan_id and pp.instance_country_id=o.instance_country_id
                join patrol_definition pd on pd.id=pp.patrol_definition_id and pd.instance_country_id=o.instance_country_id
                join lateral (
                    select a.id,coalesce(a.actual_employee_id,a.employee_id) employee_id,a.shift_occurrence_id,
                           count(*) over () candidate_count
                    from operational_assignment a
                    join shift_occurrence s on s.id=a.shift_occurrence_id and s.instance_country_id=a.instance_country_id
                    join assignment_plan ap on ap.id=a.assignment_plan_id and ap.instance_country_id=a.instance_country_id
                    where a.instance_country_id=o.instance_country_id and s.post_id=pp.post_id
                      and a.status<>'REMOVED' and ap.status in ('PUBLISHED','CLOSED')
                      and coalesce(a.actual_employee_id,a.employee_id) is not null
                      and s.starts_at<=o.window_start and s.ends_at>=o.window_end
                      and coalesce(a.actual_assigned_at,a.assigned_at,a.created_at)<=o.window_start
                ) responsible on responsible.candidate_count=1
                left join employee_operational_snapshot employee on employee.employee_id=responsible.employee_id
                    and employee.instance_country_id=o.instance_country_id
                where o.instance_country_id=:tenant and o.status='NO_EJECUTADA' and o.execution_id is null
                  and o.window_start<o.window_end and o.window_end<:until and o.updated_at>=o.window_end
                  and o.updated_at>=:since and o.updated_at<:until
                  and (o.updated_at>:since or :includeBoundary or o.id>:after)
                  and pd.structure_type='CLOSED' and pd.schedule_type='PROGRAMMED'
                  and not exists(select 1 from patrol_execution pe where pe.instance_country_id=o.instance_country_id
                      and (pe.occurrence_id=o.id or (pe.assignment_id=responsible.id
                          and (pe.patrol_plan_id=pp.id or pe.patrol_definition_id=pd.id)
                          and pe.started_at>=o.window_start and pe.started_at<o.window_end)))
                  and not exists(select 1 from smc_fact_delivery d where d.instance_country_id=o.instance_country_id
                      and d.source_type='PATROL_NOT_EXECUTED' and d.source_id=o.id)
                order by o.updated_at,o.id
                """).setParameter("tenant",tenant).setParameter("since",since).setParameter("until",until)
                .setParameter("includeBoundary",afterId==null).setParameter("after",after).setMaxResults(limit).getResultList();
            for(Object row:rows) { Object[] r=(Object[])row;
                facts.add(new Candidate(new SmcFact(tenant,sourceType,uuid(r[0]),uuid(r[1]),"ROUTE_COMPLETION",
                    instant(r[2]),BigDecimal.ZERO,uuid(r[3]),uuid(r[4]),uuid(r[5]),
                    Map.of("source","patrol_occurrence","sourceId",r[0].toString(),"result","NO_EJECUTADA",
                        "protocolVersion",r[8],"windowStart",instant(r[9]).toString(),
                        "assignmentId",r[10].toString()),personaId(r[6])),instant(r[7])));
            }
            return facts;
        }
        if ("NOVELTY_APPROVAL".equals(sourceType)) {
            List<?> rows=em.createNativeQuery("""
                select r.id,r.employee_id,a.approved_at,r.point_id,r.post_id,oa.shift_occurrence_id,
                       employee.persona_id,r.report_type,a.decision_id
                from operator_operational_report r
                join lateral (
                    select d.created_at approved_at,d.id decision_id
                    from operator_operational_report_decision d
                    where d.instance_country_id=r.instance_country_id and d.report_id=r.id
                      and d.action='APPROVE' and d.resulting_status='APPROVED'
                    order by d.created_at,d.id limit 1
                ) a on true
                left join operational_assignment oa on oa.id=r.assignment_id and oa.instance_country_id=r.instance_country_id
                left join employee_operational_snapshot employee on employee.employee_id=r.employee_id
                    and employee.instance_country_id=r.instance_country_id
                where r.instance_country_id=:tenant and r.status='APPROVED' and r.employee_id is not null
                  and a.approved_at>=:since and a.approved_at<:until
                  and (a.approved_at>:since or :includeBoundary or r.id>:after)
                  and not exists(select 1 from smc_fact_delivery d where d.instance_country_id=r.instance_country_id
                      and d.source_type='NOVELTY_APPROVAL' and d.source_id=r.id)
                order by a.approved_at,r.id
                """).setParameter("tenant",tenant).setParameter("since",since).setParameter("until",until)
                .setParameter("includeBoundary",afterId==null).setParameter("after",after).setMaxResults(limit).getResultList();
            for(Object row:rows) { Object[] r=(Object[])row;
                facts.add(new Candidate(new SmcFact(tenant,sourceType,uuid(r[0]),uuid(r[1]),"NOVELTY_APPROVED",
                    instant(r[2]),BigDecimal.ONE,uuid(r[3]),uuid(r[4]),uuid(r[5]),
                    Map.of("source","operator_operational_report","sourceId",r[0].toString(),
                        "approvalDecisionId",r[8].toString(),"reportType",r[7].toString()),personaId(r[6])),instant(r[2])));
            }
            return facts;
        }
        if ("PATROL_DISTANCE".equals(sourceType)) {
            List<?> rows=em.createNativeQuery("""
                select m.id,m.employee_id,m.occurred_at,m.measured_at,m.kilometers,m.sample_count,
                       m.accepted_segments,m.rejected_segments,m.max_accuracy_m,m.max_gap_seconds,m.algorithm_version,
                       employee.persona_id,p.point_id,so.post_id,oa.shift_occurrence_id
                from patrol_distance_measurement m
                join patrol_execution pe on pe.id=m.id and pe.instance_country_id=m.instance_country_id
                left join employee_operational_snapshot employee on employee.employee_id=m.employee_id and employee.instance_country_id=m.instance_country_id
                left join operational_assignment oa on oa.id=pe.assignment_id and oa.instance_country_id=m.instance_country_id
                left join shift_occurrence so on so.id=oa.shift_occurrence_id and so.instance_country_id=m.instance_country_id
                left join post p on p.id=so.post_id and p.instance_country_id=m.instance_country_id
                where m.instance_country_id=:tenant and m.measured_at>=:since and m.measured_at<:until
                  and (m.measured_at>:since or :includeBoundary or m.id>:after) and m.accepted_segments>0
                  and not exists(select 1 from smc_fact_delivery d where d.instance_country_id=m.instance_country_id
                      and d.source_type='PATROL_DISTANCE' and d.source_id=m.id)
                order by m.measured_at,m.id
                """).setParameter("tenant",tenant).setParameter("since",since).setParameter("until",until)
                .setParameter("includeBoundary",afterId==null).setParameter("after",after).setMaxResults(limit).getResultList();
            for(Object row:rows){Object[] r=(Object[])row;
                facts.add(new Candidate(new SmcFact(tenant,sourceType,uuid(r[0]),uuid(r[1]),"PATROL_KM",instant(r[2]),
                    (BigDecimal)r[4],uuid(r[12]),uuid(r[13]),uuid(r[14]),
                    Map.of("source","patrol_distance_measurement","sourceId",r[0].toString(),"sampleCount",r[5],
                        "acceptedSegments",r[6],"rejectedSegments",r[7],"maxAccuracyM",r[8],"maxGapSeconds",r[9],
                        "algorithmVersion",r[10],"maxSpeedKmh",PatrolDistanceCalculator.MAX_SPEED_KMH),personaId(r[11])),instant(r[3])));
            }
            return facts;
        }
        if ("IMPULSE_EVALUATION".equals(sourceType)) {
            List<?> rows = em.createNativeQuery("""
                select e.id, e.employee_id, e.occurred_at, e.evaluated_at, e.rule_code,
                       e.source_type, e.source_id, employee.persona_id, so.post_id, oa.shift_occurrence_id, p.point_id, e.rule_version
                from impulse_evaluation e
                left join employee_operational_snapshot employee on employee.employee_id=e.employee_id
                    and employee.instance_country_id=e.instance_country_id
                left join operational_assignment oa on oa.id=e.assignment_id and oa.instance_country_id=e.instance_country_id
                left join shift_occurrence so on so.id=oa.shift_occurrence_id and so.instance_country_id=oa.instance_country_id
                left join post p on p.id=so.post_id and p.instance_country_id=e.instance_country_id
                where e.instance_country_id=:tenant and e.evaluated_at>=:since and e.evaluated_at<:until
                  and e.employee_id is not null
                  and (e.evaluated_at>:since or :includeBoundary or e.id>:after)
                  and e.decision in ('AWARDED','NO_AWARD')
                  and ((e.rule_code in ('REL_A_TIEMPO','REL_FOTO_CUERPO') and e.source_type='RELIEF')
                    or (e.rule_code='BIT_CA_CORRECTO' and e.source_type='LOGBOOK')
                    or (e.rule_code='PAT_CORRECTA' and e.source_type='PATROL')
                    or (e.rule_code='CON_CORRECTA' and e.source_type='CONSIGNMENT'))
                  and not exists(select 1 from smc_fact_delivery d where d.instance_country_id=e.instance_country_id
                      and d.source_type='IMPULSE_EVALUATION' and d.source_id=e.id)
                and not (
                """ + SIMULATED_IMPULSE_EVIDENCE + """
                ) order by e.evaluated_at,e.id
                """).setParameter("tenant", tenant).setParameter("since", since).setParameter("until", until)
                .setParameter("includeBoundary", afterId == null).setParameter("after", after).setMaxResults(limit).getResultList();
            for (Object row : rows) {
                Object[] r = (Object[]) row;
                String factType = switch (r[4].toString()) {
                    case "REL_A_TIEMPO" -> "RELIEF_COMPLETED_ON_TIME";
                    case "REL_FOTO_CUERPO" -> "RELIEF_FULL_BODY_PHOTO";
                    case "BIT_CA_CORRECTO" -> "ACCESS_CONTROL_OK";
                    case "PAT_CORRECTA" -> "PATROL_OK";
                    case "CON_CORRECTA" -> "CONSIGNMENT_OK";
                    default -> throw new IllegalStateException("Regla de impulso desconocida");
                };
                facts.add(new Candidate(new SmcFact(tenant, sourceType, uuid(r[0]), uuid(r[1]), factType,
                    instant(r[2]), BigDecimal.ONE, uuid(r[10]), uuid(r[8]), uuid(r[9]),
                    Map.of("source", r[5].toString(), "sourceId", r[6].toString(),
                        "evaluationId", r[0].toString(), "ruleCode", r[4].toString(), "ruleVersion", r[11]), personaId(r[7])), instant(r[3])));
            }
            return facts;
        }
        if ("PATROL_EXECUTION".equals(sourceType)) {
        List<?> patrols = em.createNativeQuery("""
            select pe.id, pe.employee_id, pe.finished_at, pd.point_id,
                   coalesce(so.post_id, pp.post_id), oa.shift_occurrence_id,
                   (select count(*) from patrol_checkpoint k
                    where k.patrol_definition_id=pd.id and k.instance_country_id=pe.instance_country_id),
                   (select count(distinct x.checkpoint_id) from patrol_checkpoint_execution x
                    join patrol_checkpoint k on k.id=x.checkpoint_id and k.instance_country_id=x.instance_country_id
                    where x.patrol_execution_id=pe.id and x.instance_country_id=pe.instance_country_id
                      and k.patrol_definition_id=pd.id and x.result='CUMPLIDO'), pd.version_no, employee.persona_id, greatest(pe.updated_at,sync.completed_at),pe.result
            from patrol_execution pe
            left join patrol_plan pp on pp.id=pe.patrol_plan_id and pp.instance_country_id=pe.instance_country_id
            join patrol_definition pd on pd.id=coalesce(pe.patrol_definition_id, pp.patrol_definition_id)
                and pd.instance_country_id=pe.instance_country_id
            left join operational_assignment oa on oa.id=pe.assignment_id and oa.instance_country_id=pe.instance_country_id
            left join shift_occurrence so on so.id=oa.shift_occurrence_id and so.instance_country_id=oa.instance_country_id
            left join employee_operational_snapshot employee on employee.employee_id=pe.employee_id
                and employee.instance_country_id=pe.instance_country_id
            left join operator_shift_sync sync on sync.assignment_id=pe.assignment_id and sync.instance_country_id=pe.instance_country_id
            where pe.instance_country_id=:tenant and pe.finished_at is not null
              and (sync.assignment_id is null or sync.completed_at is not null)
              and greatest(pe.updated_at,sync.completed_at)>=:since and greatest(pe.updated_at,sync.completed_at)<:until
              and (greatest(pe.updated_at,sync.completed_at)>:since or :includeBoundary or pe.id>:after)
              and pe.result in ('COMPLETA','INCOMPLETA','NO_EJECUTADA') and pe.employee_id is not null
              and pd.structure_type='CLOSED' and pd.schedule_type='PROGRAMMED'
              and (pe.result='NO_EJECUTADA' or exists(select 1 from patrol_checkpoint k where k.patrol_definition_id=pd.id
                         and k.instance_country_id=pe.instance_country_id))
              and not exists(select 1 from smc_fact_delivery d where d.instance_country_id=pe.instance_country_id
                  and d.source_type='PATROL_EXECUTION' and d.source_id=pe.id)
            and not (
            """ + SIMULATED_PATROL_EVIDENCE + """
            ) order by greatest(pe.updated_at,sync.completed_at), pe.id
            """).setParameter("tenant", tenant).setParameter("since", since).setParameter("until", until)
            .setParameter("includeBoundary", afterId == null).setParameter("after", after).setMaxResults(limit).getResultList();
        for (Object row : patrols) {
            Object[] r = (Object[]) row;
            long total = ((Number) r[6]).longValue(), completed = ((Number) r[7]).longValue();
            boolean notExecuted="NO_EJECUTADA".equals(r[11]);
            facts.add(new Candidate(new SmcFact(tenant, "PATROL_EXECUTION", uuid(r[0]), uuid(r[1]), "ROUTE_COMPLETION",
                instant(r[2]), notExecuted?BigDecimal.ZERO:SmcFact.percentage(completed, total), uuid(r[3]), uuid(r[4]), uuid(r[5]),
                Map.of("source", "patrol_execution", "sourceId", r[0].toString(),
                    "hitosCumplidos", notExecuted?0L:completed, "hitosTotales", total, "protocolVersion", r[8]), personaId(r[9])), instant(r[10])));
        }
        return facts;
        }
        List<?> consignments = em.createNativeQuery("""
            select cc.id, cc.employee_id, coalesce(cc.executed_at,cc.occurred_at,cc.confirmed_at), c.point_id,
                   coalesce(so.post_id,c.post_id), oa.shift_occurrence_id, cc.consignment_id, employee.persona_id, greatest(cc.created_at,sync.completed_at)
            from consignment_compliance cc
            join consignment c on c.id=cc.consignment_id and c.instance_country_id=cc.instance_country_id
            left join operational_assignment oa on oa.id=cc.assignment_id and oa.instance_country_id=cc.instance_country_id
            left join shift_occurrence so on so.id=oa.shift_occurrence_id and so.instance_country_id=oa.instance_country_id
            left join employee_operational_snapshot employee on employee.employee_id=cc.employee_id
                and employee.instance_country_id=cc.instance_country_id
            left join operator_shift_sync sync on sync.assignment_id=cc.assignment_id and sync.instance_country_id=cc.instance_country_id
            where cc.instance_country_id=:tenant and (sync.assignment_id is null or sync.completed_at is not null)
              and greatest(cc.created_at,sync.completed_at)>=:since and greatest(cc.created_at,sync.completed_at)<:until
              and (greatest(cc.created_at,sync.completed_at)>:since or :includeBoundary or cc.id>:after)
              and cc.result='NO_CUMPLIDA' and cc.employee_id is not null
              and not exists(select 1 from smc_fact_delivery d where d.instance_country_id=cc.instance_country_id
                  and d.source_type='CONSIGNMENT_COMPLIANCE' and d.source_id=cc.id)
            order by greatest(cc.created_at,sync.completed_at), cc.id
            """).setParameter("tenant", tenant).setParameter("since", since).setParameter("until", until)
            .setParameter("includeBoundary", afterId == null).setParameter("after", after).setMaxResults(limit).getResultList();
        for (Object row : consignments) {
            Object[] r = (Object[]) row;
            facts.add(new Candidate(new SmcFact(tenant, "CONSIGNMENT_COMPLIANCE", uuid(r[0]), uuid(r[1]), "CONSIGNMENT_NOT_DONE",
                instant(r[2]), BigDecimal.ONE, uuid(r[3]), uuid(r[4]), uuid(r[5]),
                Map.of("source", "consignment_compliance", "sourceId", r[0].toString(),
                    "consignmentId", r[6].toString(), "result", "NO_CUMPLIDA"), personaId(r[7])), instant(r[8])));
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
