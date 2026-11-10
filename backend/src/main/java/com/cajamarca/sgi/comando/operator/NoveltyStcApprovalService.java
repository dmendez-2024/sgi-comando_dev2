package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.NotFoundException;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@ApplicationScoped
public class NoveltyStcApprovalService {
    static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Guayaquil");

    @Inject EntityManager em;
    @Inject TenantContext tenant;
    @Inject SecurityIdentity identity;
    @Inject ObjectMapper mapper;
    @Inject NoveltyIntegrationGateway gateway;

    public record ApprovalResult(String taskCode, Instant startDate, Instant endDate, int attempts) {}
    record Codes(int category, int subcategory, int incident, int severity) {}
    record ReportData(UUID id, String type, String category, String subcategory, String title,
                      String description, String severity, Instant occurredAt, String companyName,
                      Long reporterPersonaId, Long coordinatorPersonaId, Long assistantPersonaId) {}

    public ApprovalResult createTask(UUID reportId, Instant approvedAt) {
        ReportData report = report(reportId);
        Instant start = approvedAt.truncatedTo(ChronoUnit.MILLIS);
        Instant end = endOfIsoWeek(start);
        String approvingEmail = identity.getPrincipal().getName();

        String ownerEmail = gateway.employeeEmail(tenant.instanceCountryId(), report.reporterPersonaId());
        String coordinatorEmail = identity.hasRole("COORDINADOR_COMPANIA")
            ? approvingEmail : gateway.employeeEmail(tenant.instanceCountryId(), report.coordinatorPersonaId());
        String assistantEmail = identity.hasRole("ASISTENTE_COORDINACION")
            ? approvingEmail : gateway.employeeEmail(tenant.instanceCountryId(), report.assistantPersonaId());

        Codes codes = codes(report);
        ObjectNode payload = payload(report, codes, start, end, ownerEmail, coordinatorEmail, assistantEmail);
        NoveltyIntegrationGateway.CreatedTask created = gateway.createStcTask(payload, reportId, approvingEmail);
        return new ApprovalResult(created.taskCode(), start, end, created.attempts());
    }

    ObjectNode payload(ReportData report, Codes codes, Instant start, Instant end, String ownerEmail,
                       String coordinatorEmail, String assistantEmail) {
        ObjectNode body = mapper.createObjectNode();
        addEmail(body.putArray("assignedTask"), coordinatorEmail);
        body.putObject("category").put("code", "").put("name", "");
        body.put("code", "");
        body.put("createDate", iso(start));
        body.put("description", report.description());
        body.putArray("documentDescriptionList");
        body.put("endDate", iso(end));
        body.put("name", report.title());
        if (ownerEmail == null || ownerEmail.isBlank()) body.putNull("owner"); else body.put("owner", ownerEmail.trim());
        body.put("photoUrl", "");
        ObjectNode recurrence = body.putObject("recurrence");
        recurrence.putArray("daysWeek").add("");
        recurrence.put("finishType", "");
        recurrence.put("finishValue", iso(end));
        recurrence.put("finishValueRepeat", 0);
        recurrence.put("monthType", "");
        recurrence.put("repeatTimes", 0);
        recurrence.put("type", "");
        addEmail(body.putArray("stakeholderTask"), assistantEmail);
        body.put("startDate", iso(start));
        body.put("taskReference", "");
        body.put("categoriaId", 0);
        body.put("subcategoriaId", 0);
        body.put("indicenteId", 0);
        body.putObject("categoryK").put("id", codes.category());
        body.putObject("subCategoryK").put("id", codes.subcategory());
        body.putObject("incidenteK").put("id", codes.incident());
        body.putObject("severityLevel").put("id", codes.severity());
        body.putObject("statusId").put("id", 0);
        body.putObject("enterprise").put("id", 0);
        body.put("requestTaskDate", iso(report.occurredAt()));
        body.put("idCompany", 0);
        body.put("nameCompany", value(report.companyName()));
        body.put("nameClient", "");
        body.put("point", 0);
        body.put("platform", 0);
        return body;
    }

    private ReportData report(UUID reportId) {
        @SuppressWarnings("unchecked") List<Object[]> rows = em.createNativeQuery("""
            select report.id,report.report_type,report.category,report.subcategory,report.title,
                   report.description,report.severity,report.occurred_at,company.name,
                   reporter.persona_id,coordinator.persona_id,assistant.persona_id
              from operator_operational_report report
              join point point_row on point_row.id=report.point_id
                                  and point_row.instance_country_id=report.instance_country_id
              left join company company on company.id=point_row.company_id
                                       and company.instance_country_id=report.instance_country_id
              left join employee_operational_snapshot reporter
                     on reporter.employee_id=report.employee_id
                    and reporter.instance_country_id=report.instance_country_id
              left join employee_operational_snapshot coordinator
                     on coordinator.employee_id=company.responsible_employee_id
                    and coordinator.instance_country_id=report.instance_country_id
              left join employee_operational_snapshot assistant
                     on assistant.employee_id=company.assistant_employee_id
                    and assistant.instance_country_id=report.instance_country_id
             where report.id=:report and report.instance_country_id=:tenant
            """).setParameter("report", reportId).setParameter("tenant", tenant.instanceCountryId()).getResultList();
        if (rows.isEmpty()) throw new NotFoundException("Novedad no encontrada.");
        Object[] row = rows.get(0);
        return new ReportData(uuid(row[0]), value(row[1]), value(row[2]), nullable(row[3]), value(row[4]),
            value(row[5]), value(row[6]), instant(row[7]), nullable(row[8]), number(row[9]), number(row[10]), number(row[11]));
    }

    private Codes codes(ReportData report) {
        Codes fallback = new Codes(categoryCode(report.category()), subcategoryCode(report.subcategory()), 0,
            severityCode(report.severity()));
        if (!"INCIDENT".equals(report.type())) return fallback;
        @SuppressWarnings("unchecked") List<Object[]> linked = em.createNativeQuery("""
            select category.code,subcategory.code,incident_type.code,severity.code
              from incident_notification incident
              left join incident_category_catalog category on category.id=incident.category_catalog_id
              left join incident_subcategory_catalog subcategory on subcategory.id=incident.subcategory_catalog_id
              left join incident_type_catalog incident_type on incident_type.id=incident.incident_type_catalog_id
              left join incident_severity_catalog severity on severity.id=incident.severity_catalog_id
             where incident.instance_country_id=:tenant and incident.operational_report_id=:report
             order by incident.updated_at desc limit 1
            """).setParameter("tenant", tenant.instanceCountryId()).setParameter("report", report.id()).getResultList();
        if (linked.isEmpty()) return fallback;
        Object[] row = linked.get(0);
        return new Codes(positive(row[0], fallback.category()), positive(row[1], fallback.subcategory()),
            positive(row[2], 0), positive(row[3], fallback.severity()));
    }

    private int categoryCode(String value) {
        return mappedCode("""
            select code from incident_category_catalog
             where instance_country_id=:tenant and active
               and (lower(name)=lower(:value) or upper(system_key)=upper(:value))
             order by display_order limit 1
            """, value);
    }

    private int subcategoryCode(String value) {
        return mappedCode("""
            select code from incident_subcategory_catalog
             where instance_country_id=:tenant and active and lower(name)=lower(:value)
             order by display_order limit 1
            """, value);
    }

    private int mappedCode(String sql, String value) {
        if (value == null || value.isBlank()) return 0;
        @SuppressWarnings("unchecked") List<Object> rows = em.createNativeQuery(sql)
            .setParameter("tenant", tenant.instanceCountryId()).setParameter("value", value.trim()).getResultList();
        return rows.isEmpty() ? 0 : positive(rows.get(0), 0);
    }

    private static int severityCode(String value) {
        return switch (value == null ? "" : value.trim().toUpperCase(Locale.ROOT)) {
            case "LOW", "MINOR" -> 8;
            case "MEDIUM", "MODERATE" -> 9;
            case "HIGH", "MAJOR" -> 10;
            case "CRITICAL" -> 11;
            default -> 0;
        };
    }

    static Instant endOfIsoWeek(Instant approval) {
        ZonedDateTime local = approval.atZone(BUSINESS_ZONE);
        return local.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            .atTime(LocalTime.of(23, 59, 59, 999_000_000)).atZone(BUSINESS_ZONE).toInstant();
    }

    private static String iso(Instant value) { return value.truncatedTo(ChronoUnit.MILLIS).toString(); }
    private static void addEmail(ArrayNode target, String email) { if (email != null && !email.isBlank()) target.add(email.trim()); }
    private static int positive(Object value, int fallback) { return value instanceof Number n && n.intValue() > 0 ? n.intValue() : fallback; }
    private static Long number(Object value) { return value instanceof Number n ? n.longValue() : null; }
    private static UUID uuid(Object value) { return value instanceof UUID id ? id : UUID.fromString(value.toString()); }
    private static Instant instant(Object value) {
        if (value instanceof Instant time) return time;
        if (value instanceof java.time.OffsetDateTime time) return time.toInstant();
        if (value instanceof java.time.ZonedDateTime time) return time.toInstant();
        if (value instanceof java.sql.Timestamp time) return time.toInstant();
        return Instant.parse(value.toString());
    }
    private static String value(Object value) { return value == null ? "" : value.toString(); }
    private static String nullable(Object value) { String text = value(value); return text.isBlank() ? null : text; }
}
