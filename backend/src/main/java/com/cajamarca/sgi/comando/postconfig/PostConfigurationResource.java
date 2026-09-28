package com.cajamarca.sgi.comando.postconfig;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.ats.AtsPointPackage;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.operations.PostEntity;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.*;

@Path("/api/post-configurations")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PostConfigurationResource {
    private static final Set<String> POST_TYPES = Set.of("CAA","PAT","VIG","MIX");

    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject SecurityIdentity identity;

    public record SkillSet(int attendance,int accessControl,int patrol,int judgement,int tactical,int bearing,int leadership,int customerService) {}
    public record ConfigDto(UUID postId,String postType,String description,String atsLocationKey,String atsLocationLabel,UUID atsPackageId,Double atsLocationX,Double atsLocationY,SkillSet skills,String adjustmentJustification,String configStatus,String updatedBy) {}
    public record SaveRequest(String postType,String description,String atsLocationKey,String atsLocationLabel,UUID atsPackageId,Double atsLocationX,Double atsLocationY,SkillSet skills,String adjustmentJustification,String configStatus) {}

    @GET
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
    public List<ConfigDto> list(@QueryParam("pointId") UUID pointId){
        PointEntity point=point(pointId);
        scope.requireCompany(point.companyId);
        List<PostEntity> posts=PostEntity.list("pointId=?1 and instanceCountryId=?2 order by code",pointId,tenant.instanceCountryId());
        if(posts.isEmpty()) return List.of();
        Set<UUID> ids=new HashSet<>(); for(PostEntity p:posts) ids.add(p.id);
        List<PostOperationalConfig> configs=PostOperationalConfig.list("instanceCountryId=?1 and postId in ?2",tenant.instanceCountryId(),ids);
        Map<UUID,PostOperationalConfig> byPost=new HashMap<>(); for(PostOperationalConfig c:configs) byPost.put(c.postId,c);
        List<ConfigDto> out=new ArrayList<>();
        for(PostEntity post:posts){
            PostOperationalConfig c=byPost.get(post.id);
            out.add(c==null?defaults(post):dto(c));
        }
        return out;
    }

    @PUT
    @Path("/{postId}")
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public ConfigDto save(@PathParam("postId") UUID postId, SaveRequest request){
        if(request==null) throw new BadRequestException("Solicitud obligatoria");
        PostEntity post=PostEntity.find("id=?1 and instanceCountryId=?2",postId,tenant.instanceCountryId()).firstResult();
        if(post==null) throw new NotFoundException("Puesto no encontrado");
        PointEntity point=point(post.pointId);
        scope.requireCompany(point.companyId);
        try{
            validate(request,point);
        }catch(BadRequestException error){
            throw validationResponse(error.getMessage());
        }
        PostOperationalConfig config=PostOperationalConfig.find("postId=?1 and instanceCountryId=?2",postId,tenant.instanceCountryId()).firstResult();
        if(config==null){
            config=new PostOperationalConfig();
            config.instanceCountryId=tenant.instanceCountryId();
            config.postId=postId;
        }
        config.postType=request.postType().trim().toUpperCase(Locale.ROOT);
        config.description=request.description().trim();
        config.atsLocationKey=blankToEmpty(request.atsLocationKey()).toUpperCase(Locale.ROOT);
        config.atsLocationLabel=blankToEmpty(request.atsLocationLabel());
        config.atsPackageId=request.atsPackageId();
        config.atsLocationX=request.atsLocationX();
        config.atsLocationY=request.atsLocationY();
        SkillSet s=request.skills();
        config.skillAttendance=s.attendance();
        config.skillAccessControl=s.accessControl();
        config.skillPatrol=s.patrol();
        config.skillJudgement=s.judgement();
        config.skillTactical=s.tactical();
        config.skillBearing=s.bearing();
        config.skillLeadership=s.leadership();
        config.skillCustomerService=s.customerService();
        config.adjustmentJustification=blankToNull(request.adjustmentJustification());
        config.configStatus=normalizeStatus(request.configStatus());
        config.updatedByUsername=identity.getPrincipal().getName();
        if(config.id==null) config.persist();
        post.configStatus="CONFIGURED".equals(config.configStatus)?"CONFIGURED":"PENDING";
        return dto(config);
    }

    private void validate(SaveRequest request, PointEntity point){
        String type=request.postType()==null?"":request.postType().trim().toUpperCase(Locale.ROOT);
        if(!POST_TYPES.contains(type)) throw new BadRequestException("Tipo de Puesto inválido");
        if(request.description()==null||request.description().isBlank()) throw new BadRequestException("La descripción es obligatoria");
        if(request.description().trim().length()>600) throw new BadRequestException("La descripción no puede superar 600 caracteres");
        boolean hasCoordinates=request.atsLocationX()!=null&&request.atsLocationY()!=null;
        if(hasCoordinates){
            if(request.atsLocationX()<0||request.atsLocationX()>1||request.atsLocationY()<0||request.atsLocationY()>1) throw new BadRequestException("La ubicación del Puesto debe estar dentro de los límites del plano ATS");
            if(request.atsPackageId()==null) throw new BadRequestException("La ubicación del Puesto debe vincularse a la versión ATS vigente");
            AtsPointPackage ats=AtsPointPackage.find("id=?1 and pointId=?2 and instanceCountryId=?3 and current=true",request.atsPackageId(),point.id,tenant.instanceCountryId()).firstResult();
            if(ats==null) throw new BadRequestException("La ubicación debe vincularse a la versión ATS vigente del Punto");
        }else{
            if(request.atsLocationKey()==null||request.atsLocationKey().isBlank()||request.atsLocationLabel()==null||request.atsLocationLabel().isBlank()) throw new BadRequestException("Debe seleccionar la ubicación del Puesto directamente sobre el plano ATS");
        }
        if(request.skills()==null) throw new BadRequestException("Las habilidades son obligatorias");
        validateSkills(request.skills());
        normalizeStatus(request.configStatus());
    }

    private void validateSkills(SkillSet skills){
        int[] values={skills.attendance(),skills.accessControl(),skills.patrol(),skills.judgement(),skills.tactical(),skills.bearing(),skills.leadership(),skills.customerService()};
        int total=0,fives=0,fours=0;
        for(int value:values){
            if(value<1||value>5) throw new BadRequestException("Cada habilidad debe estar entre 1 y 5 puntos");
            total+=value;
            if(value==5) fives++;
            if(value==4) fours++;
        }
        if(fives>1) throw new BadRequestException("Solo una habilidad puede tener 5 puntos");
        if(fours>2) throw new BadRequestException("Solo dos habilidades pueden tener 4 puntos");
        if(total>22) throw new BadRequestException("La suma de habilidades no puede superar 22 puntos");
    }

    private String normalizeStatus(String status){
        String value=status==null?"DRAFT":status.trim().toUpperCase(Locale.ROOT);
        if(!Set.of("DRAFT","CONFIGURED").contains(value)) throw new BadRequestException("Estado de configuración inválido");
        return value;
    }

    private PointEntity point(UUID pointId){
        if(pointId==null) throw new BadRequestException("pointId obligatorio");
        PointEntity point=PointEntity.find("id=?1 and instanceCountryId=?2",pointId,tenant.instanceCountryId()).firstResult();
        if(point==null) throw new NotFoundException("Punto no encontrado");
        return point;
    }

    private ConfigDto defaults(PostEntity post){
        String type=post.name!=null&&post.name.toLowerCase(Locale.ROOT).contains("acceso")?"CAA":post.name!=null&&post.name.toLowerCase(Locale.ROOT).contains("perímetro")?"PAT":"VIG";
        SkillSet skills=template(type);
        return new ConfigDto(post.id,type,"","","",null,null,null,skills,null,"DRAFT",null);
    }

    private SkillSet template(String type){
        return switch(type){
            case "CAA" -> new SkillSet(3,3,1,3,1,3,1,3);
            case "PAT" -> new SkillSet(2,1,3,2,2,1,1,1);
            case "MIX" -> new SkillSet(3,3,3,3,2,3,1,3);
            default -> new SkillSet(2,1,1,2,2,2,1,1);
        };
    }

    private ConfigDto dto(PostOperationalConfig c){
        return new ConfigDto(c.postId,c.postType,c.description,c.atsLocationKey,c.atsLocationLabel,c.atsPackageId,c.atsLocationX,c.atsLocationY,
            new SkillSet(c.skillAttendance,c.skillAccessControl,c.skillPatrol,c.skillJudgement,c.skillTactical,c.skillBearing,c.skillLeadership,c.skillCustomerService),
            c.adjustmentJustification,c.configStatus,c.updatedByUsername);
    }

    private String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
    private String blankToEmpty(String value){return value==null?"":value.trim();}

    private BadRequestException validationResponse(String message){
        return new BadRequestException(Response.status(Response.Status.BAD_REQUEST)
            .type(MediaType.APPLICATION_JSON)
            .entity(Map.of("message",message==null||message.isBlank()?"La solicitud no cumple las validaciones requeridas.":message))
            .build());
    }
}
