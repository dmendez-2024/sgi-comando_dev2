package com.cajamarca.sgi.comando.companies;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.operations.ServiceEntity;
import com.cajamarca.sgi.comando.territory.CountrySubdivision;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import com.cajamarca.sgi.comando.territory.TerritoryRegion;
import com.cajamarca.sgi.comando.territory.TerritoryZone;
import com.cajamarca.sgi.comando.territory.UserOperationalScope;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.panache.common.Page;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Path("/api/companies")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CompanyResource {
    private static final Set<String> ACTIVE_SERVICE_STATUSES = Set.of("ACTIVE", "VIGENTE");

    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject ObjectMapper mapper;

    public record CompanyRequest(
        String name,
        String status,
        UUID zoneId,
        List<UUID> regionIds,
        String logoDataUrl,
        String historicalReview,
        String changeReason
    ) {}

    public record RegionLoadDto(UUID regionId, int activeServiceCount) {}
    public record ServiceBlockerDto(UUID id, String code, String name, String clientName, UUID regionId) {}
    public record CompanyDto(
        UUID id,
        String code,
        String name,
        String status,
        int requiredChangeCount,
        UUID zoneId,
        List<UUID> regionIds,
        String logoDataUrl,
        String historicalReview,
        int versionNumber,
        int activeServiceCount,
        List<RegionLoadDto> regionLoads,
        List<ServiceBlockerDto> activeServices
    ) {}
    public record CompanyVersionDto(
        int versionNumber,
        String changeType,
        String changeReason,
        String actorUsername,
        Instant effectiveAt,
        String snapshotJson
    ) {}
    public record PageResponse<T>(List<T> items, long total, int page, int size) {}

    @GET
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
    public PageResponse<CompanyDto> list(@QueryParam("page") @DefaultValue("0") int page,
                                         @QueryParam("size") @DefaultValue("50") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Set<UUID> allowed = visibleCompanyIdsForCom();
        if (allowed.isEmpty()) return new PageResponse<>(List.of(), 0, safePage, safeSize);
        var q = Company.find("instanceCountryId = ?1 and id in ?2 order by name", tenant.instanceCountryId(), allowed);
        long total = q.count();
        List<Company> companies = q.page(Page.of(safePage, safeSize)).list();
        return new PageResponse<>(companies.stream().map(this::dto).toList(), total, safePage, safeSize);
    }

    @GET @Path("/{id}")
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
    public CompanyDto detail(@PathParam("id") UUID id) {
        requireComCompany(id);
        Company c = company(id);
        return dto(c);
    }

    @GET @Path("/{id}/history")
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
    public List<CompanyVersionDto> history(@PathParam("id") UUID id) {
        requireComCompany(id);
        List<CompanyVersion> versions = CompanyVersion.list(
            "instanceCountryId=?1 and companyId=?2 order by versionNumber desc",
            tenant.instanceCountryId(), id
        );
        return versions.stream().map(v -> new CompanyVersionDto(
            v.versionNumber, v.changeType, v.changeReason, v.actorUsername, v.effectiveAt, v.snapshotJson
        )).toList();
    }

    @POST
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL"})
    public Response create(CompanyRequest req) {
        validateBase(req);
        validateNameUnique(req.name().trim(), null);
        TerritorialSelection selection = validateTerritory(req.zoneId(), req.regionIds());
        requireTargetZone(selection.zone().id);

        Company c = new Company();
        c.instanceCountryId = tenant.instanceCountryId();
        c.code = nextCode();
        c.name = req.name().trim();
        c.status = normalizeStatus(req.status(), "ACTIVE");
        if ("INACTIVE".equals(c.status)) throw new BadRequestException("Una Compañía nueva no puede crearse Inactiva.");
        c.requiredChangeCount = 0;
        c.zoneId = selection.zone().id;
        c.regionId = selection.regions().get(0).id; // compatibilidad con TER v1.0 congelada
        c.logoDataUrl = normalizeLogo(req.logoDataUrl());
        c.historicalReview = normalizeReview(req.historicalReview());
        c.versionNumber = 1;
        c.persist();
        replaceRegions(c, selection.regions());
        persistVersion(c, "CREATED", req.changeReason());
        return Response.status(Response.Status.CREATED).entity(dto(c)).build();
    }

    @PUT @Path("/{id}")
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL"})
    public CompanyDto update(@PathParam("id") UUID id, CompanyRequest req) {
        requireComCompany(id);
        Company c = company(id);
        validateBase(req);
        validateNameUnique(req.name().trim(), c.id);
        TerritorialSelection selection = validateTerritory(req.zoneId(), req.regionIds());
        requireTargetZone(selection.zone().id);

        List<UUID> currentRegions = regionIds(c.id);
        Set<UUID> nextRegions = selection.regions().stream().map(r -> r.id).collect(Collectors.toCollection(LinkedHashSet::new));
        for (UUID oldRegion : currentRegions) {
            if (!nextRegions.contains(oldRegion)) {
                int blockers = activeServicesInRegion(c.id, oldRegion).size();
                if (blockers > 0) {
                    throw conflict("No se puede retirar la Región porque existen " + blockers + " Servicio(s) activo(s) de la Compañía en ella.");
                }
            }
        }

        String nextStatus = normalizeStatus(req.status(), c.status);
        if (!Set.of("ACTIVE", "INACTIVE", "DRAFT").contains(nextStatus)) throw new BadRequestException("Estado inválido");
        if (!Objects.equals(c.status, nextStatus) && "INACTIVE".equals(nextStatus)) {
            int active = activeServices(c.id).size();
            if (active > 0) {
                throw conflict("No se puede inactivar la Compañía. Primero debe migrar o finalizar sus " + active + " Servicio(s) activo(s).");
            }
        }

        String previousStatus = c.status;
        UUID previousZone = c.zoneId;
        Set<UUID> previousRegions = new LinkedHashSet<>(currentRegions);

        c.name = req.name().trim();
        c.status = nextStatus;
        c.zoneId = selection.zone().id;
        c.regionId = selection.regions().get(0).id; // alias de compatibilidad; COM usa company_region
        c.logoDataUrl = normalizeLogo(req.logoDataUrl());
        c.historicalReview = normalizeReview(req.historicalReview());
        replaceRegions(c, selection.regions());
        c.versionNumber = Math.max(c.versionNumber, 1) + 1;

        String changeType = "UPDATED";
        if (!Objects.equals(previousStatus, c.status) && "INACTIVE".equals(c.status)) changeType = "INACTIVATED";
        else if (!Objects.equals(previousStatus, c.status) && "ACTIVE".equals(c.status)) changeType = "REACTIVATED";
        else if (!Objects.equals(previousZone, c.zoneId) || !previousRegions.equals(nextRegions)) changeType = "TERRITORY_UPDATED";

        persistVersion(c, changeType, req.changeReason());
        return dto(c);
    }

    private void validateBase(CompanyRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()) throw new BadRequestException("Nombre obligatorio");
        if (req.name().trim().length() > 160) throw new BadRequestException("El nombre no puede superar 160 caracteres.");
        if (req.zoneId() == null) throw new BadRequestException("Zona obligatoria");
        if (req.regionIds() == null || req.regionIds().isEmpty()) throw new BadRequestException("Debe seleccionar al menos una Región.");
        normalizeReview(req.historicalReview());
        normalizeLogo(req.logoDataUrl());
    }

    private void validateNameUnique(String name, UUID selfId) {
        List<Company> same = Company.list("instanceCountryId=?1 and lower(name)=lower(?2)", tenant.instanceCountryId(), name);
        boolean duplicate = same.stream().anyMatch(c -> selfId == null || !Objects.equals(c.id, selfId));
        if (duplicate) throw conflict("Ya existe una Compañía con ese nombre.");
    }

    private record TerritorialSelection(TerritoryZone zone, List<TerritoryRegion> regions) {}

    private TerritorialSelection validateTerritory(UUID zoneId, List<UUID> regionIds) {
        TerritoryZone zone = TerritoryZone.find("id=?1 and instanceCountryId=?2", zoneId, tenant.instanceCountryId()).firstResult();
        if (zone == null) throw new BadRequestException("Zona inválida");
        LinkedHashSet<UUID> uniqueIds = new LinkedHashSet<>(regionIds);
        if (uniqueIds.size() != regionIds.size()) throw new BadRequestException("No repita Regiones.");
        List<TerritoryRegion> regions = new ArrayList<>();
        for (UUID regionId : uniqueIds) {
            TerritoryRegion region = TerritoryRegion.find("id=?1 and instanceCountryId=?2", regionId, tenant.instanceCountryId()).firstResult();
            if (region == null) throw new BadRequestException("Región inválida");
            if (!Objects.equals(region.zoneId, zone.id)) throw new BadRequestException("Todas las Regiones de una Compañía deben pertenecer a su única Zona.");
            regions.add(region);
        }
        return new TerritorialSelection(zone, regions);
    }

    private void replaceRegions(Company c, List<TerritoryRegion> regions) {
        CompanyRegion.delete("instanceCountryId=?1 and companyId=?2", tenant.instanceCountryId(), c.id);
        for (TerritoryRegion region : regions) {
            CompanyRegion link = new CompanyRegion();
            link.instanceCountryId = tenant.instanceCountryId();
            link.companyId = c.id;
            link.regionId = region.id;
            link.persist();
        }
    }

    private CompanyDto dto(Company c) {
        List<UUID> regions = regionIds(c.id);
        List<ServiceBlockerDto> blockers = activeServices(c.id);
        List<RegionLoadDto> loads = regions.stream()
            .map(r -> new RegionLoadDto(r, activeServicesInRegion(c.id, r).size()))
            .toList();
        return new CompanyDto(
            c.id, c.code, c.name, c.status, c.requiredChangeCount, c.zoneId, regions,
            c.logoDataUrl, c.historicalReview, Math.max(c.versionNumber, 1), blockers.size(), loads, blockers
        );
    }

    private List<UUID> regionIds(UUID companyId) {
        List<CompanyRegion> links = CompanyRegion.list(
            "instanceCountryId=?1 and companyId=?2 order by createdAt, regionId",
            tenant.instanceCountryId(), companyId
        );
        if (!links.isEmpty()) return links.stream().map(l -> l.regionId).toList();
        Company c = company(companyId);
        return c.regionId == null ? List.of() : List.of(c.regionId);
    }

    private List<ServiceBlockerDto> activeServices(UUID companyId) {
        return activeServiceBlockers(companyId, null);
    }

    private List<ServiceBlockerDto> activeServicesInRegion(UUID companyId, UUID regionId) {
        return activeServiceBlockers(companyId, regionId);
    }

    private List<ServiceBlockerDto> activeServiceBlockers(UUID companyId, UUID filterRegionId) {
        List<PointEntity> points = PointEntity.list(
            "instanceCountryId=?1 and companyId=?2 and status='ACTIVE'",
            tenant.instanceCountryId(), companyId
        );
        if (points.isEmpty()) return List.of();

        Map<String, UUID> regionBySubdivisionName = new HashMap<>();
        List<CountrySubdivision> subdivisions = CountrySubdivision.list("instanceCountryId=?1", tenant.instanceCountryId());
        for (CountrySubdivision subdivision : subdivisions) {
            if (subdivision.regionId != null) regionBySubdivisionName.put(normalizeKey(subdivision.name), subdivision.regionId);
        }

        LinkedHashMap<UUID, ServiceBlockerDto> out = new LinkedHashMap<>();
        for (PointEntity point : points) {
            UUID pointRegion = regionBySubdivisionName.get(normalizeKey(point.province));
            if (filterRegionId != null && !Objects.equals(filterRegionId, pointRegion)) continue;
            ServiceEntity service = ServiceEntity.find(
                "id=?1 and instanceCountryId=?2", point.serviceId, tenant.instanceCountryId()
            ).firstResult();
            if (service == null || !ACTIVE_SERVICE_STATUSES.contains(service.commercialStatus == null ? "" : service.commercialStatus.toUpperCase(Locale.ROOT))) continue;
            out.putIfAbsent(service.id, new ServiceBlockerDto(service.id, service.code, service.name, service.clientName, pointRegion));
        }
        return new ArrayList<>(out.values());
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeReview(String value) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim();
        if (clean.length() > 750) throw new BadRequestException("La reseña histórica no puede superar 750 caracteres.");
        return clean;
    }

    private String normalizeLogo(String value) {
        if (value == null || value.isBlank()) return null;
        String clean = value.trim();
        if (!clean.startsWith("data:image/png;base64,") && !clean.startsWith("data:image/jpeg;base64,") && !clean.startsWith("data:image/webp;base64,")) {
            throw new BadRequestException("El logo debe ser PNG, JPG o WEBP.");
        }
        if (clean.length() > 500_000) throw new BadRequestException("El logo supera el tamaño permitido para esta UAT.");
        return clean;
    }

    private String normalizeStatus(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private void persistVersion(Company c, String changeType, String reason) {
        CompanyVersion v = new CompanyVersion();
        v.instanceCountryId = tenant.instanceCountryId();
        v.companyId = c.id;
        v.versionNumber = Math.max(c.versionNumber, 1);
        v.changeType = changeType;
        v.changeReason = reason == null || reason.isBlank() ? null : reason.trim();
        v.actorUsername = scope.username();
        v.effectiveAt = Instant.now();
        v.snapshotJson = snapshot(c);
        v.persist();
    }

    private String snapshot(Company c) {
        try {
            ObjectNode node = mapper.createObjectNode();
            node.put("code", c.code);
            node.put("name", c.name);
            node.put("status", c.status);
            if (c.zoneId != null) node.put("zoneId", c.zoneId.toString()); else node.putNull("zoneId");
            ArrayNode regionArray = node.putArray("regionIds");
            for (UUID regionId : regionIds(c.id)) regionArray.add(regionId.toString());
            if (c.historicalReview != null) node.put("historicalReview", c.historicalReview); else node.putNull("historicalReview");
            if (c.logoDataUrl != null) node.put("logoDataUrl", c.logoDataUrl); else node.putNull("logoDataUrl");
            node.put("requiredChangeCount", c.requiredChangeCount);
            node.put("versionNumber", Math.max(c.versionNumber, 1));
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new InternalServerErrorException("No se pudo generar el snapshot versionado de la Compañía.");
        }
    }

    private Company company(UUID id) {
        Company c = Company.find("id=?1 and instanceCountryId=?2", id, tenant.instanceCountryId()).firstResult();
        if (c == null) throw new NotFoundException();
        return c;
    }

    /** Alcance de lectura de COM compatible con Compañías multi-región sin modificar TER v1.0. */
    private Set<UUID> visibleCompanyIdsForCom() {
        if (scope.countryWide()) {
            List<Company> all = Company.list("instanceCountryId=?1", tenant.instanceCountryId());
            return all.stream().map(c -> c.id).collect(Collectors.toCollection(LinkedHashSet::new));
        }
        LinkedHashSet<UUID> out = new LinkedHashSet<>();
        for (UserOperationalScope s : scope.scopes()) {
            if (s.scopeId == null) continue;
            if ("ZONE".equals(s.scopeType)) {
                List<Company> companies = Company.list("instanceCountryId=?1 and zoneId=?2", tenant.instanceCountryId(), s.scopeId);
                companies.forEach(c -> out.add(c.id));
            } else if ("REGION".equals(s.scopeType)) {
                List<CompanyRegion> links = CompanyRegion.list("instanceCountryId=?1 and regionId=?2", tenant.instanceCountryId(), s.scopeId);
                links.forEach(link -> out.add(link.companyId));
            } else if ("COMPANY".equals(s.scopeType)) {
                out.add(s.scopeId);
            }
        }
        return out;
    }

    private void requireComCompany(UUID companyId) {
        if (companyId == null || !visibleCompanyIdsForCom().contains(companyId)) {
            throw new ForbiddenException("La Compañía está fuera del alcance territorial del usuario.");
        }
    }

    private void requireTargetZone(UUID zoneId) {
        if (scope.countryWide()) return;
        boolean allowed = scope.scopes().stream().anyMatch(s -> "ZONE".equals(s.scopeType) && Objects.equals(s.scopeId, zoneId));
        if (!allowed) throw new ForbiddenException("La Zona está fuera del alcance territorial del usuario.");
    }

    private WebApplicationException conflict(String message) {
        return new WebApplicationException(message, Response.Status.CONFLICT);
    }

    private String nextCode() {
        long n = Company.count("instanceCountryId", tenant.instanceCountryId()) + 1;
        return "COM-%03d".formatted(n);
    }
}
