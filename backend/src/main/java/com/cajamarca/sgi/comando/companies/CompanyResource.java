package com.cajamarca.sgi.comando.companies;

import com.cajamarca.sgi.comando.assignments.EmployeeOperationalSnapshot;
import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.core.CoreCatalogService;
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
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;

@Path("/api/companies")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CompanyResource {
    private static final Logger LOG = Logger.getLogger(CompanyResource.class);
    private static final Set<String> ACTIVE_SERVICE_STATUSES = Set.of("ACTIVE", "VIGENTE");
    private static final Set<String> KAIBIL_ROLES = Set.of("PRESIDENTE","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL");

    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject ObjectMapper mapper;
    @Inject SecurityIdentity identity;
    @Inject CoreCatalogService coreCatalogService;

    public record CompanyUpdateRequest(String status, UUID zoneId, List<UUID> regionIds, UUID responsibleEmployeeId, String changeReason) {}
    public record CompanyActivationRequest(UUID coreCompanyId, String status, UUID zoneId, List<UUID> regionIds, String changeReason) {}
    public record CoreCompanyDto(UUID coreCompanyId,String code,String name,String historicalReview,String logoDataUrl,String companyType,String sourceVersion,String sourceStatus,boolean activated) {}
    public record RegionLoadDto(UUID regionId, int activeServiceCount) {}
    public record ServiceBlockerDto(UUID id, String code, String name, String clientName, UUID regionId) {}
    public record ResponsibleDto(UUID employeeId, String fullName, String roleCode) {}
    public record CompanyDto(
        UUID id,String code,String name,String status,int requiredChangeCount,UUID zoneId,List<UUID> regionIds,
        String logoDataUrl,String historicalReview,int versionNumber,int activeServiceCount,List<RegionLoadDto> regionLoads,List<ServiceBlockerDto> activeServices,
        UUID coreCompanyId,String sourceSystem,String sourceVersion,String companyType,boolean alwaysActive,
        UUID responsibleEmployeeId,String responsibleName,String responsibleRoleCode
    ) {}
    public record CompanyVersionDto(int versionNumber,String changeType,String changeReason,String actorUsername,Instant effectiveAt,String snapshotJson) {}
    public record PageResponse<T>(List<T> items,long total,int page,int size) {}

    @GET
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
    public PageResponse<CompanyDto> list(@QueryParam("page") @DefaultValue("0") int page,@QueryParam("size") @DefaultValue("50") int size) {
        int safePage=Math.max(page,0),safeSize=Math.min(Math.max(size,1),100);
        Set<UUID> allowed=visibleCompanyIdsForCom();
        if(allowed.isEmpty()) return new PageResponse<>(List.of(),0,safePage,safeSize);
        var q=Company.find("instanceCountryId=?1 and id in ?2 order by name",tenant.instanceCountryId(),allowed);
        long total=q.count();
        List<Company> rows=q.page(Page.of(safePage,safeSize)).list();
        return new PageResponse<>(rows.stream().map(this::dto).toList(),total,safePage,safeSize);
    }

    @GET @Path("/core-catalog")
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL"})
    public List<CoreCompanyDto> coreCatalog(){
        try{coreCatalogService.synchronizeActiveCompanies();}
        catch(RuntimeException e){LOG.warn("No se pudo actualizar el catálogo de Compañías desde CORE; se conserva el último snapshot local.",e);}
        List<CoreCompanyCatalogSnapshot> rows=CoreCompanyCatalogSnapshot.list("instanceCountryId=?1 and sourceStatus='ACTIVE' order by name",tenant.instanceCountryId());
        Set<UUID> active=Company.<Company>list("instanceCountryId=?1 and coreCatalogId is not null",tenant.instanceCountryId()).stream().map(c->c.coreCatalogId).collect(Collectors.toSet());
        return rows.stream().map(c->new CoreCompanyDto(c.coreCompanyId,c.code,c.name,c.historicalReview,c.logoDataUrl,c.companyType,c.sourceVersion,c.sourceStatus,active.contains(c.coreCompanyId))).toList();
    }

    @GET @Path("/responsibles")
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL"})
    public List<ResponsibleDto> responsibles(){
        Set<UUID> companyIds=visibleCompanyIdsForCom();
        if(companyIds.isEmpty())return List.of();
        List<EmployeeOperationalSnapshot> people=EmployeeOperationalSnapshot.list(
            "instanceCountryId=?1 and companyId in ?2 and employmentStatus='ACTIVE' " +
                "and (lower(roleCode) like ?3 or lower(roleCode) like ?4 or lower(roleCode) like ?5 or lower(roleCode) like ?6) " +
                "order by fullName",
            tenant.instanceCountryId(),companyIds,"%coordinador%","%jefe%","%director%","%presidente%"
        );
        return people.stream().map(e->new ResponsibleDto(e.employeeId,e.fullName,e.roleCode)).toList();
    }

    @POST @Path("/activate")
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL"})
    public Response activate(CompanyActivationRequest req){
        if(req==null||req.coreCompanyId()==null) throw new BadRequestException("Seleccione una Compañía del catálogo CORE.");
        CoreCompanyCatalogSnapshot core=CoreCompanyCatalogSnapshot.find("instanceCountryId=?1 and coreCompanyId=?2",tenant.instanceCountryId(),req.coreCompanyId()).firstResult();
        if(core==null||!"ACTIVE".equals(core.sourceStatus)) throw new BadRequestException("La Compañía no está disponible en CORE.");
        Company alreadyBound=Company.find("instanceCountryId=?1 and coreCatalogId=?2",tenant.instanceCountryId(),core.coreCompanyId).firstResult();
        if(alreadyBound!=null) throw conflict("La Compañía ya está activada en SGI: Comando.");
        boolean kaibil="COORDINATION".equals(core.companyType);
        TerritorialSelection selection=kaibil?new TerritorialSelection(null,List.of()):validateTerritory(req.zoneId(),req.regionIds());
        if(!kaibil) requireTargetZone(selection.zone().id);

        // Upgrade-safe activation: older UAT databases may contain a locally-created Company
        // with the same CORE name and/or code. Reuse that UUID instead of creating a duplicate.
        Company byName=Company.find("instanceCountryId=?1 and lower(name)=?2",tenant.instanceCountryId(),core.name.trim().toLowerCase(Locale.ROOT)).firstResult();
        Company byCode=Company.find("instanceCountryId=?1 and code=?2",tenant.instanceCountryId(),core.code).firstResult();
        Company c=byName!=null?byName:byCode;
        boolean reused=c!=null;
        if(c!=null&&c.coreCatalogId!=null&&!Objects.equals(c.coreCatalogId,core.coreCompanyId)) throw conflict("Existe una Compañía local con la misma identidad pero ya está vinculada a otro registro CORE.");
        if(c==null){c=new Company();c.instanceCountryId=tenant.instanceCountryId();c.code=core.code;c.requiredChangeCount=0;c.versionNumber=1;}
        else c.versionNumber=Math.max(c.versionNumber,1)+1;
        c.name=core.name;c.status="ACTIVE";c.zoneId=kaibil?null:selection.zone().id;c.regionId=kaibil?null:selection.regions().get(0).id;c.logoDataUrl=core.logoDataUrl;c.historicalReview=core.historicalReview;c.coreCatalogId=core.coreCompanyId;c.sourceSystem="CORE";c.sourceVersion=core.sourceVersion;c.companyType=core.companyType;c.alwaysActive=kaibil;
        if(!reused)c.persist();
        if(kaibil)CompanyRegion.delete("instanceCountryId=?1 and companyId=?2",tenant.instanceCountryId(),c.id);else replaceRegions(c,selection.regions());
        persistVersion(c,"CORE_ACTIVATED",req.changeReason());
        return Response.status(reused?Response.Status.OK:Response.Status.CREATED).entity(dto(c)).build();
    }

    /** Direct creation is intentionally disabled: CORE owns company identity. */
    @POST
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL"})
    public Response createDisabled(Object ignored){throw new BadRequestException("Las Compañías no se crean desde cero en SGI: Comando. Use Activar desde CORE.");}

    @GET @Path("/{id}")
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
    public CompanyDto detail(@PathParam("id") UUID id){requireComCompany(id);return dto(company(id));}

    @GET @Path("/{id}/history")
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
    public List<CompanyVersionDto> history(@PathParam("id") UUID id){
        requireComCompany(id);List<CompanyVersion> versions=CompanyVersion.list("instanceCountryId=?1 and companyId=?2 order by versionNumber desc",tenant.instanceCountryId(),id);
        return versions.stream().map(v->new CompanyVersionDto(v.versionNumber,v.changeType,v.changeReason,v.actorUsername,v.effectiveAt,v.snapshotJson)).toList();
    }

    @PUT @Path("/{id}")
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL"})
    public CompanyDto update(@PathParam("id") UUID id,CompanyUpdateRequest req){
        requireComCompany(id);if(req==null)throw new BadRequestException("Solicitud obligatoria");Company c=company(id);CoreCompanyCatalogSnapshot core=core(c);
        boolean kaibil=c.alwaysActive||"COORDINATION".equals(c.companyType);
        TerritorialSelection selection=kaibil?new TerritorialSelection(null,List.of()):validateTerritory(req.zoneId(),req.regionIds());
        if(!kaibil)requireTargetZone(selection.zone().id);
        List<UUID> currentRegions=regionIds(c.id);Set<UUID> nextRegions=kaibil?Set.of():selection.regions().stream().map(r->r.id).collect(Collectors.toCollection(LinkedHashSet::new));
        for(UUID oldRegion:currentRegions)if(!nextRegions.contains(oldRegion)){int blockers=activeServicesInRegion(c.id,oldRegion).size();if(blockers>0)throw conflict("No se puede retirar la Región porque existen "+blockers+" Servicio(s) activo(s) de la Compañía en ella.");}
        String nextStatus=kaibil?"ACTIVE":normalizeStatus(req.status(),c.status);
        if(!Set.of("ACTIVE","INACTIVE","DRAFT").contains(nextStatus))throw new BadRequestException("Estado inválido");
        if("INACTIVE".equals(nextStatus)&&activeServices(c.id).size()>0)throw conflict("No se puede inactivar la Compañía mientras tenga Servicios activos.");
        EmployeeOperationalSnapshot responsible=validateResponsible(req.responsibleEmployeeId());
        String previousStatus=c.status;UUID previousZone=c.zoneId,previousResponsible=c.responsibleEmployeeId;Set<UUID> previousRegions=new LinkedHashSet<>(currentRegions);
        // Identity always comes from CORE and is never accepted from the SGI request.
        if(core!=null){c.name=core.name;c.logoDataUrl=core.logoDataUrl;c.historicalReview=core.historicalReview;c.sourceVersion=core.sourceVersion;c.sourceSystem="CORE";c.companyType=core.companyType;}
        c.status=nextStatus;c.zoneId=kaibil?null:selection.zone().id;c.regionId=kaibil?null:selection.regions().get(0).id;c.responsibleEmployeeId=responsible==null?null:responsible.employeeId;
        if(kaibil)CompanyRegion.delete("instanceCountryId=?1 and companyId=?2",tenant.instanceCountryId(),c.id);else replaceRegions(c,selection.regions());
        c.versionNumber=Math.max(c.versionNumber,1)+1;
        String changeType="UPDATED";if(!Objects.equals(previousStatus,c.status)&&"INACTIVE".equals(c.status))changeType="INACTIVATED";else if(!Objects.equals(previousStatus,c.status)&&"ACTIVE".equals(c.status))changeType="REACTIVATED";else if(!Objects.equals(previousZone,c.zoneId)||!previousRegions.equals(nextRegions))changeType="TERRITORY_UPDATED";else if(!Objects.equals(previousResponsible,c.responsibleEmployeeId))changeType="RESPONSIBLE_UPDATED";
        persistVersion(c,changeType,req.changeReason());return dto(c);
    }

    private record TerritorialSelection(TerritoryZone zone,List<TerritoryRegion> regions){}
    private TerritorialSelection validateTerritory(UUID zoneId,List<UUID> regionIds){
        if(zoneId==null)throw new BadRequestException("Zona obligatoria");if(regionIds==null||regionIds.isEmpty())throw new BadRequestException("Debe seleccionar al menos una Región.");
        TerritoryZone zone=TerritoryZone.find("id=?1 and instanceCountryId=?2",zoneId,tenant.instanceCountryId()).firstResult();if(zone==null)throw new BadRequestException("Zona inválida");
        LinkedHashSet<UUID> unique=new LinkedHashSet<>(regionIds);if(unique.size()!=regionIds.size())throw new BadRequestException("No repita Regiones.");List<TerritoryRegion> regions=new ArrayList<>();
        for(UUID regionId:unique){TerritoryRegion r=TerritoryRegion.find("id=?1 and instanceCountryId=?2",regionId,tenant.instanceCountryId()).firstResult();if(r==null)throw new BadRequestException("Región inválida");if(!Objects.equals(r.zoneId,zone.id))throw new BadRequestException("Todas las Regiones deben pertenecer a la misma Zona.");regions.add(r);}return new TerritorialSelection(zone,regions);
    }
    private void replaceRegions(Company c,List<TerritoryRegion> regions){CompanyRegion.delete("instanceCountryId=?1 and companyId=?2",tenant.instanceCountryId(),c.id);for(TerritoryRegion r:regions){CompanyRegion link=new CompanyRegion();link.instanceCountryId=tenant.instanceCountryId();link.companyId=c.id;link.regionId=r.id;link.persist();}}

    private CompanyDto dto(Company c){
        CoreCompanyCatalogSnapshot core=core(c);String name=core==null?c.name:core.name,logo=core==null?c.logoDataUrl:core.logoDataUrl,review=core==null?c.historicalReview:core.historicalReview,sourceVersion=core==null?c.sourceVersion:core.sourceVersion,companyType=core==null?c.companyType:core.companyType;
        List<UUID> regions=regionIds(c.id);List<ServiceBlockerDto> blockers=activeServices(c.id);List<RegionLoadDto> loads=regions.stream().map(r->new RegionLoadDto(r,activeServicesInRegion(c.id,r).size())).toList();
        EmployeeOperationalSnapshot responsible=responsible(c.responsibleEmployeeId);
        return new CompanyDto(c.id,c.code,name,c.status,c.requiredChangeCount,c.zoneId,regions,logo,review,Math.max(c.versionNumber,1),blockers.size(),loads,blockers,c.coreCatalogId,c.sourceSystem,sourceVersion,companyType,c.alwaysActive,c.responsibleEmployeeId,responsible==null?null:responsible.fullName,responsible==null?null:responsible.roleCode);
    }
    private CoreCompanyCatalogSnapshot core(Company c){return c.coreCatalogId==null?null:CoreCompanyCatalogSnapshot.find("instanceCountryId=?1 and coreCompanyId=?2",tenant.instanceCountryId(),c.coreCatalogId).firstResult();}
    private List<UUID> regionIds(UUID companyId){List<CompanyRegion> links=CompanyRegion.list("instanceCountryId=?1 and companyId=?2 order by createdAt,regionId",tenant.instanceCountryId(),companyId);if(!links.isEmpty())return links.stream().map(l->l.regionId).toList();Company c=company(companyId);return c.regionId==null?List.of():List.of(c.regionId);}
    private List<ServiceBlockerDto> activeServices(UUID companyId){return activeServiceBlockers(companyId,null);}private List<ServiceBlockerDto> activeServicesInRegion(UUID companyId,UUID regionId){return activeServiceBlockers(companyId,regionId);}
    private List<ServiceBlockerDto> activeServiceBlockers(UUID companyId,UUID filterRegionId){List<PointEntity> points=PointEntity.list("instanceCountryId=?1 and companyId=?2 and status='ACTIVE'",tenant.instanceCountryId(),companyId);if(points.isEmpty())return List.of();Map<String,UUID> regionBySubdivisionName=new HashMap<>();List<CountrySubdivision> subdivisions=CountrySubdivision.list("instanceCountryId=?1",tenant.instanceCountryId());for(CountrySubdivision s:subdivisions)if(s.regionId!=null)regionBySubdivisionName.put(normalizeKey(s.name),s.regionId);LinkedHashMap<UUID,ServiceBlockerDto> out=new LinkedHashMap<>();for(PointEntity point:points){UUID pointRegion=regionBySubdivisionName.get(normalizeKey(point.province));if(filterRegionId!=null&&!Objects.equals(filterRegionId,pointRegion))continue;ServiceEntity service=ServiceEntity.find("id=?1 and instanceCountryId=?2",point.serviceId,tenant.instanceCountryId()).firstResult();if(service==null||!ACTIVE_SERVICE_STATUSES.contains(service.commercialStatus==null?"":service.commercialStatus.toUpperCase(Locale.ROOT)))continue;out.putIfAbsent(service.id,new ServiceBlockerDto(service.id,service.code,service.name,service.clientName,pointRegion));}return new ArrayList<>(out.values());}
    private String normalizeKey(String value){return value==null?"":value.trim().toLowerCase(Locale.ROOT);}private String normalizeStatus(String value,String fallback){return value==null||value.isBlank()?fallback:value.trim().toUpperCase(Locale.ROOT);}

    private void persistVersion(Company c,String changeType,String reason){CompanyVersion v=new CompanyVersion();v.instanceCountryId=tenant.instanceCountryId();v.companyId=c.id;v.versionNumber=Math.max(c.versionNumber,1);v.changeType=changeType;v.changeReason=reason==null||reason.isBlank()?null:reason.trim();v.actorUsername=scope.username();v.effectiveAt=Instant.now();v.snapshotJson=snapshot(c);v.persist();}
    private String snapshot(Company c){try{CoreCompanyCatalogSnapshot core=core(c);EmployeeOperationalSnapshot responsible=responsible(c.responsibleEmployeeId);ObjectNode node=mapper.createObjectNode();node.put("code",c.code);node.put("name",core==null?c.name:core.name);node.put("status",c.status);if(c.zoneId!=null)node.put("zoneId",c.zoneId.toString());else node.putNull("zoneId");ArrayNode arr=node.putArray("regionIds");for(UUID r:regionIds(c.id))arr.add(r.toString());if(c.responsibleEmployeeId!=null)node.put("responsibleEmployeeId",c.responsibleEmployeeId.toString());else node.putNull("responsibleEmployeeId");if(responsible!=null){node.put("responsibleName",responsible.fullName);node.put("responsibleRoleCode",responsible.roleCode);}else{node.putNull("responsibleName");node.putNull("responsibleRoleCode");}String review=core==null?c.historicalReview:core.historicalReview,StringLogo=core==null?c.logoDataUrl:core.logoDataUrl;if(review!=null)node.put("historicalReview",review);else node.putNull("historicalReview");if(StringLogo!=null)node.put("logoDataUrl",StringLogo);else node.putNull("logoDataUrl");node.put("sourceSystem",c.sourceSystem);node.put("sourceVersion",c.sourceVersion);node.put("companyType",c.companyType);node.put("alwaysActive",c.alwaysActive);node.put("requiredChangeCount",c.requiredChangeCount);node.put("versionNumber",Math.max(c.versionNumber,1));return mapper.writeValueAsString(node);}catch(JsonProcessingException e){throw new InternalServerErrorException("No se pudo generar snapshot de Compañía.");}}
    private EmployeeOperationalSnapshot responsible(UUID employeeId){return employeeId==null?null:EmployeeOperationalSnapshot.find("instanceCountryId=?1 and employeeId=?2",tenant.instanceCountryId(),employeeId).firstResult();}
    private EmployeeOperationalSnapshot validateResponsible(UUID employeeId){
        if(employeeId==null)return null;
        EmployeeOperationalSnapshot employee=responsible(employeeId);
        if(employee==null||!"ACTIVE".equalsIgnoreCase(employee.employmentStatus))throw new BadRequestException("El responsable seleccionado no existe o no está activo.");
        if(!isEligibleResponsibleRole(employee.roleCode))throw new BadRequestException("El responsable debe tener un cargo de Coordinador, Jefe, Director o Presidente.");
        if(!visibleCompanyIdsForCom().contains(employee.companyId))throw new ForbiddenException("El responsable está fuera del alcance territorial del usuario.");
        return employee;
    }
    static boolean isEligibleResponsibleRole(String roleCode){String role=roleCode==null?"":roleCode.trim().toLowerCase(Locale.ROOT);return role.contains("coordinador")||role.contains("jefe")||role.contains("director")||role.contains("presidente");}
    private Company company(UUID id){Company c=Company.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(c==null)throw new NotFoundException();return c;}

    private Set<UUID> visibleCompanyIdsForCom(){
        if(scope.countryWide())return Company.<Company>list("instanceCountryId=?1",tenant.instanceCountryId()).stream().map(c->c.id).collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<UUID> out=new LinkedHashSet<>();for(UserOperationalScope s:scope.scopes()){if(s.scopeId==null)continue;if("ZONE".equals(s.scopeType)){Company.<Company>list("instanceCountryId=?1 and zoneId=?2",tenant.instanceCountryId(),s.scopeId).forEach(c->out.add(c.id));}else if("REGION".equals(s.scopeType)){CompanyRegion.<CompanyRegion>list("instanceCountryId=?1 and regionId=?2",tenant.instanceCountryId(),s.scopeId).forEach(l->out.add(l.companyId));}else if("COMPANY".equals(s.scopeType))out.add(s.scopeId);}if(identity.getRoles().stream().anyMatch(KAIBIL_ROLES::contains))Company.<Company>list("instanceCountryId=?1 and alwaysActive=true",tenant.instanceCountryId()).forEach(c->out.add(c.id));return out;
    }
    private void requireComCompany(UUID companyId){if(companyId==null||!visibleCompanyIdsForCom().contains(companyId))throw new ForbiddenException("La Compañía está fuera del alcance territorial del usuario.");}
    private void requireTargetZone(UUID zoneId){if(scope.countryWide())return;boolean allowed=scope.scopes().stream().anyMatch(s->"ZONE".equals(s.scopeType)&&Objects.equals(s.scopeId,zoneId));if(!allowed)throw new ForbiddenException("La Zona está fuera del alcance territorial del usuario.");}
    private WebApplicationException conflict(String message){return new WebApplicationException(message,Response.Status.CONFLICT);}
}
