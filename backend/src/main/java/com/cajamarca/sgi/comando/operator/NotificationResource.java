package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.*;

/** Active operational reports, scoped on the server to the authenticated user's companies. */
@Path("/api/notifications")
@RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","MONITOR"})
@Produces(MediaType.APPLICATION_JSON)
public class NotificationResource {
    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject EntityManager em;

    public record Item(String id, String type, String title, String priority, String status,
                       String companyName, String clientName, String pointName, Instant occurredAt) {}
    public record Feed(long total, List<Item> items) {}

    @GET
    public Feed list() {
        Set<UUID> companies = scope.allowedCompanyIdsForCompanyRoles();
        if (companies.isEmpty()) return new Feed(0, List.of());
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
            select r.id,r.report_type,r.title,r.severity,r.status,coalesce(c.name,'—'),
                   coalesce(p.client_name,'—'),p.name,extract(epoch from r.occurred_at),count(*) over()
            from operator_operational_report r
            join point p on p.id=r.point_id and p.instance_country_id=r.instance_country_id
            join company c on c.id=p.company_id and c.instance_country_id=r.instance_country_id
            where r.instance_country_id=:tenant and p.company_id in (:companies)
              and r.status in ('REPORTED','PENDING','IN_PROGRESS','OVERDUE','NEW','CRITICAL','ESCALATED','ACKNOWLEDGED')
              and not exists (select 1 from incident_notification i
                  where i.instance_country_id=r.instance_country_id and i.operational_report_id=r.id
                    and i.status in ('DRAFT','FINALIZED'))
            order by case when r.severity='CRITICAL' or r.status='CRITICAL' then 0
                          when r.status in ('OVERDUE','ESCALATED') then 1
                          when r.severity='HIGH' then 2 else 3 end,
                     r.occurred_at desc,r.id
            limit 50
            """).setParameter("tenant", tenant.instanceCountryId()).setParameter("companies", companies).getResultList();
        List<Item> items = rows.stream().map(r -> new Item(r[0].toString(),r[1].toString(),r[2].toString(),
            r[3].toString(),"REPORTED".equals(r[4]) ? ("CRITICAL".equals(r[3]) ? "CRITICAL" : "PENDING") : r[4].toString(),
            r[5].toString(),r[6].toString(),r[7].toString(),
            Instant.ofEpochMilli((long)(((Number)r[8]).doubleValue()*1000)))).toList();
        return new Feed(rows.isEmpty() ? 0 : ((Number)rows.getFirst()[9]).longValue(), items);
    }
}
