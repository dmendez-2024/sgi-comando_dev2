package com.cajamarca.sgi.comando.territory;

import com.cajamarca.sgi.comando.assignments.EmployeeOperationalSnapshot;
import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.companies.Company;
import com.cajamarca.sgi.comando.core.CoreCatalogService;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.jboss.logging.Logger;

@Path("/api/territory")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
public class TerritoryResource {
    private static final Logger LOG=Logger.getLogger(TerritoryResource.class);
    private static final Set<String> COUNTRY_EDIT_ROLES=Set.of("PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL");
    private static final Set<String> REGION_EDIT_ROLES=Set.of("PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL");
    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject io.quarkus.security.identity.SecurityIdentity identity;
    @Inject ObjectMapper mapper;
    @Inject CoreCatalogService coreCatalog;

    public record ZoneRequest(String code,String name,String status,UUID responsibleEmployeeId,List<String> provinceCodes){}
    public record RegionRequest(UUID zoneId,String code,String name,String status,UUID responsibleEmployeeId,List<String> provinceCodes){}
    public record ZoneDto(UUID id,String code,String name,String status,UUID responsibleEmployeeId,String responsibleName,List<String> provinceCodes){}
    public record RegionDto(UUID id,UUID zoneId,String code,String name,String status,UUID responsibleEmployeeId,String responsibleName,List<String> provinceCodes){}
    public record CompanyDto(UUID id,String code,String name,String status,UUID regionId){}
    public record ProvinceDto(UUID id,String code,String name,UUID zoneId,UUID regionId,String status,UUID coreSubdivisionId,String geometryJson,String coreDatasetVersion){}
    public record ResponsibleDto(UUID employeeId,String fullName,String roleCode){}
    public record TreeResponse(List<ZoneDto> zones,List<RegionDto> regions,List<CompanyDto> companies,List<ProvinceDto> provinces){}

    @GET
    public TreeResponse tree(){ return buildTree(false); }

    @GET @Path("/map")
    public Response map(){
        try{
            coreCatalog.synchronizeTerritoryCatalog();
            return Response.ok(coreCatalog.territoryGeoJson()).build();
        }catch(RuntimeException e){
            LOG.warn("No se pudo actualizar el mapa territorial desde CORE.",e);
            return Response.status(Response.Status.BAD_GATEWAY)
                .entity(Map.of("message","No se pudo obtener la geografía territorial desde CORE."))
                .build();
        }
    }

    @GET @Path("/admin")
    public TreeResponse adminTree(){
        if(identity.getRoles().stream().noneMatch(REGION_EDIT_ROLES::contains)) throw new ForbiddenException("Rol de solo lectura para Territorio.");
        return buildTree(true);
    }

    @GET @Path("/audit")
    public List<TerritoryAuditEvent> audit(@QueryParam("entityType") String entityType,@QueryParam("entityId") UUID entityId){
        if(entityId==null||blank(entityType)) throw new BadRequestException("entityType y entityId son obligatorios.");
        String type=entityType.toUpperCase(Locale.ROOT);
        if("ZONE".equals(type)) requireVisibleZone(zone(entityId).id);
        else if("REGION".equals(type)){ TerritoryRegion r=region(entityId); requireVisibleZone(r.zoneId); }
        else throw new BadRequestException("entityType no soportado para auditoría territorial.");
        return TerritoryAuditEvent.list("instanceCountryId=?1 and entityType=?2 and entityId=?3 order by occurredAt desc",tenant.instanceCountryId(),type,entityId);
    }

    @GET @Path("/responsibles")
    public List<ResponsibleDto> responsibles(){
        List<Company> allCompanies=scope.countryWide()?Company.list("instanceCountryId=?1",tenant.instanceCountryId()):List.of();
        Set<UUID> companyIds=scope.countryWide()?allCompanies.stream().map(c->c.id).collect(Collectors.toSet()):scope.allowedCompanyIds();
        if(companyIds.isEmpty()) return List.of();
        List<EmployeeOperationalSnapshot> people=EmployeeOperationalSnapshot.list(
            "instanceCountryId=?1 and companyId in ?2 and employmentStatus='ACTIVE' " +
                "and (lower(roleCode) like ?3 or lower(roleCode) like ?4 or lower(roleCode) like ?5 or lower(roleCode) like ?6) " +
                "order by fullName",
            tenant.instanceCountryId(),companyIds,"%coordinador%","%jefe%","%director%","%presidente%"
        );
        return people.stream().map(e->new ResponsibleDto(e.employeeId,e.fullName,e.roleCode)).toList();
    }

    @POST @Path("/zones") @Transactional
    public Response createZone(ZoneRequest req){
        requireCountryEditor(); if(req==null||blank(req.code())||blank(req.name())) throw new BadRequestException("Código y nombre son obligatorios.");
        TerritoryZone z=new TerritoryZone();z.instanceCountryId=tenant.instanceCountryId();z.code=req.code().trim().toUpperCase(Locale.ROOT);z.name=req.name().trim();z.status=blank(req.status())?"DRAFT":req.status();z.responsibleEmployeeId=req.responsibleEmployeeId();z.persist();
        applyZoneProvinces(z,req.provinceCodes(),"ACTIVE".equals(z.status)); audit("ZONE",z.id,"CREATED",Map.of("code",z.code,"name",z.name,"status",z.status));
        return Response.status(Response.Status.CREATED).entity(zoneDto(z)).build();
    }

    @PUT @Path("/zones/{id}") @Transactional
    public ZoneDto updateZone(@PathParam("id") UUID id, ZoneRequest req){
        requireCountryEditor(); TerritoryZone z=zone(id); if(req==null) throw new BadRequestException("Datos obligatorios.");
        boolean activating=!"ACTIVE".equals(z.status)&&"ACTIVE".equals(req.status());
        if(!"ACTIVE".equals(z.status)){ if(!blank(req.name()))z.name=req.name().trim(); if(!blank(req.status()))z.status=req.status(); }
        z.responsibleEmployeeId=req.responsibleEmployeeId(); applyZoneProvinces(z,req.provinceCodes(),activating); audit("ZONE",z.id,"UPDATED",Map.of("status",z.status)); return zoneDto(z);
    }

    @DELETE @Path("/zones/{id}") @Transactional
    public Response deleteZone(@PathParam("id") UUID id){
        requireCountryEditor(); TerritoryZone z=zone(id); if("ACTIVE".equals(z.status)) throw new WebApplicationException("Una Zona activa no se puede eliminar; debe conservarse para auditoría.", Response.Status.CONFLICT);
        long regions=TerritoryRegion.count("instanceCountryId=?1 and zoneId=?2",tenant.instanceCountryId(),z.id); if(regions>0) throw new WebApplicationException("Elimine primero las Regiones no activas de esta Zona.", Response.Status.CONFLICT);
        CountrySubdivision.update("draftZoneId=null, draftRegionId=null where instanceCountryId=?1 and draftZoneId=?2",tenant.instanceCountryId(),z.id); audit("ZONE",z.id,"DELETED",Map.of("code",z.code)); z.delete(); return Response.noContent().build();
    }

    @POST @Path("/regions") @Transactional
    public Response createRegion(RegionRequest req){
        requireRegionEditor(); if(req==null||req.zoneId()==null||blank(req.code())||blank(req.name())) throw new BadRequestException("Zona, código y nombre son obligatorios.");
        TerritoryZone z=zone(req.zoneId()); requireVisibleZone(z.id);
        TerritoryRegion r=new TerritoryRegion();r.instanceCountryId=tenant.instanceCountryId();r.zoneId=z.id;r.code=req.code().trim().toUpperCase(Locale.ROOT);r.name=req.name().trim();r.status=blank(req.status())?"DRAFT":req.status();r.responsibleEmployeeId=req.responsibleEmployeeId();r.persist();
        applyRegionProvinces(r,req.provinceCodes(),"ACTIVE".equals(r.status)); audit("REGION",r.id,"CREATED",Map.of("zoneId",z.id,"code",r.code,"name",r.name));
        return Response.status(Response.Status.CREATED).entity(regionDto(r)).build();
    }

    @PUT @Path("/regions/{id}") @Transactional
    public RegionDto updateRegion(@PathParam("id") UUID id, RegionRequest req){
        requireRegionEditor(); TerritoryRegion r=region(id); requireVisibleZone(r.zoneId); if(req==null) throw new BadRequestException("Datos obligatorios.");
        boolean activating=!"ACTIVE".equals(r.status)&&"ACTIVE".equals(req.status());
        if(!"ACTIVE".equals(r.status)){ if(req.zoneId()!=null){TerritoryZone z=zone(req.zoneId());requireVisibleZone(z.id);r.zoneId=z.id;} if(!blank(req.name()))r.name=req.name().trim(); if(!blank(req.status()))r.status=req.status(); }
        r.responsibleEmployeeId=req.responsibleEmployeeId(); applyRegionProvinces(r,req.provinceCodes(),activating); audit("REGION",r.id,"UPDATED",Map.of("status",r.status)); return regionDto(r);
    }

    @DELETE @Path("/regions/{id}") @Transactional
    public Response deleteRegion(@PathParam("id") UUID id){
        requireRegionEditor(); TerritoryRegion r=region(id); requireVisibleZone(r.zoneId); if("ACTIVE".equals(r.status)) throw new WebApplicationException("Una Región activa no se puede eliminar; debe conservarse para auditoría.", Response.Status.CONFLICT);
        long companies=Company.count("instanceCountryId=?1 and regionId=?2",tenant.instanceCountryId(),r.id); if(companies>0) throw new WebApplicationException("La Región tiene Compañías asociadas.", Response.Status.CONFLICT);
        CountrySubdivision.update("draftRegionId=null where instanceCountryId=?1 and draftRegionId=?2",tenant.instanceCountryId(),r.id); audit("REGION",r.id,"DELETED",Map.of("code",r.code)); r.delete(); return Response.noContent().build();
    }

    @PUT @Path("/companies/{companyId}/region/{regionId}") @Transactional
    public CompanyDto assignCompany(@PathParam("companyId") UUID companyId,@PathParam("regionId") UUID regionId){
        requireRegionEditor(); scope.requireCompany(companyId);
        Company c=Company.find("id=?1 and instanceCountryId=?2",companyId,tenant.instanceCountryId()).firstResult(); if(c==null) throw new NotFoundException("Compañía no encontrada");
        TerritoryRegion r=region(regionId); requireVisibleZone(r.zoneId); c.regionId=r.id; audit("COMPANY",c.id,"REGION_CHANGED",Map.of("regionId",r.id)); return new CompanyDto(c.id,c.code,c.name,c.status,c.regionId);
    }

    private TreeResponse buildTree(boolean admin){
        List<TerritoryZone> zones; List<TerritoryRegion> regions; List<Company> companies; List<CountrySubdivision> provinces;
        if(admin&&scope.countryWide()){
            zones=TerritoryZone.list("instanceCountryId=?1 order by code",tenant.instanceCountryId());
            regions=TerritoryRegion.list("instanceCountryId=?1 order by code",tenant.instanceCountryId());
            companies=Company.list("instanceCountryId=?1 order by name",tenant.instanceCountryId());
            provinces=CountrySubdivision.list("instanceCountryId=?1 order by name",tenant.instanceCountryId());
        }else{
            Set<UUID> allowedCompanies=scope.allowedCompanyIds(); companies=allowedCompanies.isEmpty()?List.of():Company.list("instanceCountryId=?1 and id in ?2 order by name",tenant.instanceCountryId(),allowedCompanies);
            Set<UUID> regionIds=scope.visibleRegionIds(); regions=regionIds.isEmpty()?List.of():TerritoryRegion.list("instanceCountryId=?1 and id in ?2 order by code",tenant.instanceCountryId(),regionIds);
            Set<UUID> zoneIds=scope.visibleZoneIds(); zones=zoneIds.isEmpty()?List.of():TerritoryZone.list("instanceCountryId=?1 and id in ?2 order by code",tenant.instanceCountryId(),zoneIds);
            provinces=zoneIds.isEmpty()?List.of():CountrySubdivision.list("instanceCountryId=?1 and zoneId in ?2 order by name",tenant.instanceCountryId(),zoneIds);
        }
        return new TreeResponse(zones.stream().map(this::zoneDto).toList(),regions.stream().map(this::regionDto).toList(),companies.stream().map(c->new CompanyDto(c.id,c.code,c.name,c.status,c.regionId)).toList(),provinces.stream().map(p->new ProvinceDto(p.id,p.code,p.name,p.zoneId,p.regionId,p.status,p.coreSubdivisionId,p.geometryJson,p.coreDatasetVersion)).toList());
    }

    private ZoneDto zoneDto(TerritoryZone z){ return new ZoneDto(z.id,z.code,z.name,z.status,z.responsibleEmployeeId,responsibleName(z.responsibleEmployeeId),provinceCodes(z)); }
    private RegionDto regionDto(TerritoryRegion r){ return new RegionDto(r.id,r.zoneId,r.code,r.name,r.status,r.responsibleEmployeeId,responsibleName(r.responsibleEmployeeId),provinceCodes(r)); }
    private String responsibleName(UUID id){ if(id==null)return null; EmployeeOperationalSnapshot e=EmployeeOperationalSnapshot.find("instanceCountryId=?1 and employeeId=?2",tenant.instanceCountryId(),id).firstResult();return e==null?null:e.fullName; }
    private List<String> provinceCodes(TerritoryZone z){ String field="ACTIVE".equals(z.status)?"zoneId":"draftZoneId"; return CountrySubdivision.<CountrySubdivision>list("instanceCountryId=?1 and "+field+"=?2 order by name",tenant.instanceCountryId(),z.id).stream().map(p->p.code).toList(); }
    private List<String> provinceCodes(TerritoryRegion r){ TerritoryZone z=zone(r.zoneId); boolean effective="ACTIVE".equals(r.status)&&"ACTIVE".equals(z.status); String field=effective?"regionId":"draftRegionId"; return CountrySubdivision.<CountrySubdivision>list("instanceCountryId=?1 and "+field+"=?2 order by name",tenant.instanceCountryId(),r.id).stream().map(p->p.code).toList(); }
    private void applyZoneProvinces(TerritoryZone z,List<String> codes,boolean activating){
        if(codes==null)return;
        if("ACTIVE".equals(z.status)){
            List<CountrySubdivision> current=CountrySubdivision.list("instanceCountryId=?1 and zoneId=?2",tenant.instanceCountryId(),z.id);
            for(CountrySubdivision p:current)if(!codes.contains(p.code)){p.zoneId=null;p.regionId=null;}
            for(String code:codes){
                CountrySubdivision p=subdivision(code);if(p==null)continue;p.zoneId=z.id;
                if(p.regionId!=null){TerritoryRegion currentRegion=TerritoryRegion.find("id=?1",p.regionId).firstResult();if(currentRegion==null||!Objects.equals(currentRegion.zoneId,z.id))p.regionId=null;}
                if(activating&&p.draftRegionId!=null){TerritoryRegion pendingRegion=TerritoryRegion.find("id=?1",p.draftRegionId).firstResult();if(pendingRegion!=null&&"ACTIVE".equals(pendingRegion.status)&&Objects.equals(pendingRegion.zoneId,z.id)){p.regionId=pendingRegion.id;p.draftRegionId=null;}}
                if(activating&&Objects.equals(p.draftZoneId,z.id))p.draftZoneId=null;
            }
            if(activating)for(CountrySubdivision p:CountrySubdivision.<CountrySubdivision>list("instanceCountryId=?1 and draftZoneId=?2",tenant.instanceCountryId(),z.id)){p.draftZoneId=null;p.draftRegionId=null;}
            return;
        }
        List<CountrySubdivision> current=CountrySubdivision.list("instanceCountryId=?1 and draftZoneId=?2",tenant.instanceCountryId(),z.id);
        for(CountrySubdivision p:current)if(!codes.contains(p.code)){p.draftZoneId=null;p.draftRegionId=null;}
        for(String code:codes){CountrySubdivision p=subdivision(code);if(p!=null){p.draftZoneId=z.id;if(p.draftRegionId!=null){TerritoryRegion pendingRegion=TerritoryRegion.find("id=?1",p.draftRegionId).firstResult();if(pendingRegion==null||!Objects.equals(pendingRegion.zoneId,z.id))p.draftRegionId=null;}}}
    }
    private void applyRegionProvinces(TerritoryRegion r,List<String> codes,boolean activating){
        if(codes==null)return;
        TerritoryZone z=zone(r.zoneId);boolean effective="ACTIVE".equals(r.status)&&"ACTIVE".equals(z.status);
        if(effective){
            List<CountrySubdivision> current=CountrySubdivision.list("instanceCountryId=?1 and regionId=?2",tenant.instanceCountryId(),r.id);
            for(CountrySubdivision p:current)if(!codes.contains(p.code))p.regionId=null;
            for(String code:codes){CountrySubdivision p=subdivision(code);if(p==null)continue;if(!Objects.equals(p.zoneId,r.zoneId))throw new BadRequestException("La provincia "+p.name+" no pertenece a la Zona de esta Región.");p.regionId=r.id;if(activating&&Objects.equals(p.draftRegionId,r.id))p.draftRegionId=null;}
            if(activating)for(CountrySubdivision p:CountrySubdivision.<CountrySubdivision>list("instanceCountryId=?1 and draftRegionId=?2",tenant.instanceCountryId(),r.id))p.draftRegionId=null;
            return;
        }
        List<CountrySubdivision> current=CountrySubdivision.list("instanceCountryId=?1 and draftRegionId=?2",tenant.instanceCountryId(),r.id);
        for(CountrySubdivision p:current)if(!codes.contains(p.code))p.draftRegionId=null;
        for(String code:codes){CountrySubdivision p=subdivision(code);if(p==null)continue;UUID configuredZoneId="ACTIVE".equals(z.status)?p.zoneId:p.draftZoneId;if(!Objects.equals(configuredZoneId,r.zoneId))throw new BadRequestException("La provincia "+p.name+" no pertenece a la Zona de esta Región.");p.draftRegionId=r.id;}
    }
    private CountrySubdivision subdivision(String code){return CountrySubdivision.find("instanceCountryId=?1 and code=?2",tenant.instanceCountryId(),code).firstResult();}
    private TerritoryZone zone(UUID id){TerritoryZone z=TerritoryZone.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(z==null)throw new NotFoundException("Zona no encontrada");return z;}
    private TerritoryRegion region(UUID id){TerritoryRegion r=TerritoryRegion.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(r==null)throw new NotFoundException("Región no encontrada");return r;}
    private void requireVisibleZone(UUID zoneId){ if(!scope.countryWide()&&!scope.visibleZoneIds().contains(zoneId)) throw new ForbiddenException("La Zona está fuera del alcance territorial del usuario."); }
    private void requireCountryEditor(){ if(identity.getRoles().stream().noneMatch(COUNTRY_EDIT_ROLES::contains)) throw new ForbiddenException("Solo un rol de alcance país puede configurar Zonas."); }
    private void requireRegionEditor(){ if(identity.getRoles().stream().noneMatch(REGION_EDIT_ROLES::contains)) throw new ForbiddenException("Rol de solo lectura para Territorio."); }
    private void audit(String entityType,UUID entityId,String eventType,Object payload){ try{TerritoryAuditEvent e=new TerritoryAuditEvent();e.instanceCountryId=tenant.instanceCountryId();e.entityType=entityType;e.entityId=entityId;e.eventType=eventType;e.actorUsername=identity.getPrincipal().getName();e.payloadJson=mapper.writeValueAsString(payload);e.occurredAt=Instant.now();e.persist();}catch(Exception ex){throw new InternalServerErrorException("No se pudo registrar auditoría territorial",ex);} }
    private static boolean blank(String s){ return s==null||s.isBlank(); }
}
