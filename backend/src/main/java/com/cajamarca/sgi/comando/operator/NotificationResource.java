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
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Bandeja única de alertas activas, siempre limitada al alcance del usuario autenticado. */
@Path("/api/notifications")
@RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","MONITOR"})
@Produces(MediaType.APPLICATION_JSON)
public class NotificationResource {
    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject EntityManager em;

    public record Item(String id, String type, String destination, String title, String priority, String status,
                       String companyId, String companyName, String clientName, String pointName, String postName,
                       Instant occurredAt) {}
    public record Feed(long total, List<Item> items) {}

    @GET
    public Feed list() {
        Set<UUID> companies = scope.allowedCompanyIdsForCompanyRoles();
        if (companies.isEmpty()) return new Feed(0, List.of());
        List<Item> items = new ArrayList<>();
        items.addAll(consoleDrafts(companies));
        items.addAll(reports(companies));
        items.addAll(consignmentReviews(companies));
        items.addAll(activePatrols(companies));
        items.addAll(pendingReliefs(companies));
        items.sort(Comparator.comparingInt((Item item) -> priorityRank(item.priority())).reversed()
            .thenComparing(Item::occurredAt, Comparator.reverseOrder()));
        if (items.size() > 100) items = new ArrayList<>(items.subList(0, 100));
        return new Feed(items.size(), items);
    }

    private List<Item> consoleDrafts(Set<UUID> companies) {
        return query("""
          select i.id,i.code,i.payload_json::jsonb ->> 'title',i.payload_json::jsonb ->> 'severity',i.status,i.updated_at,
                 i.company_id,co.name,coalesce(i.payload_json::jsonb ->> 'client',''),coalesce(i.payload_json::jsonb ->> 'point',''),coalesce(i.payload_json::jsonb ->> 'post','')
          from incident_notification i
          left join company co on co.id=i.company_id and co.instance_country_id=i.instance_country_id
          where i.instance_country_id=:tenant and i.company_id in (:companies)
            and i.status not in ('FINALIZED','APPROVED','DISCARDED','COMPLETED','RESOLVED','CLOSED')
          order by i.updated_at desc limit 100
          """, companies, row -> item("incident:" + text(row[0]), "CONSOLA", "Consola",
            text(row[2]).isBlank() ? text(row[1]) : text(row[2]), priority(text(row[3])), text(row[4]),
            row[6], row[7], row[8], row[9], row[10], row[5]));
    }

    private List<Item> reports(Set<UUID> companies) {
        return query("""
          select r.id,r.title,r.severity,r.status,r.occurred_at,p.company_id,co.name,p.client_name,p.name,coalesce(po.name,'—')
          from operator_operational_report r
          join point p on p.id=r.point_id and p.instance_country_id=r.instance_country_id
          left join post po on po.id=r.post_id and po.instance_country_id=r.instance_country_id
          left join company co on co.id=p.company_id and co.instance_country_id=r.instance_country_id
          where r.instance_country_id=:tenant and p.company_id in (:companies)
            and r.status not in ('FINALIZED','APPROVED','DISCARDED','COMPLETED','RESOLVED','CLOSED')
          order by r.occurred_at desc limit 100
          """, companies, row -> item("operator:" + text(row[0]), "NOVEDADES", "Novedades", text(row[1]),
            priority(text(row[2])), text(row[3]), row[5], row[6], row[7], row[8], row[9], row[4]));
    }

    private List<Item> consignmentReviews(Set<UUID> companies) {
        return query("""
          select r.id,r.title,r.priority,r.status,r.submitted_at,p.company_id,co.name,p.client_name,p.name,coalesce(po.name,'—')
          from operator_consignment_review_request r
          join point p on p.id=r.point_id and p.instance_country_id=r.instance_country_id
          left join post po on po.id=r.post_id and po.instance_country_id=r.instance_country_id
          left join company co on co.id=p.company_id and co.instance_country_id=r.instance_country_id
          where r.instance_country_id=:tenant and p.company_id in (:companies)
            and r.status not in ('APPROVED','DISCARDED','COMPLETED','RESOLVED','FINALIZED','CLOSED')
          order by r.submitted_at desc limit 100
          """, companies, row -> item("opr-review-" + text(row[0]), "CONSIGNAS", "Consignas", text(row[1]),
            priority(text(row[2])), text(row[3]), row[5], row[6], row[7], row[8], row[9], row[4]));
    }

    private List<Item> activePatrols(Set<UUID> companies) {
        return query("""
          select e.id,coalesce(d.name,'Patrulla en ejecución'),e.started_at,p.company_id,co.name,p.client_name,p.name,coalesce(po.name,'—')
          from patrol_execution e
          join patrol_plan plan on plan.id=e.patrol_plan_id and plan.instance_country_id=e.instance_country_id
          left join patrol_definition d on d.id=plan.patrol_definition_id and d.instance_country_id=e.instance_country_id
          join post po on po.id=plan.post_id and po.instance_country_id=e.instance_country_id
          join point p on p.id=po.point_id and p.instance_country_id=e.instance_country_id
          left join company co on co.id=p.company_id and co.instance_country_id=e.instance_country_id
          where e.instance_country_id=:tenant and p.company_id in (:companies) and e.finished_at is null
          order by e.started_at desc limit 100
          """, companies, row -> item("patrol:" + text(row[0]), "PATRULLAS", "Consignas", text(row[1]),
            "MEDIUM", "IN_PROGRESS", row[3], row[4], row[5], row[6], row[7], row[2]));
    }

    private List<Item> pendingReliefs(Set<UUID> companies) {
        return query("""
          select r.id,r.status,r.planned_at,p.company_id,co.name,p.client_name,p.name,coalesce(po.name,'—')
          from relief_event r
          join post po on po.id=r.post_id and po.instance_country_id=r.instance_country_id
          join point p on p.id=po.point_id and p.instance_country_id=r.instance_country_id
          left join company co on co.id=p.company_id and co.instance_country_id=r.instance_country_id
          where r.instance_country_id=:tenant and p.company_id in (:companies) and r.executed_at is null
            and r.status not in ('FINALIZED','APPROVED','DISCARDED','COMPLETED','RESOLVED','CLOSED')
          order by r.planned_at desc nulls last limit 100
          """, companies, row -> item("relief:" + text(row[0]), "RELEVOS", "Consignas", "Relevo pendiente",
            isPast(row[2]) ? "HIGH" : "MEDIUM", text(row[1]), row[3], row[4], row[5], row[6], row[7], row[2]));
    }

    @FunctionalInterface private interface RowMapper { Item map(Object[] row); }
    @SuppressWarnings("unchecked")
    private List<Item> query(String sql, Set<UUID> companies, RowMapper mapper) {
        List<Object[]> rows = em.createNativeQuery(sql).setParameter("tenant", tenant.instanceCountryId())
            .setParameter("companies", companies).getResultList();
        return rows.stream().map(mapper::map).toList();
    }
    private static Item item(String id, String type, String destination, String title, String priority, String status,
                             Object companyId, Object companyName, Object clientName, Object pointName, Object postName, Object occurredAt) {
        return new Item(id, type, destination, title, priority, status, text(companyId), text(companyName), text(clientName),
            text(pointName), text(postName), timestamp(occurredAt));
    }
    private static int priorityRank(String value) { return switch (value) { case "CRITICAL" -> 4; case "HIGH" -> 3; case "MEDIUM" -> 2; default -> 1; }; }
    private static String priority(String value) { return switch (value == null ? "" : value.toUpperCase()) { case "CRÍTICA", "CRITICA", "CRITICAL" -> "CRITICAL"; case "ALTA", "HIGH" -> "HIGH"; default -> "MEDIUM"; }; }
    private static boolean isPast(Object value) { return value != null && timestamp(value).isBefore(Instant.now()); }
    private static Instant timestamp(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        return value == null ? Instant.now() : Instant.parse(value.toString());
    }
    private static String text(Object value) { return value == null ? "" : value.toString(); }
}
