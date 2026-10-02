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
import java.util.Set;
import java.util.UUID;

/** Aggregates stored records; missing execution sources are not inferred from planning. */
@Path("/api/dashboard")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
public class DashboardResource {
    private static final ZoneId OPERATING_ZONE = ZoneId.of("America/Guayaquil");
    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject EntityManager em;

    public record Metrics(LocalDate weekStart, Instant calculatedAt, Double idAverage, Double icAverage,
                          long idSamples, long icSamples, long lateReliefs, long recordedReliefs) {}

    @GET @Path("/metrics")
    public Metrics metrics(@QueryParam("weekStart") String weekStart,
                           @QueryParam("companyId") UUID companyId, @QueryParam("clientId") UUID clientId) {
        LocalDate week;
        try {
            week = (weekStart == null ? LocalDate.now(OPERATING_ZONE) : LocalDate.parse(weekStart))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        } catch (DateTimeException error) { throw new BadRequestException("weekStart debe ser una fecha YYYY-MM-DD."); }
        Set<UUID> allowed = scope.allowedCompanyIds();
        if (companyId != null) { scope.requireCompany(companyId); allowed = Set.of(companyId); }
        Instant now = Instant.now();
        if (allowed.isEmpty()) return new Metrics(week, now, null, null, 0, 0, 0, 0);
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
        Object[] reliefs = (Object[]) query(points + """
            select count(*) filter (where r.executed_at>r.planned_at), count(*)
            from relief_event r join post p on p.id=r.post_id and p.instance_country_id=r.instance_country_id
            where r.instance_country_id=:tenant and p.point_id in (select id from scoped_points)
                and r.planned_at>=:from and r.planned_at<:to and r.executed_at is not null
            """, allowed, clientId, from, to).getSingleResult();
        return new Metrics(week, now, value(scores[0]), value(scores[1]),
            ((Number)scores[2]).longValue(), ((Number)scores[3]).longValue(),
            ((Number)reliefs[0]).longValue(), ((Number)reliefs[1]).longValue());
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
    private static Double value(Object value) { return value == null ? null : ((Number)value).doubleValue(); }
}
