package com.cajamarca.sgi.comando.dashboard;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.hibernate.query.NativeQuery;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Aggregates stored records; missing execution sources are not inferred from planning. */
@Path("/api/dashboard")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
public class DashboardResource {
    private static final ZoneId OPERATING_ZONE = ZoneId.of("America/Guayaquil");
    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject EntityManager em;

    public record Metrics(LocalDate weekStart, Instant calculatedAt, Double idAverage, Double icAverage,
                          long idSamples, long icSamples, long lateReliefs, long recordedReliefs,
                          long reliefsOverThreeHours, long previousLateReliefs) {}
    public record OperationalCoverage(LocalDate weekStart, long uncoveredPoints, long evaluatedPoints,
                                      long uncoveredShifts, long evaluatedShifts, Double coveragePct,
                                      long previousUncoveredPoints, long previousEvaluatedPoints) {}
    public record Suitability(long pointsBelowIdMinimum, long idEvaluatedPoints, Double idGapPct,
                              long pointsBelowFullCompatibility, long icEvaluatedPoints, Double icGapPct) {}
    public record ConsignmentAttention(UUID pointId, String clientName, String pointName,
                                       LocalDate date, long nonCompliant) {}
    public record ConsignmentCompliance(long nonCompliant, long activeConsignments, long expectedExecutions, Double nonCompliancePct,
                                        List<ConsignmentAttention> attention) {}
    public record SupervisionRouteCompliance(long nonCompliantRoutes, long evaluatedRoutes, Double nonCompliancePct) {}
    public record MissedReliefs(long missedShifts, long assignedShifts) {}
    public record CriticalIncidents(long reportedCriticalIncidents) {}
    public record RiskTrendPoint(LocalDate date, long scheduledPoints, long uncoveredPoints, Double riskIndex) {}

    @GET @Path("/suitability")
    public Suitability suitability(@QueryParam("weekStart") String weekStart,
            @QueryParam("companyId") UUID companyId, @QueryParam("clientId") UUID clientId) {
        LocalDate week = parseWeek(weekStart);
        Set<UUID> allowed = scope.allowedCompanyIds();
        if (companyId != null) { scope.requireCompany(companyId); allowed = Set.of(companyId); }
        if (allowed.isEmpty()) return new Suitability(0, 0, null, 0, 0, null);
        String sql = """
            select count(distinct p.id) filter (where a.id_score is not null and
                    a.id_score < case upper(trim(coalesce(po.tier,'')))
                        when 'IV' then 9.5 when 'III' then 8.5 when 'II' then 7.5 else 6.5 end),
                   count(distinct p.id) filter (where a.id_score is not null),
                   count(distinct p.id) filter (where a.compatibility_index is not null and a.compatibility_index < 100),
                   count(distinct p.id) filter (where a.compatibility_index is not null)
            from operational_assignment a
            join shift_occurrence sh on sh.id=a.shift_occurrence_id and sh.instance_country_id=a.instance_country_id
            join post po on po.id=sh.post_id and po.instance_country_id=sh.instance_country_id
            join point p on p.id=po.point_id and p.instance_country_id=sh.instance_country_id
            join service sv on sv.id=p.service_id and sv.instance_country_id=p.instance_country_id
            where a.instance_country_id=:tenant and p.company_id in (:companies) and p.status='ACTIVE'
              and a.status<>'REMOVED' and sh.required=true and sh.starts_at>=:from and sh.starts_at<:to
              and (p.operational_transition_until is null or sh.starts_at>=p.operational_transition_until)
            """ + (clientId == null ? "" : " and sv.client_id=:client");
        Object[] values = (Object[]) query(sql, allowed, clientId,
            week.atStartOfDay(OPERATING_ZONE).toInstant(), week.plusDays(7).atStartOfDay(OPERATING_ZONE).toInstant()).getSingleResult();
        long belowId = ((Number) values[0]).longValue(), evaluatedId = ((Number) values[1]).longValue();
        long belowIc = ((Number) values[2]).longValue(), evaluatedIc = ((Number) values[3]).longValue();
        return new Suitability(belowId, evaluatedId, percentage(belowId, evaluatedId),
            belowIc, evaluatedIc, percentage(belowIc, evaluatedIc));
    }

    @GET @Path("/consignment-compliance")
    public ConsignmentCompliance consignmentCompliance(@QueryParam("weekStart") String weekStart,
            @QueryParam("companyId") UUID companyId, @QueryParam("clientId") UUID clientId,
            @QueryParam("period") String period) {
        LocalDate week = parseWeek(weekStart);
        ConsignmentWindow window = consignmentWindow(week, period, LocalDate.now(OPERATING_ZONE));
        Set<UUID> allowed = scope.allowedCompanyIds();
        if (companyId != null) { scope.requireCompany(companyId); allowed = Set.of(companyId); }
        if (allowed.isEmpty()) return new ConsignmentCompliance(0, 0, 0, null, List.of());
        Instant now = Instant.now();
        String activeSql = """
            select count(*) from consignment c
            join consignment_protocol cp on cp.id=c.protocol_id and cp.instance_country_id=c.instance_country_id
                and cp.point_id=c.point_id
            join point p on p.id=c.point_id and p.instance_country_id=c.instance_country_id
            join service sv on sv.id=p.service_id and sv.instance_country_id=p.instance_country_id
            where c.instance_country_id=:tenant and p.company_id in (:companies) and p.status='ACTIVE'
              and cp.status='ACTIVO' and c.status='VIGENTE'
              and (c.validity_from is null or c.validity_from<:to)
              and (c.validity_until is null or c.validity_until>=:from)
            """ + (clientId == null ? "" : " and sv.client_id=:client");
        long active = ((Number) query(activeSql, allowed, clientId,
            window.from(), window.to()).getSingleResult()).longValue();

        // An expected execution is one active consignment in one assigned, finished shift.
        // Calendar consignments can create more than one due window in a long shift.
        String slotsSql = """
            with candidate_slots as (
                select c.id as consignment_id, sh.id as shift_id, sh.instance_country_id,
                       p.id as point_id, p.client_name, p.name as point_name,
                       sh.starts_at as shift_start, sh.ends_at as shift_end,
                       case when c.application_type='CALENDAR'
                           then (d.day::date+c.application_time_from) at time zone 'America/Guayaquil'
                           else sh.starts_at end as window_start,
                       case when c.application_type='CALENDAR'
                           then (d.day::date+c.application_time_to
                               + case when c.application_time_to<=c.application_time_from
                                   then interval '1 day' else interval '0 day' end)
                               at time zone 'America/Guayaquil'
                           else sh.ends_at end as due_at
                from consignment c
                join consignment_protocol cp on cp.id=c.protocol_id and cp.instance_country_id=c.instance_country_id
                    and cp.point_id=c.point_id and cp.status='ACTIVO'
                join point p on p.id=c.point_id and p.instance_country_id=c.instance_country_id and p.status='ACTIVE'
                join service sv on sv.id=p.service_id and sv.instance_country_id=p.instance_country_id
                join post po on po.point_id=p.id and po.instance_country_id=p.instance_country_id
                join shift_occurrence sh on sh.post_id=po.id and sh.instance_country_id=po.instance_country_id
                    and sh.required=true and sh.starts_at<:to and sh.ends_at>=:from and sh.ends_at<=:now
                cross join lateral generate_series(
                    (sh.starts_at at time zone 'America/Guayaquil')::date,
                    ((sh.ends_at-interval '1 microsecond') at time zone 'America/Guayaquil')::date,
                    interval '1 day') as d(day)
                where c.instance_country_id=:tenant and p.company_id in (:companies) and c.status='VIGENTE'
                  and (c.scope_type='POINT' or exists (
                      select 1 from consignment_post_scope cps
                      where cps.consignment_id=c.id and cps.instance_country_id=c.instance_country_id
                        and cps.post_id=po.id))
                  and exists (select 1 from operational_assignment a
                      where a.shift_occurrence_id=sh.id and a.instance_country_id=sh.instance_country_id
                        and a.status<>'REMOVED' and coalesce(a.actual_employee_id,a.employee_id) is not null)
                  and (c.application_type='ALL_TIME' and d.day::date=(sh.starts_at at time zone 'America/Guayaquil')::date
                    or c.application_type='CALENDAR' and c.application_time_from is not null
                        and c.application_time_to is not null
                        and jsonb_exists(c.application_days_json::jsonb, case extract(isodow from d.day::date)::int
                            when 1 then 'MON' when 2 then 'TUE' when 3 then 'WED' when 4 then 'THU'
                            when 5 then 'FRI' when 6 then 'SAT' else 'SUN' end))
            """ + (clientId == null ? "" : " and sv.client_id=:client\n") + """
            ), due_slots as (
                select * from candidate_slots
                where window_start>=shift_start and due_at<=shift_end and window_start<due_at
                  and due_at>=:from and due_at<:to and due_at<=:now
                  and exists (select 1 from consignment c where c.id=consignment_id
                      and (c.validity_from is null or c.validity_from<=due_at)
                      and (c.validity_until is null or c.validity_until>=window_start))
            ), assessed as (
                select s.point_id,s.client_name,s.point_name,s.due_at,
                       (coalesce(confirmations.rejected,false) or coalesce(photos.rejected,false)
                           or (confirmations.total=0 and photos.total=0)) as failed
                from due_slots s
                left join lateral (
                    select count(*) as total, bool_or(cc.result='NO_CUMPLIDA') as rejected
                    from consignment_compliance cc
                    join operational_assignment a on a.id=cc.assignment_id
                        and a.instance_country_id=cc.instance_country_id
                    where cc.consignment_id=s.consignment_id and cc.instance_country_id=s.instance_country_id
                      and a.shift_occurrence_id=s.shift_id
                      and cc.confirmed_at>=s.window_start and cc.confirmed_at<=s.due_at
                ) confirmations on true
                left join lateral (
                    select count(*) as total, bool_or(vr.status='FAILED') as rejected
                    from consignment_evidence ce
                    join task_execution te on te.target_id=ce.id and te.instance_country_id=ce.instance_country_id
                        and te.target_type='CONSIGNMENT_EVIDENCE' and te.shift_occurrence_id=s.shift_id
                    left join visual_review vr on vr.task_execution_id=te.id and vr.instance_country_id=te.instance_country_id
                        and coalesce(vr.reviewed_at,vr.created_at)<:to
                    where ce.consignment_id=s.consignment_id and ce.instance_country_id=s.instance_country_id
                      and te.executed_at>=s.window_start and te.executed_at<=s.due_at
                ) photos on true
            )
            """;
        Object[] counts = (Object[]) query(slotsSql + "select count(*) filter (where failed),count(*) from assessed",
            allowed, clientId, window.from(), window.to()).setParameter("now", now).getSingleResult();
        long nonCompliant = ((Number) counts[0]).longValue();
        long expected = ((Number) counts[1]).longValue();
        String attentionSql = slotsSql + """
            select point_id,client_name,point_name,
                   to_char(max(due_at) at time zone 'America/Guayaquil','YYYY-MM-DD'),count(*)
            from assessed where failed
            group by point_id,client_name,point_name
            order by count(*) desc,max(due_at) desc,point_id
            """;
        List<ConsignmentAttention> attention = new ArrayList<>();
        for (Object result : query(attentionSql, allowed, clientId,
                window.from(), window.to()).setParameter("now", now).getResultList()) {
            Object[] row = (Object[]) result;
            attention.add(new ConsignmentAttention((UUID) row[0], (String) row[1], (String) row[2],
                LocalDate.parse((String) row[3]), ((Number) row[4]).longValue()));
        }
        return new ConsignmentCompliance(nonCompliant, active, expected,
            percentage(nonCompliant, expected), attention);
    }

    record ConsignmentWindow(Instant from, Instant to) {}

    static ConsignmentWindow consignmentWindow(LocalDate week, String period, LocalDate today) {
        // Existing API callers without a period retain the weekly contract.
        if (period == null) return new ConsignmentWindow(week.atStartOfDay(OPERATING_ZONE).toInstant(),
            week.plusDays(7).atStartOfDay(OPERATING_ZONE).toInstant());
        LocalDate end = week.plusDays(6).isAfter(today) ? today : week.plusDays(6);
        LocalDate start = switch (period) {
            case "7d" -> end.minusDays(6);
            case "30d" -> end.minusDays(29);
            case "6m" -> end.minusMonths(6).plusDays(1);
            default -> throw new BadRequestException("period debe ser 7d, 30d o 6m.");
        };
        return new ConsignmentWindow(start.atStartOfDay(OPERATING_ZONE).toInstant(),
            end.plusDays(1).atStartOfDay(OPERATING_ZONE).toInstant());
    }

    @GET @Path("/operational-coverage")
    public OperationalCoverage operationalCoverage(@QueryParam("weekStart") String weekStart,
            @QueryParam("companyId") UUID companyId, @QueryParam("clientId") UUID clientId) {
        LocalDate week = parseWeek(weekStart);
        Set<UUID> allowed = scope.allowedCompanyIds();
        if (companyId != null) { scope.requireCompany(companyId); allowed = Set.of(companyId); }
        if (allowed.isEmpty()) return new OperationalCoverage(week, 0, 0, 0, 0, null, 0, 0);
        Instant now = Instant.now();
        long[] current = coverageCounts(week, allowed, clientId, now);
        long[] previous = coverageCounts(week.minusWeeks(1), allowed, clientId, now);
        return new OperationalCoverage(week, current[0], current[1], current[2], current[3],
            percentage(current[3] - current[2], current[3]), previous[0], previous[1]);
    }

    private long[] coverageCounts(LocalDate week, Set<UUID> allowed, UUID clientId, Instant now) {
        String sql = """
            with evaluated_shifts as (
                select sh.id, sh.instance_country_id, sh.post_id, sh.starts_at, sh.ends_at, p.id as point_id,
                       exists (select 1 from relief_event r
                           where r.instance_country_id=sh.instance_country_id and r.executed_at is not null
                             and (r.shift_occurrence_id=sh.id or r.shift_occurrence_id is null
                                 and r.post_id=sh.post_id and r.planned_at=sh.starts_at))
                       or exists (select 1 from operational_assignment a
                           join consignment_compliance cc on cc.assignment_id=a.id
                               and cc.instance_country_id=a.instance_country_id
                           where a.instance_country_id=sh.instance_country_id and a.shift_occurrence_id=sh.id
                             and a.status<>'REMOVED' and cc.confirmed_at>=sh.starts_at and cc.confirmed_at<sh.ends_at)
                       or exists (select 1 from task_execution te
                           where te.instance_country_id=sh.instance_country_id and te.shift_occurrence_id=sh.id
                             and te.status='RECEIVED' and te.executed_at>=sh.starts_at and te.executed_at<sh.ends_at)
                       or exists (select 1 from patrol_execution pe
                           join operational_assignment a on a.id=pe.assignment_id
                               and a.instance_country_id=pe.instance_country_id
                           where a.instance_country_id=sh.instance_country_id and a.shift_occurrence_id=sh.id
                             and a.status<>'REMOVED' and pe.started_at>=sh.starts_at and pe.started_at<sh.ends_at)
                       or exists (select 1 from operator_operational_report rep
                           join operational_assignment a on a.id=rep.assignment_id
                               and a.instance_country_id=rep.instance_country_id
                           where a.instance_country_id=sh.instance_country_id and a.shift_occurrence_id=sh.id
                             and a.status<>'REMOVED' and rep.occurred_at>=sh.starts_at and rep.occurred_at<sh.ends_at)
                       as has_service
                from shift_occurrence sh
                join post po on po.id=sh.post_id and po.instance_country_id=sh.instance_country_id
                join point p on p.id=po.point_id and p.instance_country_id=po.instance_country_id
                join service sv on sv.id=p.service_id and sv.instance_country_id=p.instance_country_id
                where sh.instance_country_id=:tenant and p.company_id in (:companies) and p.status='ACTIVE'
                  and sh.required=true and sh.starts_at>=:from and sh.starts_at<:to and sh.ends_at<=:now
                  and (p.operational_transition_until is null or sh.starts_at>=p.operational_transition_until)
            """ + (clientId == null ? "" : " and sv.client_id=:client\n") + """
            ), point_results as (
                select point_id, bool_or(not has_service) as uncovered from evaluated_shifts group by point_id
            )
            select (select count(*) from point_results where uncovered),
                   (select count(*) from point_results),
                   (select count(*) from evaluated_shifts where not has_service),
                   (select count(*) from evaluated_shifts)
            """;
        Object[] row = (Object[]) query(sql, allowed, clientId,
            week.atStartOfDay(OPERATING_ZONE).toInstant(), week.plusDays(7).atStartOfDay(OPERATING_ZONE).toInstant())
            .setParameter("now", now).getSingleResult();
        return new long[]{((Number) row[0]).longValue(), ((Number) row[1]).longValue(),
            ((Number) row[2]).longValue(), ((Number) row[3]).longValue()};
    }

    @GET @Path("/supervision-route-compliance")
    public SupervisionRouteCompliance supervisionRouteCompliance(@QueryParam("weekStart") String weekStart,
            @QueryParam("companyId") UUID companyId, @QueryParam("clientId") UUID clientId) {
        LocalDate week = parseWeek(weekStart);
        Set<UUID> allowed = scope.allowedCompanyIds();
        if (companyId != null) { scope.requireCompany(companyId); allowed = Set.of(companyId); }
        if (allowed.isEmpty()) return new SupervisionRouteCompliance(0, 0, null);
        String sql = """
            with route_executions as (
            select sh.id, sr.id as route_id, count(distinct rp.point_id) as expected_points,
                   count(distinct rp.point_id) filter (where exists (
                     select 1 from supervisor_evaluation e
                     join operational_assignment a on a.id=e.supervisor_assignment_id
                         and a.instance_country_id=e.instance_country_id and a.status<>'REMOVED'
                     where e.instance_country_id=sh.instance_country_id and e.point_id=rp.point_id
                       and e.status='COMPLETED' and a.shift_occurrence_id=sh.id)) as completed_points
            from shift_occurrence sh
            join coordination_post cp on cp.id=sh.coordination_post_id and cp.instance_country_id=sh.instance_country_id
                and cp.post_type='SUPERVISION'
            join assignment_plan ap on ap.instance_country_id=sh.instance_country_id
                and ap.company_id=cp.company_id and ap.week_start=:week and ap.status in ('PUBLISHED','CLOSED')
            join supervision_route sr on sr.id=sh.supervision_route_id and sr.instance_country_id=sh.instance_country_id
                and sr.coordination_post_id=cp.id and sr.status in ('ACTIVE','REPLACED')
            join supervision_route_point rp on rp.route_id=sr.id and rp.instance_country_id=sr.instance_country_id
            join point p on p.id=rp.point_id and p.instance_country_id=rp.instance_country_id and p.status='ACTIVE'
                and p.company_id=cp.company_id
            join service sv on sv.id=p.service_id and sv.instance_country_id=p.instance_country_id
            where sh.instance_country_id=:tenant and cp.company_id in (:companies)
              and sh.required=true and sh.starts_at>=:from and sh.starts_at<:to and sh.ends_at<=:now
            """ + (clientId == null ? "" : " and sv.client_id=:client\n") + """
            group by sh.id, sr.id
            )
            select count(*) filter (where completed_points<expected_points), count(*) from route_executions
            """;
        Object[] counts = (Object[]) query(sql, allowed, clientId,
            week.atStartOfDay(OPERATING_ZONE).toInstant(), week.plusDays(7).atStartOfDay(OPERATING_ZONE).toInstant())
            .setParameter("week", week).setParameter("now", Instant.now()).getSingleResult();
        long missed = ((Number) counts[0]).longValue(), expected = ((Number) counts[1]).longValue();
        return new SupervisionRouteCompliance(missed, expected, percentage(missed, expected));
    }

    @GET @Path("/missed-reliefs")
    public MissedReliefs missedReliefs(@QueryParam("weekStart") String weekStart,
            @QueryParam("companyId") UUID companyId, @QueryParam("clientId") UUID clientId) {
        LocalDate week = parseWeek(weekStart);
        Set<UUID> allowed = scope.allowedCompanyIds();
        if (companyId != null) { scope.requireCompany(companyId); allowed = Set.of(companyId); }
        if (allowed.isEmpty()) return new MissedReliefs(0, 0);
        String sql = """
            select count(*) filter (where not exists (
                     select 1 from relief_event r
                     where r.instance_country_id=sh.instance_country_id and r.executed_at is not null
                       and (r.shift_occurrence_id=sh.id or r.shift_occurrence_id is null
                           and r.post_id=sh.post_id and r.planned_at=sh.starts_at))),
                   count(*)
            from shift_occurrence sh
            join post po on po.id=sh.post_id and po.instance_country_id=sh.instance_country_id
            join point p on p.id=po.point_id and p.instance_country_id=po.instance_country_id and p.status='ACTIVE'
            join service sv on sv.id=p.service_id and sv.instance_country_id=p.instance_country_id
            where sh.instance_country_id=:tenant and p.company_id in (:companies)
              and sh.required=true and sh.starts_at>=:from and sh.starts_at<:to and sh.ends_at<=:now
              and (p.operational_transition_until is null or sh.starts_at>=p.operational_transition_until)
              and exists (select 1 from operational_assignment a
                  join assignment_plan ap on ap.id=a.assignment_plan_id and ap.instance_country_id=a.instance_country_id
                  where a.instance_country_id=sh.instance_country_id and a.shift_occurrence_id=sh.id
                    and a.status<>'REMOVED' and coalesce(a.actual_employee_id,a.employee_id) is not null
                    and ap.status in ('PUBLISHED','CLOSED'))
            """ + (clientId == null ? "" : " and sv.client_id=:client");
        Object[] counts = (Object[]) query(sql, allowed, clientId,
            week.atStartOfDay(OPERATING_ZONE).toInstant(), week.plusDays(7).atStartOfDay(OPERATING_ZONE).toInstant())
            .setParameter("now", Instant.now()).getSingleResult();
        return new MissedReliefs(((Number) counts[0]).longValue(), ((Number) counts[1]).longValue());
    }

    @GET @Path("/critical-incidents")
    public CriticalIncidents criticalIncidents(@QueryParam("weekStart") String weekStart,
            @QueryParam("companyId") UUID companyId, @QueryParam("clientId") UUID clientId) {
        LocalDate week = parseWeek(weekStart);
        Set<UUID> allowed = scope.allowedCompanyIds();
        if (companyId != null) { scope.requireCompany(companyId); allowed = Set.of(companyId); }
        if (allowed.isEmpty()) return new CriticalIncidents(0);
        String sql = """
            select sum(source_count) from (
            select count(*) as source_count from operator_operational_report r
            join point p on p.id=r.point_id and p.instance_country_id=r.instance_country_id
            join service sv on sv.id=p.service_id and sv.instance_country_id=p.instance_country_id
            where r.instance_country_id=:tenant and p.company_id in (:companies)
              and r.report_type='INCIDENT' and r.severity='CRITICAL'
              and r.status not in ('CLOSED','RESOLVED','DISCARDED')
              and r.occurred_at>=:from and r.occurred_at<:to
            """ + (clientId == null ? "" : " and sv.client_id=:client\n") + """
            union all
            select count(*) from incident_notification i
            left join point p on p.id=i.point_id and p.instance_country_id=i.instance_country_id
            left join service sv on sv.id=p.service_id and sv.instance_country_id=p.instance_country_id
            where i.instance_country_id=:tenant and i.company_id in (:companies)
              and (i.payload_json::jsonb->>'severity')='CRITICAL'
              and i.status not in ('CLOSED','RESOLVED','DISCARDED')
              and i.created_at>=:from and i.created_at<:to
            """ + (clientId == null ? "" : " and sv.client_id=:client\n") + """
            ) sources
            """;
        Number count = (Number) query(sql, allowed, clientId,
            week.atStartOfDay(OPERATING_ZONE).toInstant(), week.plusDays(7).atStartOfDay(OPERATING_ZONE).toInstant()).getSingleResult();
        return new CriticalIncidents(count.longValue());
    }
    @GET @Path("/operational-risk-trend")
    public List<RiskTrendPoint> operationalRiskTrend(@QueryParam("weekStart") String weekStart,
            @QueryParam("companyId") UUID companyId, @QueryParam("clientId") UUID clientId,
            @QueryParam("period") @DefaultValue("30d") String period) {
        LocalDate week;
        try {
            week = (weekStart == null ? LocalDate.now(OPERATING_ZONE) : LocalDate.parse(weekStart))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        } catch (DateTimeException error) { throw new BadRequestException("weekStart debe ser una fecha YYYY-MM-DD."); }
        LocalDate end = week.plusDays(6);
        LocalDate today = LocalDate.now(OPERATING_ZONE);
        if (end.isAfter(today)) end = today;
        LocalDate start = switch (period) {
            case "7d" -> end.minusDays(6);
            case "30d" -> end.minusDays(29);
            case "6m" -> end.minusMonths(6).plusDays(1);
            default -> throw new BadRequestException("period debe ser 7d, 30d o 6m.");
        };
        Set<UUID> allowed = scope.allowedCompanyIds();
        if (companyId != null) { scope.requireCompany(companyId); allowed = Set.of(companyId); }
        List<RiskTrendPoint> trend = new ArrayList<>((int) (end.toEpochDay() - start.toEpochDay() + 1));
        if (allowed.isEmpty()) {
            for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) trend.add(new RiskTrendPoint(day, 0, 0, null));
            return trend;
        }
        String sql = """
            with point_days as (
                select (sh.starts_at at time zone 'America/Guayaquil')::date as day,
                       p.id as point_id,
                       bool_or(not exists (
                           select 1 from operational_assignment a
                           where a.instance_country_id=sh.instance_country_id
                             and a.shift_occurrence_id=sh.id and a.status<>'REMOVED'
                       )) as uncovered
                from shift_occurrence sh
                join post po on po.id=sh.post_id and po.instance_country_id=sh.instance_country_id
                join point p on p.id=po.point_id and p.instance_country_id=sh.instance_country_id
                join service sv on sv.id=p.service_id and sv.instance_country_id=p.instance_country_id
                where sh.instance_country_id=:tenant and p.company_id in (:companies)
                  and sh.required=true and sh.starts_at>=:from and sh.starts_at<:to
            """ + (clientId == null ? "" : " and sv.client_id=:client") + """
                group by day,p.id
            )
            select day,count(*) as scheduled_points,count(*) filter (where uncovered) as uncovered_points
            from point_days group by day order by day
            """;
        NativeQuery<?> query = em.createNativeQuery(sql).unwrap(NativeQuery.class);
        query.setParameter("tenant", tenant.instanceCountryId());
        query.setParameterList("companies", allowed);
        query.setParameter("from", start.atStartOfDay(OPERATING_ZONE).toInstant());
        query.setParameter("to", end.plusDays(1).atStartOfDay(OPERATING_ZONE).toInstant());
        if (clientId != null) query.setParameter("client", clientId);
        java.util.Map<LocalDate, long[]> counts = new java.util.HashMap<>();
        for (Object result : query.getResultList()) {
            Object[] row = (Object[]) result;
            LocalDate day = row[0] instanceof LocalDate local ? local : ((java.sql.Date) row[0]).toLocalDate();
            counts.put(day, new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            long[] values = counts.get(day);
            long scheduled = values == null ? 0 : values[0], uncovered = values == null ? 0 : values[1];
            trend.add(new RiskTrendPoint(day, scheduled, uncovered,
                scheduled == 0 ? null : Math.round(1000d * uncovered / scheduled) / 10d));
        }
        return trend;
    }

    @GET @Path("/metrics")
    public Metrics metrics(@QueryParam("weekStart") String weekStart,
                           @QueryParam("companyId") UUID companyId, @QueryParam("clientId") UUID clientId,
                           @QueryParam("period") String period) {
        LocalDate week;
        try {
            week = (weekStart == null ? LocalDate.now(OPERATING_ZONE) : LocalDate.parse(weekStart))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        } catch (DateTimeException error) { throw new BadRequestException("weekStart debe ser una fecha YYYY-MM-DD."); }
        Set<UUID> allowed = scope.allowedCompanyIds();
        if (companyId != null) { scope.requireCompany(companyId); allowed = Set.of(companyId); }
        Instant now = Instant.now();
        ConsignmentWindow reliefWindow = consignmentWindow(week, period, LocalDate.now(OPERATING_ZONE));
        if (allowed.isEmpty()) return new Metrics(week, now, null, null, 0, 0, 0, 0, 0, 0);
        Instant from = week.atStartOfDay(OPERATING_ZONE).toInstant();
        Instant to = week.plusDays(7).atStartOfDay(OPERATING_ZONE).toInstant();
        String points = """
            with scoped_points as (
                select p.id from point p join service s on s.id=p.service_id
                    and s.instance_country_id=p.instance_country_id
                where p.instance_country_id=:tenant and p.company_id in (:companies)
            """ + (clientId == null ? "" : " and s.client_id=:client") + ") ";
        Object[] scores = (Object[]) query(points + """
            select sum(a.id_score * greatest(extract(epoch from (s.ends_at-s.starts_at)), 1))
                     / nullif(sum(case when a.id_score is not null then greatest(extract(epoch from (s.ends_at-s.starts_at)), 1) end),0),
                   sum(a.compatibility_index * greatest(extract(epoch from (s.ends_at-s.starts_at)), 1))
                     / nullif(sum(case when a.compatibility_index is not null then greatest(extract(epoch from (s.ends_at-s.starts_at)), 1) end),0),
                   count(a.id_score), count(a.compatibility_index)
            from operational_assignment a
            join shift_occurrence s on s.id=a.shift_occurrence_id and s.instance_country_id=a.instance_country_id
            join post p on p.id=s.post_id and p.instance_country_id=s.instance_country_id
            where a.instance_country_id=:tenant and p.point_id in (select id from scoped_points)
                and a.status<>'REMOVED' and s.ends_at>=:from and s.ends_at<:to and s.ends_at<=:now
            """, allowed, clientId, from, to).setParameter("now", now).getSingleResult();
        Number reliefs = (Number) query(points + """
            select count(*)
            from relief_event r join post p on p.id=r.post_id and p.instance_country_id=r.instance_country_id
            where r.instance_country_id=:tenant and p.point_id in (select id from scoped_points)
                and r.planned_at>=:from and r.planned_at<:to and r.executed_at is not null
                and r.executed_at>r.planned_at+interval '2 hours'
            """, allowed, clientId,
            reliefWindow.from().minus(Duration.between(reliefWindow.from(), reliefWindow.to())),
            reliefWindow.from()).getSingleResult();
        Object[] currentReliefs = (Object[]) query(points + """
            select count(*) filter (where r.executed_at>r.planned_at+interval '2 hours'),
                   count(*), count(*) filter (where r.executed_at>r.planned_at+interval '3 hours')
            from relief_event r join post p on p.id=r.post_id and p.instance_country_id=r.instance_country_id
            where r.instance_country_id=:tenant and p.point_id in (select id from scoped_points)
              and r.planned_at>=:from and r.planned_at<:to and r.executed_at is not null
            """, allowed, clientId, reliefWindow.from(), reliefWindow.to()).getSingleResult();
        return new Metrics(week, now, value(scores[0]), value(scores[1]),
            ((Number)scores[2]).longValue(), ((Number)scores[3]).longValue(),
            ((Number)currentReliefs[0]).longValue(), ((Number)currentReliefs[1]).longValue(),
            ((Number)currentReliefs[2]).longValue(), reliefs.longValue());
    }

    private NativeQuery<?> query(String sql, Set<UUID> companies, UUID clientId, Instant from, Instant to) {
        NativeQuery<?> query = em.createNativeQuery(sql).unwrap(NativeQuery.class);
        query.setParameter("tenant", tenant.instanceCountryId());
        query.setParameterList("companies", companies);
        query.setParameter("from", from);
        query.setParameter("to", to);
        if (clientId != null) query.setParameter("client", clientId);
        return query;
    }
    private static LocalDate parseWeek(String weekStart) {
        try {
            return (weekStart == null ? LocalDate.now(OPERATING_ZONE) : LocalDate.parse(weekStart))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        } catch (DateTimeException error) { throw new BadRequestException("weekStart debe ser una fecha YYYY-MM-DD."); }
    }
    private static Double percentage(long numerator, long denominator) {
        return denominator == 0 ? null : Math.round(1000d * numerator / denominator) / 10d;
    }
    private static Double value(Object value) { return value == null ? null : ((Number)value).doubleValue(); }
}
