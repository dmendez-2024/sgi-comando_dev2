package com.cajamarca.sgi.comando.coordination;

import com.cajamarca.sgi.comando.assignments.PostShiftTemplate;
import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.companies.Company;
import com.cajamarca.sgi.comando.core.CoreCatalogService;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.operations.PostEntity;
import com.cajamarca.sgi.comando.operations.ServiceEntity;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Path("/api/coordination")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
public class CoordinationResource {
    private static final Set<String> TYPES=Set.of("MONITORING","SUPERVISION");
    private static final Set<String> FORMATS=Set.of("24/7","12/7","12/5");
    private static final Set<String> POST_STATES=Set.of("DRAFT","INACTIVE","ACTIVE");
    private static final Set<String> EXECUTIVE_ROLES=Set.of("PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL");
    private static final Set<String> COMPANY_ROLES=Set.of("COORDINADOR_COMPANIA","ASISTENTE_COORDINACION");

    @Inject TenantContext tenant;
    @Inject CoreCatalogService coreCatalog;
    @Inject EntityManager entityManager;
    @Inject OperationalScopeService scope;
    @Inject SecurityIdentity identity;

    public record CompanyDto(UUID id,String code,String name,String companyType,boolean kaibil){}
    public record ShiftDto(String code,String label,String start,String end){}
    public record RouteSummaryDto(UUID id,String code,String name,int versionNo,String status,int pointCount){}
    public record CoordinationPostDto(UUID id,UUID companyId,String companyName,String code,String name,String postType,String format,String rotation,String shiftStartTime,int dayMask,String status,List<ShiftDto> shifts,RouteSummaryDto route,String updatedBy){}
    public record CreatePostRequest(UUID companyId,String postType,String name,String format,String shiftStartTime,Integer dayMask){}
    public record SavePostRequest(String name,String format,String shiftStartTime,Integer dayMask,String status){}

    public record OperationalPostDto(UUID id,String code,String name,String format,List<ShiftDto> shifts){}
    public record PointDto(UUID id,String code,String name,String clientName,UUID companyId,String companyName,String status,List<OperationalPostDto> posts,boolean coveredByActiveRoute,boolean newOrUncovered){}
    public record RoutePointDto(UUID id,UUID pointId,int sortOrder,String pointCodeSnapshot,String pointNameSnapshot,boolean currentlyOperational){}
    public record RouteDto(UUID id,UUID seriesId,UUID basedOnRouteId,UUID coordinationPostId,String code,String name,int versionNo,String status,String publishedAt,String updatedBy,List<RoutePointDto> points){}
    public record SaveRouteRequest(String name,List<UUID> pointIds){}
    public record CoverageDto(int totalOperationalPoints,int coveredPoints,int uncoveredPoints,List<PointDto> uncovered){}
    public record PreviewPostDto(UUID postId,String code,String name,String format){}
    public record PreviewPointDto(UUID pointId,String code,String name,int activePostCount,int totalPostCount,List<PreviewPostDto> activePosts,boolean included){}
    public record PreviewShiftDto(String code,String label,String start,String end,List<PreviewPointDto> points){}
    public record RoutePreviewDto(UUID routeId,String routeCode,int versionNo,List<PreviewShiftDto> shifts){}

    @GET
    @Path("/companies")
    public List<CompanyDto> companies(){
        if(hasAny(COMPANY_ROLES)){
            Set<UUID> allowed=scope.allowedCompanyIds();
            if(allowed.isEmpty())return List.of();
            List<Company> rows=Company.list("instanceCountryId=?1 and id in ?2 and status='ACTIVE' order by name",tenant.instanceCountryId(),allowed);
            return rows.stream().filter(c->!isKaibil(c)).map(this::companyDto).toList();
        }
        if(hasAny(EXECUTIVE_ROLES)){
            Company k=kaibil();
            return k==null?List.of():List.of(companyDto(k));
        }
        return List.of();
    }

    @GET
    @Path("/posts")
    public List<CoordinationPostDto> posts(@QueryParam("companyId") UUID companyId){
        Company c=manageableCompany(companyId);
        return CoordinationPost.<CoordinationPost>list("instanceCountryId=?1 and companyId=?2 order by code",tenant.instanceCountryId(),c.id).stream().map(p->postDto(p,c)).toList();
    }

    @POST
    @Path("/posts")
    @Transactional
    public CoordinationPostDto createPost(CreatePostRequest req){
        if(req==null||req.companyId()==null)throw new BadRequestException("Compañía obligatoria.");
        Company c=manageableCompany(req.companyId());
        String type=norm(req.postType(),TYPES,null,"Tipo de Puesto inválido.");
        String format=norm(req.format(),FORMATS,"24/7","Formato inválido.");
        int mask=normalizeDayMask(format,req.dayMask());
        CoordinationPost p=new CoordinationPost();
        p.instanceCountryId=tenant.instanceCountryId();p.companyId=c.id;p.postType=type;p.code=nextPostCode(c,type);p.name=clean(req.name(),"MONITORING".equals(type)?"Nuevo Monitoreo":"Nueva Supervisión");p.format=format;p.rotation=rotationFor(format);p.shiftStartTime=parseTime(req.shiftStartTime(),LocalTime.of(6,0));p.dayMask=mask;p.status="DRAFT";p.updatedByUsername=scope.username();p.persist();
        return postDto(p,c);
    }

    @PUT
    @Path("/posts/{id}")
    @Transactional
    public CoordinationPostDto savePost(@PathParam("id")UUID id,SavePostRequest req){
        CoordinationPost p=post(id);Company c=manageableCompany(p.companyId);if(req==null)throw new BadRequestException("Solicitud obligatoria.");
        p.name=clean(req.name(),p.name);String format=norm(req.format(),FORMATS,p.format,"Formato inválido.");p.format=format;p.rotation=rotationFor(format);p.shiftStartTime=parseTime(req.shiftStartTime(),p.shiftStartTime);p.dayMask=normalizeDayMask(format,req.dayMask());p.status=norm(req.status(),POST_STATES,p.status,"Estado inválido.");p.updatedByUsername=scope.username();
        return postDto(p,c);
    }

    @GET
    @Path("/points")
    public List<PointDto> points(@QueryParam("companyId")UUID companyId){
        Company c=manageableCompany(companyId);
        Set<UUID> covered=coveredPointIds(c.id);
        return operationalPoints(c).stream().map(p->pointDto(p,covered.contains(p.id))).toList();
    }

    @GET
    @Path("/coverage")
    public CoverageDto coverage(@QueryParam("companyId")UUID companyId){
        Company c=manageableCompany(companyId);List<PointEntity> points=operationalPoints(c);Set<UUID> covered=coveredPointIds(c.id);
        List<PointDto> uncovered=points.stream().filter(p->!covered.contains(p.id)).map(p->pointDto(p,false)).toList();
        return new CoverageDto(points.size(),points.size()-uncovered.size(),uncovered.size(),uncovered);
    }

    @GET
    @Path("/posts/{postId}/route")
    public RouteDto routeForPost(@PathParam("postId")UUID postId){
        CoordinationPost p=post(postId);manageableCompany(p.companyId);requireSupervisor(p);
        SupervisionRoute route=currentRoute(p.id);return route==null?null:routeDto(route);
    }

    @POST
    @Path("/posts/{postId}/route")
    @Transactional
    public RouteDto createRoute(@PathParam("postId")UUID postId,SaveRouteRequest req){
        CoordinationPost p=post(postId);manageableCompany(p.companyId);requireSupervisor(p);
        SupervisionRoute existing=currentRoute(p.id);if(existing!=null)return routeDto(existing);
        SupervisionRoute r=new SupervisionRoute();r.id=UUID.randomUUID();r.instanceCountryId=tenant.instanceCountryId();r.seriesId=r.id;r.coordinationPostId=p.id;r.code="RUT-"+p.code;r.name=clean(req==null?null:req.name(),"Ruta "+p.name);r.versionNo=1;r.status="DRAFT";r.updatedByUsername=scope.username();r.persist();
        if(req!=null&&req.pointIds()!=null)saveRoutePoints(r,p,req.pointIds());
        return routeDto(r);
    }

    @PUT
    @Path("/routes/{id}")
    @Transactional
    public RouteDto saveRoute(@PathParam("id")UUID id,SaveRouteRequest req){
        SupervisionRoute r=route(id);CoordinationPost p=post(r.coordinationPostId);manageableCompany(p.companyId);requireDraftRoute(r);if(req==null)throw new BadRequestException("Solicitud obligatoria.");
        r.name=clean(req.name(),r.name);r.updatedByUsername=scope.username();saveRoutePoints(r,p,req.pointIds()==null?List.of():req.pointIds());return routeDto(r);
    }

    @POST
    @Path("/routes/{id}/fork")
    @Transactional
    public RouteDto forkRoute(@PathParam("id")UUID id){
        SupervisionRoute source=route(id);CoordinationPost p=post(source.coordinationPostId);manageableCompany(p.companyId);
        SupervisionRoute existing=SupervisionRoute.find("instanceCountryId=?1 and seriesId=?2 and status='DRAFT'",tenant.instanceCountryId(),source.seriesId).firstResult();if(existing!=null)return routeDto(existing);
        SupervisionRoute target=new SupervisionRoute();target.instanceCountryId=tenant.instanceCountryId();target.seriesId=source.seriesId;target.basedOnRouteId=source.id;target.coordinationPostId=source.coordinationPostId;target.code=source.code;target.name=source.name;target.versionNo=source.versionNo+1;target.status="DRAFT";target.updatedByUsername=scope.username();target.persist();
        List<SupervisionRoutePoint> src=routePoints(source.id);for(SupervisionRoutePoint old:src){SupervisionRoutePoint rp=new SupervisionRoutePoint();rp.instanceCountryId=tenant.instanceCountryId();rp.routeId=target.id;rp.pointId=old.pointId;rp.sortOrder=old.sortOrder;rp.pointCodeSnapshot=old.pointCodeSnapshot;rp.pointNameSnapshot=old.pointNameSnapshot;rp.persist();}
        return routeDto(target);
    }

    @POST
    @Path("/routes/{id}/publish")
    @Transactional
    public RouteDto publishRoute(@PathParam("id")UUID id){
        SupervisionRoute r=route(id);CoordinationPost p=post(r.coordinationPostId);manageableCompany(p.companyId);requireDraftRoute(r);if(routePoints(r.id).isEmpty())throw new BadRequestException("La Ruta debe contener al menos un Punto.");
        List<SupervisionRoute> actives=SupervisionRoute.list("instanceCountryId=?1 and coordinationPostId=?2 and status='ACTIVE'",tenant.instanceCountryId(),p.id);for(SupervisionRoute active:actives)active.status="REPLACED";
        r.status="ACTIVE";r.publishedAt=Instant.now();r.updatedByUsername=scope.username();return routeDto(r);
    }

    @GET
    @Path("/routes/{id}/history")
    public List<RouteDto> routeHistory(@PathParam("id")UUID id){SupervisionRoute r=route(id);CoordinationPost p=post(r.coordinationPostId);manageableCompany(p.companyId);return SupervisionRoute.<SupervisionRoute>list("instanceCountryId=?1 and seriesId=?2 order by versionNo desc",tenant.instanceCountryId(),r.seriesId).stream().map(this::routeDto).toList();}

    @GET
    @Path("/routes/{id}/preview")
    public RoutePreviewDto preview(@PathParam("id")UUID id){
        SupervisionRoute r=route(id);CoordinationPost cp=post(r.coordinationPostId);manageableCompany(cp.companyId);List<SupervisionRoutePoint> rps=routePoints(r.id);
        List<TargetShift> targets=targetShifts(cp);List<PreviewShiftDto> shifts=new ArrayList<>();
        Company routeCompany=Company.find("id=?1 and instanceCountryId=?2",cp.companyId,tenant.instanceCountryId()).firstResult();Set<UUID> operational=routeCompany==null?Set.of():operationalPoints(routeCompany).stream().map(p->p.id).collect(Collectors.toSet());
        for(TargetShift target:targets){List<PreviewPointDto> points=new ArrayList<>();for(SupervisionRoutePoint rp:rps){PointEntity point=PointEntity.find("id=?1 and instanceCountryId=?2",rp.pointId,tenant.instanceCountryId()).firstResult();if(point==null||!operational.contains(rp.pointId)){points.add(new PreviewPointDto(rp.pointId,rp.pointCodeSnapshot,rp.pointNameSnapshot,0,0,List.of(),false));continue;}List<PostEntity> posts=PostEntity.list("instanceCountryId=?1 and pointId=?2 order by code",tenant.instanceCountryId(),point.id);List<PreviewPostDto> active=new ArrayList<>();for(PostEntity post:posts){if(postActiveInTarget(post,target,cp.dayMask))active.add(new PreviewPostDto(post.id,post.code,post.name,post.format));}points.add(new PreviewPointDto(point.id,point.code,point.name,active.size(),posts.size(),active,!active.isEmpty()));}shifts.add(new PreviewShiftDto(target.code,target.label,fmt(target.start),fmt(target.end),points));}
        return new RoutePreviewDto(r.id,r.code,r.versionNo,shifts);
    }

    private CoordinationPostDto postDto(CoordinationPost p,Company c){SupervisionRoute route="SUPERVISION".equals(p.postType)?currentRoute(p.id):null;RouteSummaryDto rs=route==null?null:new RouteSummaryDto(route.id,route.code,route.name,route.versionNo,route.status,routePoints(route.id).size());return new CoordinationPostDto(p.id,p.companyId,c.name,p.code,p.name,p.postType,p.format,p.rotation,fmt(p.shiftStartTime),p.dayMask,p.status,shiftDtos(p),rs,p.updatedByUsername);}
    private RouteDto routeDto(SupervisionRoute r){
        CoordinationPost cp=post(r.coordinationPostId);Company company=Company.find("id=?1 and instanceCountryId=?2",cp.companyId,tenant.instanceCountryId()).firstResult();Set<UUID> operational=company==null?Set.of():operationalPoints(company).stream().map(p->p.id).collect(Collectors.toSet());
        List<RoutePointDto> points=routePoints(r.id).stream().map(rp->new RoutePointDto(rp.id,rp.pointId,rp.sortOrder,rp.pointCodeSnapshot,rp.pointNameSnapshot,operational.contains(rp.pointId))).toList();
        return new RouteDto(r.id,r.seriesId,r.basedOnRouteId,r.coordinationPostId,r.code,r.name,r.versionNo,r.status,r.publishedAt==null?null:r.publishedAt.toString(),r.updatedByUsername,points);
    }
    private CompanyDto companyDto(Company c){return new CompanyDto(c.id,c.code,c.name,c.companyType,isKaibil(c));}
    private PointDto pointDto(PointEntity point,boolean covered){Company c=point.companyId==null?null:Company.find("id=?1 and instanceCountryId=?2",point.companyId,tenant.instanceCountryId()).firstResult();List<PostEntity> posts=PostEntity.list("instanceCountryId=?1 and pointId=?2 order by code",tenant.instanceCountryId(),point.id);List<OperationalPostDto> pd=posts.stream().map(p->new OperationalPostDto(p.id,p.code,p.name,p.format,postShiftDtos(p.id))).toList();return new PointDto(point.id,point.code,point.name,point.clientName,point.companyId,c==null?"—":c.name,point.status,pd,covered,!covered);}

    private void saveRoutePoints(SupervisionRoute r,CoordinationPost cp,List<UUID> pointIds){List<UUID> unique=new ArrayList<>(new LinkedHashSet<>(pointIds));Map<UUID,PointEntity> allowed=operationalPoints(manageableCompany(cp.companyId)).stream().collect(Collectors.toMap(p->p.id,Function.identity()));for(UUID id:unique)if(!allowed.containsKey(id))throw new BadRequestException("La Ruta contiene un Punto fuera de la Compañía/ámbito permitido.");SupervisionRoutePoint.delete("instanceCountryId=?1 and routeId=?2",tenant.instanceCountryId(),r.id);int order=1;for(UUID id:unique){PointEntity point=allowed.get(id);SupervisionRoutePoint rp=new SupervisionRoutePoint();rp.instanceCountryId=tenant.instanceCountryId();rp.routeId=r.id;rp.pointId=id;rp.sortOrder=order++;rp.pointCodeSnapshot=point.code;rp.pointNameSnapshot=point.name;rp.persist();}}
    private Set<UUID> coveredPointIds(UUID companyId){List<CoordinationPost> supervisors=CoordinationPost.list("instanceCountryId=?1 and companyId=?2 and postType='SUPERVISION' and status='ACTIVE'",tenant.instanceCountryId(),companyId);Set<UUID> ids=new HashSet<>();for(CoordinationPost p:supervisors){SupervisionRoute r=SupervisionRoute.find("instanceCountryId=?1 and coordinationPostId=?2 and status='ACTIVE'",tenant.instanceCountryId(),p.id).firstResult();if(r!=null)routePoints(r.id).forEach(rp->ids.add(rp.pointId));}return ids;}
    private List<PointEntity> operationalPoints(Company c){
        List<PointEntity> rows;
        if(isKaibil(c)){
            Set<UUID> companies=scope.allowedCompanyIds();
            if(scope.countryWide())companies=Company.<Company>list("instanceCountryId=?1 and status='ACTIVE'",tenant.instanceCountryId()).stream().filter(x->!isKaibil(x)).map(x->x.id).collect(Collectors.toSet());
            if(companies.isEmpty())return List.of();
            rows=PointEntity.list("instanceCountryId=?1 and companyId in ?2 and status='ACTIVE' and operationalAssignmentStatus='ASSIGNED' order by name",tenant.instanceCountryId(),companies);
        }else rows=PointEntity.list("instanceCountryId=?1 and companyId=?2 and status='ACTIVE' and operationalAssignmentStatus='ASSIGNED' order by name",tenant.instanceCountryId(),c.id);
        return rows.stream().filter(p->serviceActive(p.serviceId)).toList();
    }
    private boolean serviceActive(UUID serviceId){ServiceEntity s=ServiceEntity.find("id=?1 and instanceCountryId=?2",serviceId,tenant.instanceCountryId()).firstResult();return s!=null&&"ACTIVE".equalsIgnoreCase(s.commercialStatus);}

    private boolean postActiveInTarget(PostEntity post,TargetShift target,int coordinationDayMask){List<PostShiftTemplate> templates=PostShiftTemplate.list("instanceCountryId=?1 and postId=?2 and active=true",tenant.instanceCountryId(),post.id);for(PostShiftTemplate t:templates){if((t.dayMask&coordinationDayMask)==0)continue;if(overlaps(target.start,target.end,t.startTime,t.endTime))return true;}return false;}
    private boolean overlaps(LocalTime aStart,LocalTime aEnd,LocalTime bStart,LocalTime bEnd){for(int[] a:segments(aStart,aEnd))for(int[] b:segments(bStart,bEnd))if(a[0]<b[1]&&b[0]<a[1])return true;return false;}
    private List<int[]> segments(LocalTime start,LocalTime end){int s=start.getHour()*60+start.getMinute(),e=end.getHour()*60+end.getMinute();if(e>s)return List.of(new int[]{s,e});return List.of(new int[]{s,1440},new int[]{0,e});}

    private List<ShiftDto> shiftDtos(CoordinationPost p){return targetShifts(p).stream().map(s->new ShiftDto(s.code,s.label,fmt(s.start),fmt(s.end))).toList();}
    private List<ShiftDto> postShiftDtos(UUID postId){List<PostShiftTemplate> rows=PostShiftTemplate.list("instanceCountryId=?1 and postId=?2 and active=true order by startTime",tenant.instanceCountryId(),postId);return rows.stream().map(t->new ShiftDto(t.shiftCode,t.shiftName,fmt(t.startTime),fmt(t.endTime))).toList();}
    private record TargetShift(String code,String label,LocalTime start,LocalTime end){}
    private List<TargetShift> targetShifts(CoordinationPost p){LocalTime start=p.shiftStartTime;LocalTime end=start.plusHours(12);if("24/7".equals(p.format))return List.of(new TargetShift("T1","Turno 1 (Día)",start,end),new TargetShift("T2","Turno 2 (Noche)",end,start));return List.of(new TargetShift("T1","Turno operativo",start,end));}

    private Company manageableCompany(UUID id){if(id==null)throw new BadRequestException("Compañía obligatoria.");Company c=Company.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(c==null)throw new NotFoundException("Compañía no encontrada.");if(hasAny(COMPANY_ROLES)){scope.requireCompany(c.id);if(isKaibil(c))throw new ForbiddenException("Coordinación de Compañía no administra Kaibil.");return c;}if(hasAny(EXECUTIVE_ROLES)){if(!isKaibil(c))throw new ForbiddenException("Este perfil crea Puestos de Coordinación únicamente dentro de Kaibil.");return c;}throw new ForbiddenException("Perfil sin permisos para administrar Coordinación.");}
    private Company kaibil(){return Company.find("instanceCountryId=?1 and (alwaysActive=true or upper(companyType)='COORDINATION')",tenant.instanceCountryId()).firstResult();}
    private boolean isKaibil(Company c){return c!=null&&(c.alwaysActive||"COORDINATION".equalsIgnoreCase(c.companyType));}
    private boolean hasAny(Set<String> roles){for(String r:identity.getRoles())if(roles.contains(r))return true;return false;}
    private CoordinationPost post(UUID id){CoordinationPost p=CoordinationPost.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(p==null)throw new NotFoundException("Puesto de Coordinación no encontrado.");return p;}
    private SupervisionRoute route(UUID id){SupervisionRoute r=SupervisionRoute.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(r==null)throw new NotFoundException("Ruta de Supervisión no encontrada.");return r;}
    private SupervisionRoute currentRoute(UUID postId){List<SupervisionRoute> rows=SupervisionRoute.list("instanceCountryId=?1 and coordinationPostId=?2 order by versionNo desc",tenant.instanceCountryId(),postId);for(SupervisionRoute r:rows)if("DRAFT".equals(r.status))return r;for(SupervisionRoute r:rows)if("ACTIVE".equals(r.status))return r;return rows.isEmpty()?null:rows.get(0);}
    private List<SupervisionRoutePoint> routePoints(UUID routeId){return SupervisionRoutePoint.list("instanceCountryId=?1 and routeId=?2 order by sortOrder",tenant.instanceCountryId(),routeId);}
    private void requireSupervisor(CoordinationPost p){if(!"SUPERVISION".equals(p.postType))throw new BadRequestException("Solo los Puestos de Supervisión tienen Ruta.");}
    private void requireDraftRoute(SupervisionRoute r){if(!"DRAFT".equals(r.status))throw new ClientErrorException("La Ruta publicada es inmutable. Cree una nueva versión.",409);}
    private String nextPostCode(Company company,String type){
        String countryIsoAlpha3=coreCatalog.resolveCountry().isoAlpha3();
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:key,0))")
            .setParameter("key","coordination-post:"+tenant.instanceCountryId()+":"+company.id+":"+type).getSingleResult();
        List<CoordinationPost> rows=CoordinationPost.list("instanceCountryId=?1 and companyId=?2 and postType=?3",tenant.instanceCountryId(),company.id,type);
        return formatPostCode(countryIsoAlpha3,company.code,type,rows.stream().map(p->p.code).toList());
    }

    static String formatPostCode(String countryIsoAlpha3,String companyCode,String type,Collection<String> existingCodes){
        if(countryIsoAlpha3==null||!countryIsoAlpha3.matches("(?i)[a-z]{3}"))throw new IllegalStateException("CORE no devolvió countryIsoAlpha3 válido.");
        if(companyCode==null||companyCode.length()<3||!companyCode.substring(0,3).matches("(?i)[a-z0-9]{3}"))throw new IllegalStateException("La Compañía no tiene un código válido para el Puesto.");
        String prefix="MONITORING".equals(type)?"MON":"SUP";
        int max=0;
        for(String code:existingCodes){
            if(code==null)continue;
            String[] parts=code.split("-");
            try{max=Math.max(max,Integer.parseInt(parts[parts.length-1]));}catch(NumberFormatException ignored){}
        }
        return countryIsoAlpha3.toUpperCase(Locale.ROOT)+"-"+companyCode.substring(0,3).toUpperCase(Locale.ROOT)+"-"+prefix+"-"+String.format(Locale.ROOT,"%03d",max+1);
    }
    private String rotationFor(String format){return "12/5".equals(format)?"5-2":"6-2";}
    private int normalizeDayMask(String format,Integer value){if("12/5".equals(format)){int mask=value==null?31:value;if(Integer.bitCount(mask)!=5||mask<1||mask>127)throw new BadRequestException("12/5 requiere seleccionar exactamente 5 días.");return mask;}return 127;}
    private String norm(String value,Set<String> allowed,String fallback,String message){String v=value==null||value.isBlank()?fallback:value.trim().toUpperCase(Locale.ROOT);if(v==null||!allowed.contains(v))throw new BadRequestException(message);return v;}
    private String clean(String value,String fallback){return value==null||value.isBlank()?fallback:value.trim();}
    private LocalTime parseTime(String value,LocalTime fallback){if(value==null||value.isBlank())return fallback;try{return LocalTime.parse(value);}catch(Exception e){throw new BadRequestException("Hora inválida. Use HH:mm.");}}
    private String fmt(LocalTime value){return value==null?null:String.format(Locale.ROOT,"%02d:%02d",value.getHour(),value.getMinute());}
}
