package com.cajamarca.sgi.comando.services;

import com.cajamarca.sgi.comando.assignments.OperationalAssignmentEntity;
import com.cajamarca.sgi.comando.assignments.ShiftOccurrenceEntity;
import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.companies.Company;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.operations.PostEntity;
import com.cajamarca.sgi.comando.operations.ServiceEntity;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Path("/api/services/overview")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
public class ServiceOverviewResource {
    private static final Duration EXECUTION_WINDOW = Duration.ofDays(14);

    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject EntityManager em;

    public record OverviewRow(
        UUID serviceId, UUID pointId, UUID postId, UUID companyId,
        String companyName, String companyLogoDataUrl,
        String serviceCode, String serviceName, String clientName,
        String pointCode, String pointName, String postCode, String postName,
        String tier, Double idAverage, Double icAverage,
        int pendingNews, String state
    ) {}

    public record OverviewSummary(
        int totalPosts, int activePosts, int toConfigurePosts, int inactivePosts,
        int pendingNews, Double icAverage
    ) {}

    public record OverviewResponse(
        Instant windowFrom, Instant windowTo, String executionBasis,
        OverviewSummary summary, List<OverviewRow> rows
    ) {}

    @GET
    @Transactional
    public OverviewResponse overview() {
        Set<UUID> allowed = scope.allowedCompanyIds();
        Instant to = Instant.now();
        Instant from = to.minus(EXECUTION_WINDOW);
        if (allowed.isEmpty()) {
            return new OverviewResponse(from, to, "ENDED_ASSIGNMENTS_UAT_PROXY", new OverviewSummary(0,0,0,0,0,null), List.of());
        }

        List<PointEntity> points = PointEntity.list("instanceCountryId=?1 and companyId in ?2 order by name", tenant.instanceCountryId(), allowed);
        if (points.isEmpty()) {
            return new OverviewResponse(from, to, "ENDED_ASSIGNMENTS_UAT_PROXY", new OverviewSummary(0,0,0,0,0,null), List.of());
        }

        Set<UUID> serviceIds = points.stream().map(p -> p.serviceId).collect(Collectors.toSet());
        Set<UUID> pointIds = points.stream().map(p -> p.id).collect(Collectors.toSet());
        Set<UUID> companyIds = points.stream().map(p -> p.companyId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<UUID,ServiceEntity> services = ServiceEntity.<ServiceEntity>list("instanceCountryId=?1 and id in ?2", tenant.instanceCountryId(), serviceIds)
            .stream().collect(Collectors.toMap(x -> x.id, Function.identity()));
        Map<UUID,Company> companies = companyIds.isEmpty() ? Map.of() : Company.<Company>list("instanceCountryId=?1 and id in ?2", tenant.instanceCountryId(), companyIds)
            .stream().collect(Collectors.toMap(x -> x.id, Function.identity()));
        Map<UUID,PointEntity> pointMap = points.stream().collect(Collectors.toMap(x -> x.id, Function.identity()));
        List<PostEntity> posts = PostEntity.list("instanceCountryId=?1 and pointId in ?2 order by code", tenant.instanceCountryId(), pointIds);
        Set<UUID> postIds = posts.stream().map(p -> p.id).collect(Collectors.toSet());

        Map<UUID,WeightedMetric> idMetrics = new HashMap<>();
        Map<UUID,WeightedMetric> icMetrics = new HashMap<>();
        if (!postIds.isEmpty()) {
            List<ShiftOccurrenceEntity> shifts = ShiftOccurrenceEntity.list(
                "instanceCountryId=?1 and postId in ?2 and endsAt>?3 and endsAt<=?4",
                tenant.instanceCountryId(), postIds, from, to);
            Map<UUID,ShiftOccurrenceEntity> shiftById = shifts.stream().collect(Collectors.toMap(x -> x.id, Function.identity()));
            Set<UUID> shiftIds = shiftById.keySet();
            if (!shiftIds.isEmpty()) {
                List<OperationalAssignmentEntity> assignments = OperationalAssignmentEntity.list(
                    "instanceCountryId=?1 and shiftOccurrenceId in ?2 and status<>'REMOVED'",
                    tenant.instanceCountryId(), shiftIds);
                // UAT proxy until SGI-07 Relevos is the execution SoR: only ended shifts are considered.
                // When Relevos exists, this aggregation must switch to minutes actually covered.
                for (OperationalAssignmentEntity assignment : assignments) {
                    ShiftOccurrenceEntity shift = shiftById.get(assignment.shiftOccurrenceId);
                    if (shift == null) continue;
                    long minutes = Math.max(1L, Duration.between(shift.startsAt, shift.endsAt).toMinutes());
                    if (assignment.idScore != null) idMetrics.computeIfAbsent(shift.postId, k -> new WeightedMetric()).add(assignment.idScore, minutes);
                    if (assignment.compatibilityIndex != null) icMetrics.computeIfAbsent(shift.postId, k -> new WeightedMetric()).add(assignment.compatibilityIndex, minutes);
                }
            }
        }

        List<OverviewRow> rows = new ArrayList<>();
        for (PostEntity post : posts) {
            PointEntity point = pointMap.get(post.pointId);
            if (point == null) continue;
            ServiceEntity service = services.get(point.serviceId);
            Company company = point.companyId == null ? null : companies.get(point.companyId);
            if (service == null) continue;
            String state = state(service, point, post);
            int pendingNews = pendingNews(point.id, post.id);
            rows.add(new OverviewRow(
                service.id, point.id, post.id, point.companyId,
                company == null ? "—" : company.name,
                company == null ? null : company.logoDataUrl,
                service.code, service.name, point.clientName,
                point.code, point.name, post.code, post.name,
                post.tier,
                metricValue(idMetrics.get(post.id)), metricValue(icMetrics.get(post.id)),
                pendingNews, state
            ));
        }
        rows.sort(Comparator.comparing(OverviewRow::companyName).thenComparing(OverviewRow::clientName).thenComparing(OverviewRow::pointName).thenComparing(OverviewRow::postCode));

        int active = (int) rows.stream().filter(r -> "ACTIVE".equals(r.state())).count();
        int toConfigure = (int) rows.stream().filter(r -> "TO_CONFIGURE".equals(r.state())).count();
        int inactive = (int) rows.stream().filter(r -> "INACTIVE".equals(r.state())).count();
        int pending = pendingNewsTotal(pointIds);
        Double icAverage = roundedAverage(rows.stream().filter(r -> "ACTIVE".equals(r.state())).map(OverviewRow::icAverage).filter(Objects::nonNull).toList());
        OverviewSummary summary = new OverviewSummary(rows.size(), active, toConfigure, inactive, pending, icAverage);
        return new OverviewResponse(from, to, "ENDED_ASSIGNMENTS_UAT_PROXY", summary, rows);
    }

    private String state(ServiceEntity service, PointEntity point, PostEntity post) {
        if (!"ACTIVE".equalsIgnoreCase(service.commercialStatus) || !"ACTIVE".equalsIgnoreCase(point.status)) return "INACTIVE";
        if (!"CONFIGURED".equalsIgnoreCase(service.configStatus) || !"CONFIGURED".equalsIgnoreCase(post.configStatus)) return "TO_CONFIGURE";
        return "ACTIVE";
    }


    private int pendingNewsTotal(Set<UUID> pointIds) {
        if (pointIds.isEmpty()) return 0;
        String closed = "('CLOSED','RESOLVED','REVIEWED','DISMISSED')";
        String ids = pointIds.stream().map(id -> "'" + id + "'::uuid").collect(Collectors.joining(","));
        Number findings = (Number) em.createNativeQuery("select count(*) from finding where instance_country_id=?1 and point_id in (" + ids + ") and upper(status) not in " + closed)
            .setParameter(1, tenant.instanceCountryId()).getSingleResult();
        Number vulnerabilities = (Number) em.createNativeQuery("select count(*) from vulnerability where instance_country_id=?1 and point_id in (" + ids + ") and upper(status) not in " + closed)
            .setParameter(1, tenant.instanceCountryId()).getSingleResult();
        return findings.intValue() + vulnerabilities.intValue();
    }

    private int pendingNews(UUID pointId, UUID postId) {
        String closed = "('CLOSED','RESOLVED','REVIEWED','DISMISSED')";
        Number findings = (Number) em.createNativeQuery("select count(*) from finding where instance_country_id=?1 and point_id=?2 and (post_id is null or post_id=?3) and upper(status) not in " + closed)
            .setParameter(1, tenant.instanceCountryId()).setParameter(2, pointId).setParameter(3, postId).getSingleResult();
        Number vulnerabilities = (Number) em.createNativeQuery("select count(*) from vulnerability where instance_country_id=?1 and point_id=?2 and (post_id is null or post_id=?3) and upper(status) not in " + closed)
            .setParameter(1, tenant.instanceCountryId()).setParameter(2, pointId).setParameter(3, postId).getSingleResult();
        return findings.intValue() + vulnerabilities.intValue();
    }

    private static Double metricValue(WeightedMetric metric) {
        return metric == null ? null : metric.value();
    }

    private static Double roundedAverage(List<Double> values) {
        if (values.isEmpty()) return null;
        return BigDecimal.valueOf(values.stream().mapToDouble(Double::doubleValue).average().orElse(0d)).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static final class WeightedMetric {
        private BigDecimal weighted = BigDecimal.ZERO;
        private long minutes = 0;
        void add(BigDecimal value, long durationMinutes) {
            weighted = weighted.add(value.multiply(BigDecimal.valueOf(durationMinutes)));
            minutes += durationMinutes;
        }
        Double value() {
            if (minutes == 0) return null;
            return weighted.divide(BigDecimal.valueOf(minutes), 1, RoundingMode.HALF_UP).doubleValue();
        }
    }
}
