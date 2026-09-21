package com.cajamarca.sgi.comando.services;

import com.cajamarca.sgi.comando.assignments.OperationalAssignmentEntity;
import com.cajamarca.sgi.comando.assignments.ShiftOccurrenceEntity;
import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.companies.Company;
import com.cajamarca.sgi.comando.companies.CompanyRegion;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.operations.PostEntity;
import com.cajamarca.sgi.comando.operations.ServiceEntity;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import com.cajamarca.sgi.comando.territory.CountrySubdivision;
import com.cajamarca.sgi.comando.territory.UserOperationalScope;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Path("/api/services/overview")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
public class ServiceOverviewResource {
    private static final Duration EXECUTION_WINDOW = Duration.ofDays(14);

    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject EntityManager em;
    @Inject SecurityIdentity identity;

    private static final Set<String> SERVICE_ASSIGN_ROLES = Set.of("PRESIDENTE","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL");

    public record OverviewRow(
        UUID serviceId, UUID pointId, UUID postId, UUID companyId,
        String companyName, String companyLogoDataUrl,
        String serviceCode, String serviceName, String clientName,
        String pointCode, String pointName, String postCode, String postName,
        String tier, Double idAverage, Double icAverage,
        int pendingNews, String state, String assignmentStatus, boolean canAssign, boolean canReturn, boolean returnedToCoordination
    ) {}

    public record AssignmentDestinationDto(UUID companyId,String code,String name,UUID zoneId,List<UUID> regionIds) {}
    public record AssignCompanyRequest(UUID companyId,String observations) {}
    public record ReturnToCoordinationRequest(String observations) {}
    public record AssignmentResultDto(UUID pointId,UUID serviceId,UUID companyId,String companyName,String assignmentStatus,Instant assignedAt,String assignedBy) {}
    public record ReturnToCoordinationResultDto(UUID pointId,UUID serviceId,UUID originCompanyId,String originCompanyName,String assignmentStatus,int releasedFutureAssignments,int retainedActiveAssignments,Instant transitionUntil,Instant returnedAt,String returnedBy) {}

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
        List<PointEntity> points = new ArrayList<>();
        if (!allowed.isEmpty()) {
            points.addAll(PointEntity.list("instanceCountryId=?1 and companyId in ?2 order by name", tenant.instanceCountryId(), allowed));
        }
        if (canAssignServices()) {
            List<PointEntity> pending = PointEntity.list("instanceCountryId=?1 and operationalAssignmentStatus='PENDING' order by name", tenant.instanceCountryId());
            Set<UUID> seen = points.stream().map(p -> p.id).collect(Collectors.toSet());
            for (PointEntity p : pending) if (canManagePoint(p) && seen.add(p.id)) points.add(p);
        }
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
            boolean pendingAssignment = "PENDING".equalsIgnoreCase(point.operationalAssignmentStatus) || point.companyId == null;
            String state = state(service, point, post);
            int pendingNews = pendingAssignment ? 0 : pendingNews(point.id, post.id);
            Company kaibil = pendingAssignment ? kaibilCompany() : null;
            rows.add(new OverviewRow(
                service.id, point.id, post.id, point.companyId,
                pendingAssignment ? (kaibil == null ? "Kaibil" : kaibil.name) : (company == null ? "—" : company.name),
                pendingAssignment ? (kaibil == null ? null : kaibil.logoDataUrl) : (company == null ? null : company.logoDataUrl),
                service.code, service.name, point.clientName,
                point.code, point.name, post.code, post.name,
                post.tier,
                metricValue(idMetrics.get(post.id)), metricValue(icMetrics.get(post.id)),
                pendingNews, state, pendingAssignment ? "PENDING" : "ASSIGNED",
                pendingAssignment && canAssignServices() && "ACTIVE".equalsIgnoreCase(service.commercialStatus) && "ACTIVE".equalsIgnoreCase(point.status),
                !pendingAssignment && canAssignServices() && canManagePoint(point),
                pendingAssignment && wasReturnedToCoordination(point.id)
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
        if ("PENDING".equalsIgnoreCase(point.operationalAssignmentStatus) || point.companyId == null) return "PENDING_ASSIGNMENT";
        if (!"CONFIGURED".equalsIgnoreCase(service.configStatus) || !"CONFIGURED".equalsIgnoreCase(post.configStatus)) return "TO_CONFIGURE";
        return "ACTIVE";
    }



    @GET
    @Path("/assignment-destinations")
    @RolesAllowed({"PRESIDENTE","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL"})
    @Transactional
    public List<AssignmentDestinationDto> assignmentDestinations(@QueryParam("pointId") UUID pointId) {
        if (pointId == null) throw new BadRequestException("Punto obligatorio.");
        PointEntity point = PointEntity.find("id=?1 and instanceCountryId=?2", pointId, tenant.instanceCountryId()).firstResult();
        if (point == null) throw new NotFoundException("Punto no encontrado.");
        if (!canManagePoint(point)) throw new ForbiddenException("El Servicio está fuera del alcance territorial del usuario.");
        ServiceEntity service = ServiceEntity.find("id=?1 and instanceCountryId=?2", point.serviceId, tenant.instanceCountryId()).firstResult();
        if (service == null) throw new NotFoundException("Servicio no encontrado.");
        if (!"ACTIVE".equalsIgnoreCase(service.commercialStatus) || !"ACTIVE".equalsIgnoreCase(point.status)) throw new BadRequestException("Solo puede asignarse un Servicio comercialmente Activo.");
        if (!"PENDING".equalsIgnoreCase(point.operationalAssignmentStatus) || point.companyId != null) throw new BadRequestException("El Servicio ya tiene Compañía operativa asignada.");
        return allowedDestinationCompanies().stream().map(c -> new AssignmentDestinationDto(c.id,c.code,c.name,c.zoneId,companyRegionIds(c))).toList();
    }

    @POST
    @Path("/points/{pointId}/assign-company")
    @RolesAllowed({"PRESIDENTE","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL"})
    @Transactional
    public AssignmentResultDto assignCompany(@PathParam("pointId") UUID pointId, AssignCompanyRequest req) {
        if (req == null || req.companyId() == null) throw new BadRequestException("Seleccione una Compañía destino.");
        PointEntity point = PointEntity.find("id=?1 and instanceCountryId=?2", pointId, tenant.instanceCountryId()).firstResult();
        if (point == null) throw new NotFoundException("Punto no encontrado.");
        if (!canManagePoint(point)) throw new ForbiddenException("El Servicio está fuera del alcance territorial del usuario.");
        ServiceEntity service = ServiceEntity.find("id=?1 and instanceCountryId=?2", point.serviceId, tenant.instanceCountryId()).firstResult();
        if (service == null) throw new NotFoundException("Servicio no encontrado.");
        if (!"ACTIVE".equalsIgnoreCase(service.commercialStatus) || !"ACTIVE".equalsIgnoreCase(point.status)) throw new BadRequestException("Solo puede asignarse un Servicio comercialmente Activo.");
        if (!"PENDING".equalsIgnoreCase(point.operationalAssignmentStatus) || point.companyId != null) throw conflict("El Servicio ya fue asignado a una Compañía operativa.");
        Company target = Company.find("id=?1 and instanceCountryId=?2", req.companyId(), tenant.instanceCountryId()).firstResult();
        if (target == null) throw new NotFoundException("Compañía destino no encontrada.");
        if (!"ACTIVE".equalsIgnoreCase(target.status)) throw new BadRequestException("La Compañía destino debe estar Activa.");
        if (target.alwaysActive || "COORDINATION".equalsIgnoreCase(target.companyType)) throw new BadRequestException("Kaibil no puede operar Servicios de clientes.");
        if (!allowedDestinationCompanies().stream().anyMatch(c -> Objects.equals(c.id,target.id))) throw new ForbiddenException("La Compañía destino está fuera del alcance territorial del usuario.");
        Instant now = Instant.now();
        UUID previous = point.companyId;
        point.companyId = target.id;
        point.operationalAssignmentStatus = "ASSIGNED";
        point.assignedByUsername = scope.username();
        point.assignedAt = now;
        if (point.operationalTransitionUntil != null && !point.operationalTransitionUntil.isAfter(now)) point.operationalTransitionUntil = null;

        ServiceCompanyAssignmentEvent event = new ServiceCompanyAssignmentEvent();
        event.instanceCountryId = tenant.instanceCountryId();
        event.serviceId = point.serviceId;
        event.pointId = point.id;
        event.originCompanyId = previous;
        event.destinationCompanyId = target.id;
        event.action = wasReturnedToCoordination(point.id) ? "REASSIGNMENT_FROM_COORDINATION" : "INITIAL_ASSIGNMENT";
        event.observations = req.observations()==null||req.observations().isBlank()?null:req.observations().trim();
        event.actorUsername = scope.username();
        event.occurredAt = now;
        event.releasedFutureAssignments = 0;
        event.retainedActiveAssignments = 0;
        event.persist();
        return new AssignmentResultDto(point.id,point.serviceId,target.id,target.name,point.operationalAssignmentStatus,now,scope.username());
    }

    @POST
    @Path("/points/{pointId}/return-to-coordination")
    @RolesAllowed({"PRESIDENTE","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL"})
    @Transactional
    public ReturnToCoordinationResultDto returnToCoordination(@PathParam("pointId") UUID pointId, ReturnToCoordinationRequest req) {
        PointEntity point = PointEntity.find("id=?1 and instanceCountryId=?2", pointId, tenant.instanceCountryId()).firstResult();
        if (point == null) throw new NotFoundException("Punto no encontrado.");
        if (!canManagePoint(point)) throw new ForbiddenException("El Servicio está fuera del alcance territorial del usuario.");
        if (point.companyId == null || !"ASSIGNED".equalsIgnoreCase(point.operationalAssignmentStatus)) throw conflict("El Servicio ya se encuentra en Kaibil o pendiente de asignación.");
        Company origin = Company.find("id=?1 and instanceCountryId=?2", point.companyId, tenant.instanceCountryId()).firstResult();
        if (origin == null) throw new NotFoundException("Compañía operadora no encontrada.");
        if (origin.alwaysActive || "COORDINATION".equalsIgnoreCase(origin.companyType)) throw new BadRequestException("Kaibil no opera Servicios de clientes.");

        Instant now = Instant.now();
        AssignmentReleaseSummary release = releaseFutureAssignmentsForPoint(point.id, now);
        UUID originId = point.companyId;
        point.companyId = null;
        point.operationalAssignmentStatus = "PENDING";
        point.assignedByUsername = null;
        point.assignedAt = null;
        point.operationalTransitionUntil = release.transitionUntil();

        ServiceCompanyAssignmentEvent event = new ServiceCompanyAssignmentEvent();
        event.instanceCountryId = tenant.instanceCountryId();
        event.serviceId = point.serviceId;
        event.pointId = point.id;
        event.originCompanyId = originId;
        event.destinationCompanyId = null;
        event.action = "RETURN_TO_COORDINATION";
        event.observations = req==null||req.observations()==null||req.observations().isBlank()?null:req.observations().trim();
        event.actorUsername = scope.username();
        event.occurredAt = now;
        event.releasedFutureAssignments = release.releasedFutureAssignments();
        event.retainedActiveAssignments = release.retainedActiveAssignments();
        event.persist();

        return new ReturnToCoordinationResultDto(point.id,point.serviceId,originId,origin.name,point.operationalAssignmentStatus,release.releasedFutureAssignments(),release.retainedActiveAssignments(),release.transitionUntil(),now,scope.username());
    }

    private record AssignmentReleaseSummary(int releasedFutureAssignments,int retainedActiveAssignments,Instant transitionUntil) {}

    private AssignmentReleaseSummary releaseFutureAssignmentsForPoint(UUID pointId, Instant now) {
        List<PostEntity> posts = PostEntity.list("instanceCountryId=?1 and pointId=?2", tenant.instanceCountryId(), pointId);
        if (posts.isEmpty()) return new AssignmentReleaseSummary(0,0,null);
        Set<UUID> postIds = posts.stream().map(p -> p.id).collect(Collectors.toSet());
        List<ShiftOccurrenceEntity> shifts = ShiftOccurrenceEntity.list("instanceCountryId=?1 and postId in ?2", tenant.instanceCountryId(), postIds);
        if (shifts.isEmpty()) return new AssignmentReleaseSummary(0,0,null);
        Map<UUID,ShiftOccurrenceEntity> shiftMap = shifts.stream().collect(Collectors.toMap(s -> s.id, Function.identity()));
        Set<UUID> shiftIds = shiftMap.keySet();
        List<OperationalAssignmentEntity> assignments = OperationalAssignmentEntity.list("instanceCountryId=?1 and shiftOccurrenceId in ?2 and status<>'REMOVED'", tenant.instanceCountryId(), shiftIds);
        int released = 0;
        int retained = 0;
        Instant transitionUntil = null;
        for (OperationalAssignmentEntity assignment : assignments) {
            ShiftOccurrenceEntity shift = shiftMap.get(assignment.shiftOccurrenceId);
            if (shift == null) continue;
            if (shift.startsAt.isAfter(now)) {
                assignment.status = "REMOVED";
                assignment.reassignmentReason = "SERVICE_RETURN_TO_COORDINATION";
                released++;
            } else if (!now.isBefore(shift.startsAt) && now.isBefore(shift.endsAt)) {
                retained++;
                if (transitionUntil == null || shift.endsAt.isAfter(transitionUntil)) transitionUntil = shift.endsAt;
            }
        }
        return new AssignmentReleaseSummary(released,retained,transitionUntil);
    }

    private boolean wasReturnedToCoordination(UUID pointId) {
        return ServiceCompanyAssignmentEvent.count("instanceCountryId=?1 and pointId=?2 and action='RETURN_TO_COORDINATION'", tenant.instanceCountryId(), pointId) > 0;
    }

    private boolean canAssignServices(){ return identity.getRoles().stream().anyMatch(SERVICE_ASSIGN_ROLES::contains); }

    private boolean canManagePoint(PointEntity point){
        if (identity.getRoles().contains("PRESIDENTE") || identity.getRoles().contains("DIRECTOR_NACIONAL")) return true;
        String province = point.province == null ? "" : point.province.trim().toLowerCase(Locale.ROOT);
        CountrySubdivision subdivision = CountrySubdivision.find("instanceCountryId=?1 and lower(name)=?2", tenant.instanceCountryId(), province).firstResult();
        if (subdivision == null) return false;
        List<UserOperationalScope> scopes = scope.scopes();
        if (identity.getRoles().contains("DIRECTOR_ZONAL")) return subdivision.zoneId != null && scopes.stream().anyMatch(x -> "ZONE".equals(x.scopeType) && Objects.equals(x.scopeId,subdivision.zoneId));
        if (identity.getRoles().contains("JEFE_REGIONAL")) return subdivision.regionId != null && scopes.stream().anyMatch(x -> "REGION".equals(x.scopeType) && Objects.equals(x.scopeId,subdivision.regionId));
        return false;
    }

    private List<Company> allowedDestinationCompanies(){
        if (!canAssignServices()) return List.of();
        List<Company> active = Company.list("instanceCountryId=?1 and status='ACTIVE' and alwaysActive=false order by name", tenant.instanceCountryId());
        if (identity.getRoles().contains("PRESIDENTE") || identity.getRoles().contains("DIRECTOR_NACIONAL")) return active;
        List<UserOperationalScope> scopes = scope.scopes();
        if (identity.getRoles().contains("DIRECTOR_ZONAL")) {
            Set<UUID> zones = scopes.stream().filter(x -> "ZONE".equals(x.scopeType) && x.scopeId != null).map(x -> x.scopeId).collect(Collectors.toSet());
            return active.stream().filter(c -> c.zoneId != null && zones.contains(c.zoneId)).toList();
        }
        if (identity.getRoles().contains("JEFE_REGIONAL")) {
            Set<UUID> regions = scopes.stream().filter(x -> "REGION".equals(x.scopeType) && x.scopeId != null).map(x -> x.scopeId).collect(Collectors.toSet());
            return active.stream().filter(c -> companyRegionIds(c).stream().anyMatch(regions::contains)).toList();
        }
        return List.of();
    }

    private List<UUID> companyRegionIds(Company c){
        List<CompanyRegion> links = CompanyRegion.list("instanceCountryId=?1 and companyId=?2", tenant.instanceCountryId(), c.id);
        if (!links.isEmpty()) return links.stream().map(x -> x.regionId).toList();
        return c.regionId == null ? List.of() : List.of(c.regionId);
    }

    private Company kaibilCompany(){ return Company.find("instanceCountryId=?1 and alwaysActive=true", tenant.instanceCountryId()).firstResult(); }
    private WebApplicationException conflict(String message){ return new WebApplicationException(message, Response.Status.CONFLICT); }

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
