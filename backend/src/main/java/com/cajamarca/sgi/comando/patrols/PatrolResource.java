package com.cajamarca.sgi.comando.patrols;

import com.cajamarca.sgi.comando.ats.AtsPointPackage;
import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.operations.PostEntity;
import com.cajamarca.sgi.comando.storage.StandardImageStore;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.time.*;
import java.util.*;

@Path("/api/patrols")
@Produces(MediaType.APPLICATION_JSON)
public class PatrolResource {
    private static final Set<String> STRUCTURES=Set.of("CLOSED","OPEN");
    private static final Set<String> SCHEDULES=Set.of("PROGRAMMED","UNPROGRAMMED");
    private static final Set<String> SEQUENCES=Set.of("STRICT","FLEXIBLE");
    private static final Set<String> ORIGINS=Set.of("ATS","FIELD","MIXED");
    private static final Set<String> CONTROL_TYPES=Set.of("INSPECCION_VISUAL","FOTOGRAFIA","CONFIRMACION","LECTURA");
    private static final Set<String> RULE_TYPES=Set.of("INSPECCION_VISUAL","FOTOGRAFIA","CONFIRMACION","LECTURA");
    private static final Set<String> IMAGE_TYPES=Set.of("image/jpeg","image/png","image/webp");
    private static final long MAX_IMAGE=5L*1024*1024;

    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject SecurityIdentity identity;
    @Inject StandardImageStore standardImages;

    public record RuleDto(UUID id,UUID checkpointId,int sortOrder,String ruleType,boolean required,boolean evidenceRequired) {}
    public record CheckpointDto(UUID id,UUID patrolId,int sortOrder,String code,String name,String description,String originMode,UUID atsPackageId,Double atsX,Double atsY,Double latitude,Double longitude,Double gpsAccuracyM,Instant locationCapturedAt,String locationCapturedBy,String controlType,boolean requiresEvidence,boolean hasStandardImage,List<StandardImageDto> standardImages,int standardImageVersion,String standardImageNotes,boolean visintEnabled,List<RuleDto> rules) {}
    public record StandardImageDto(UUID id,int position,String originalName,String contentType) {}
    public record PatrolDto(UUID id,UUID protocolId,String code,String name,String description,String structureType,String scheduleType,String sequenceType,String windowStart,String windowEnd,int repetitions,int versionNo,String updatedBy,List<CheckpointDto> checkpoints) {}
    public record ProtocolDto(UUID id,UUID seriesId,UUID basedOnProtocolId,UUID postId,String code,String name,String description,String status,int versionNo,Instant lastPublishedAt,String updatedBy,List<UUID> applicablePostIds,List<PatrolDto> patrols) {}
    public record CreateProtocolRequest(UUID postId,String name) {}
    public record SaveProtocolRequest(String name,String description,List<UUID> applicablePostIds) {}
    public record SaveScopeRequest(List<UUID> applicablePostIds) {}
    public record CreatePatrolRequest(String name,String structureType,String scheduleType) {}
    public record SavePatrolRequest(String name,String description,String structureType,String scheduleType,String sequenceType,String windowStart,String windowEnd,Integer repetitions) {}
    public record CreateCheckpointRequest(String name,String description,String originMode,UUID atsPackageId,Double atsX,Double atsY,Double latitude,Double longitude,Double gpsAccuracyM,String controlType,Boolean requiresEvidence) {}
    public record SaveCheckpointRequest(String name,String description,String originMode,UUID atsPackageId,Double atsX,Double atsY,Double latitude,Double longitude,Double gpsAccuracyM,String controlType,boolean requiresEvidence,String standardImageNotes,Boolean visintEnabled) {}
    public record SaveRulesRequest(List<RuleInput> rules) {}
    public record RuleInput(String ruleType,boolean required,boolean evidenceRequired) {}

    private static final String READ_ROLES="PRESIDENTE,DIRECTOR_OPERACIONES_LATAM,DIRECTOR_OPERACIONES_NACIONAL,DIRECTOR_NACIONAL,DIRECTOR_ZONAL,JEFE_REGIONAL,COORDINADOR_COMPANIA,ASISTENTE_COORDINACION,SUPERVISOR_SEGURIDAD,AGENTE_SEGURIDAD,CLIENTE";

    @GET
    @Path("/protocols")
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
    public List<ProtocolDto> list(@QueryParam("pointId") UUID pointId){
        PointEntity point=point(pointId); scope.requireCompany(point.companyId);
        List<PostEntity> posts=PostEntity.list("pointId=?1 and instanceCountryId=?2",pointId,tenant.instanceCountryId());
        if(posts.isEmpty()) return List.of();
        Set<UUID> ids=new HashSet<>(); posts.forEach(p->ids.add(p.id));
        List<PatrolProtocol> all=PatrolProtocol.list("instanceCountryId=?1 and postId in ?2 order by code,versionNo desc",tenant.instanceCountryId(),ids);
        Map<UUID,PatrolProtocol> latest=new LinkedHashMap<>();
        for(PatrolProtocol p:all){
            PatrolProtocol current=latest.get(p.seriesId);
            if(current==null || rank(p)>rank(current) || (rank(p)==rank(current)&&p.versionNo>current.versionNo)) latest.put(p.seriesId,p);
        }
        return latest.values().stream().sorted(Comparator.comparing((PatrolProtocol p)->p.code)).map(this::dto).toList();
    }

    @GET
    @Path("/protocols/{protocolId}/history")
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
    public List<ProtocolDto> history(@PathParam("protocolId") UUID protocolId){
        PatrolProtocol p=protocol(protocolId); authorize(p);
        List<PatrolProtocol> rows=PatrolProtocol.list("instanceCountryId=?1 and seriesId=?2 order by versionNo desc",tenant.instanceCountryId(),p.seriesId);
        return rows.stream().map(this::dto).toList();
    }

    @POST
    @Path("/protocols")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public ProtocolDto createProtocol(CreateProtocolRequest req){
        if(req==null||req.postId()==null) throw new BadRequestException("Puesto obligatorio");
        PostEntity post=post(req.postId()); PointEntity point=point(post.pointId); scope.requireCompany(point.companyId);
        PatrolProtocol p=new PatrolProtocol(); p.id=UUID.randomUUID(); p.instanceCountryId=tenant.instanceCountryId(); p.seriesId=p.id; p.postId=post.id;
        p.code=nextProtocolCode(); p.name=clean(req.name(),"Nuevo protocolo de patrullas"); p.description=""; p.status="BORRADOR"; p.versionNo=1; p.updatedByUsername=user(); p.persist();
        setScope(p,List.of(post.id));
        return dto(p);
    }

    @PUT
    @Path("/protocols/{protocolId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public ProtocolDto saveProtocol(@PathParam("protocolId") UUID protocolId,SaveProtocolRequest req){
        PatrolProtocol p=protocol(protocolId); authorize(p); requireDraft(p); if(req==null)throw new BadRequestException("Solicitud obligatoria");
        p.name=clean(req.name(),p.name); p.description=req.description()==null?"":req.description().trim(); setScope(p,req.applicablePostIds()); p.updatedByUsername=user(); return dto(p);
    }

    @PUT
    @Path("/protocols/{protocolId}/scope")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public ProtocolDto saveScope(@PathParam("protocolId") UUID protocolId,SaveScopeRequest req){
        PatrolProtocol p=protocol(protocolId); authorize(p); if(req==null)throw new BadRequestException("Solicitud obligatoria");
        setScope(p,req.applicablePostIds());
        if(!"BORRADOR".equals(p.status)){
            if(scopeIds(p).isEmpty()) setProtocolState(p,"INACTIVO");
            else activateVersion(p);
        }
        p.updatedByUsername=user();
        return dto(p);
    }

    @POST
    @Path("/protocols/{protocolId}/fork")
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public ProtocolDto fork(@PathParam("protocolId") UUID protocolId){
        PatrolProtocol source=protocol(protocolId); authorize(source);
        PatrolProtocol existing=PatrolProtocol.find("instanceCountryId=?1 and seriesId=?2 and status='BORRADOR'",tenant.instanceCountryId(),source.seriesId).firstResult();
        if(existing!=null) return dto(existing);
        PatrolProtocol target=new PatrolProtocol(); target.id=UUID.randomUUID();target.instanceCountryId=tenant.instanceCountryId();target.seriesId=source.seriesId;target.basedOnProtocolId=source.id;target.postId=source.postId;target.code=source.code;target.name=source.name;target.description=source.description;target.status="BORRADOR";target.versionNo=source.versionNo+1;target.updatedByUsername=user();target.persist();
        setScope(target,scopeIds(source));
        for(PatrolDefinition old:patrols(source.id)){
            PatrolDefinition copy=new PatrolDefinition();copy.id=UUID.randomUUID();copy.instanceCountryId=tenant.instanceCountryId();copy.pointId=old.pointId;copy.protocolId=target.id;copy.code=old.code;copy.name=old.name;copy.description=old.description;copy.structureType=old.structureType;copy.scheduleType=old.scheduleType;copy.sequenceType=old.sequenceType;copy.windowStart=old.windowStart;copy.windowEnd=old.windowEnd;copy.repetitions=old.repetitions;copy.versionNo=old.versionNo;copy.legacyVersion=copy.versionNo;copy.status="BORRADOR";copy.updatedByUsername=user();copy.persist();
            for(PatrolCheckpoint cp:checkpoints(old.id)) cloneCheckpoint(cp,copy.id);
        }
        return dto(target);
    }

    @POST
    @Path("/protocols/{protocolId}/publish")
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public ProtocolDto publish(@PathParam("protocolId") UUID protocolId){
        PatrolProtocol p=protocol(protocolId); authorize(p); requireDraft(p);
        List<PatrolDefinition> defs=patrols(p.id); if(defs.isEmpty())throw new BadRequestException("El protocolo debe contener al menos una patrulla");
        defs.forEach(this::validatePatrolForPublish);
        if(scopeIds(p).isEmpty()) setProtocolState(p,"INACTIVO"); else activateVersion(p);p.lastPublishedAt=Instant.now();p.updatedByUsername=user();for(PatrolDefinition def:defs){def.legacyVersion=def.versionNo;}return dto(p);
    }

    @POST
    @Path("/protocols/{protocolId}/activate")
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public ProtocolDto activate(@PathParam("protocolId") UUID protocolId){
        PatrolProtocol p=protocol(protocolId);authorize(p);if("BORRADOR".equals(p.status))throw new ClientErrorException("El borrador debe publicarse antes de activarse.",409);
        if(scopeIds(p).isEmpty())setScope(p,List.of(p.postId));
        activateVersion(p);p.updatedByUsername=user();return dto(p);
    }

    @POST
    @Path("/protocols/{protocolId}/deactivate")
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public ProtocolDto deactivate(@PathParam("protocolId") UUID protocolId){
        PatrolProtocol p=protocol(protocolId);authorize(p);if("BORRADOR".equals(p.status))throw new ClientErrorException("El borrador no puede inactivarse.",409);PatrolProtocolPostScope.delete("protocolId=?1 and instanceCountryId=?2",p.id,tenant.instanceCountryId());setProtocolState(p,"INACTIVO");p.updatedByUsername=user();return dto(p);
    }

    @POST
    @Path("/protocols/{protocolId}/patrols")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public PatrolDto createPatrol(@PathParam("protocolId") UUID protocolId,CreatePatrolRequest req){
        PatrolProtocol p=protocol(protocolId); authorize(p); requireDraft(p);
        int patrolCount=(int)PatrolDefinition.count("protocolId=?1 and instanceCountryId=?2",p.id,tenant.instanceCountryId());
        if(patrolCount>=15) throw new BadRequestException("Un Protocolo admite máximo 15 Patrullas");
        PostEntity protocolPost=post(p.postId);PatrolDefinition row=new PatrolDefinition();row.instanceCountryId=tenant.instanceCountryId();row.pointId=protocolPost.pointId;row.protocolId=p.id;row.code=nextPatrolCode(p.id);row.name=clean(req==null?null:req.name(),"Nueva patrulla");row.description="";row.structureType=normalize(req==null?null:req.structureType(),STRUCTURES,"CLOSED","Modalidad espacial inválida");row.scheduleType=normalize(req==null?null:req.scheduleType(),SCHEDULES,"PROGRAMMED","Modalidad temporal inválida");row.sequenceType="CLOSED".equals(row.structureType)?"STRICT":null;row.windowStart="PROGRAMMED".equals(row.scheduleType)?LocalTime.of(22,0):null;row.windowEnd="PROGRAMMED".equals(row.scheduleType)?LocalTime.of(22,45):null;row.repetitions=1;row.versionNo=1;row.legacyVersion=1;row.status="BORRADOR";row.updatedByUsername=user();row.persist();return patrolDto(row);
    }

    @PUT
    @Path("/patrols/{patrolId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public PatrolDto savePatrol(@PathParam("patrolId") UUID patrolId,SavePatrolRequest req){
        PatrolDefinition row=patrol(patrolId);PatrolProtocol protocol=protocol(row.protocolId);authorize(protocol);requireDraft(protocol);if(req==null)throw new BadRequestException("Solicitud obligatoria");
        row.name=clean(req.name(),row.name);row.description=req.description()==null?"":req.description().trim();row.structureType=normalize(req.structureType(),STRUCTURES,row.structureType,"Modalidad espacial inválida");row.scheduleType=normalize(req.scheduleType(),SCHEDULES,row.scheduleType,"Modalidad temporal inválida");
        row.sequenceType="CLOSED".equals(row.structureType)?normalize(req.sequenceType(),SEQUENCES,row.sequenceType==null?"STRICT":row.sequenceType,"Secuencia inválida"):null;
        row.windowStart="PROGRAMMED".equals(row.scheduleType)?parseTime(req.windowStart(),row.windowStart):null;row.windowEnd="PROGRAMMED".equals(row.scheduleType)?parseTime(req.windowEnd(),row.windowEnd):null;row.repetitions=Math.max(1,req.repetitions()==null?row.repetitions:req.repetitions());row.versionNo++;row.legacyVersion=row.versionNo;row.status="BORRADOR";row.updatedByUsername=user();
        if("OPEN".equals(row.structureType)){for(PatrolCheckpoint cp:checkpoints(row.id)){PatrolCheckpointRule.delete("checkpointId",cp.id);cp.delete();}}
        validateWindow(row);return patrolDto(row);
    }

    @DELETE
    @Path("/patrols/{patrolId}")
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public void deletePatrol(@PathParam("patrolId") UUID patrolId){PatrolDefinition row=patrol(patrolId);PatrolProtocol p=protocol(row.protocolId);authorize(p);requireDraft(p);for(PatrolCheckpoint cp:checkpoints(row.id)){PatrolCheckpointRule.delete("checkpointId=?1 and instanceCountryId=?2",cp.id,tenant.instanceCountryId());cp.delete();}row.delete();}

    @POST
    @Path("/patrols/{patrolId}/checkpoints")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public CheckpointDto createCheckpoint(@PathParam("patrolId") UUID patrolId,CreateCheckpointRequest req){
        PatrolDefinition pat=patrol(patrolId);PatrolProtocol p=protocol(pat.protocolId);authorize(p);requireDraft(p);if(!"CLOSED".equals(pat.structureType))throw new BadRequestException("Las Patrullas Abiertas no tienen Hitos predefinidos");
        int count=(int)PatrolCheckpoint.count("patrolId=?1 and instanceCountryId=?2",pat.id,tenant.instanceCountryId());if(count>=25)throw new BadRequestException("Una Patrulla Cerrada admite máximo 25 Hitos");
        PatrolCheckpoint cp=new PatrolCheckpoint();cp.instanceCountryId=tenant.instanceCountryId();cp.patrolId=pat.id;cp.sortOrder=count+1;cp.validationRuleJson="{}";cp.code=String.format(Locale.ROOT,"H%02d",count+1);cp.name=clean(req==null?null:req.name(),"Nuevo Hito");cp.description=req==null||req.description()==null?"":req.description().trim();cp.originMode=normalize(req==null?null:req.originMode(),ORIGINS,"ATS","Origen inválido");cp.atsPackageId=req==null?null:req.atsPackageId();cp.atsX=req==null?null:req.atsX();cp.atsY=req==null?null:req.atsY();cp.latitude=req==null?null:req.latitude();cp.longitude=req==null?null:req.longitude();cp.gpsAccuracyM=req==null?null:req.gpsAccuracyM();cp.controlType=normalize(req==null?null:req.controlType(),CONTROL_TYPES,"INSPECCION_VISUAL","Tipo de control inválido");cp.requiresEvidence=req==null||req.requiresEvidence()==null||req.requiresEvidence();cp.standardImageVersion=0;cp.standardImageNotes="";cp.visintEnabled=cp.requiresEvidence;
        validateOrigin(cp,p);if("FIELD".equals(cp.originMode)||"MIXED".equals(cp.originMode)){cp.locationCapturedAt=Instant.now();cp.locationCapturedBy=user();}cp.persist();seedRules(cp);return checkpointDto(cp);
    }

    @PUT
    @Path("/checkpoints/{checkpointId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public CheckpointDto saveCheckpoint(@PathParam("checkpointId") UUID checkpointId,SaveCheckpointRequest req){
        PatrolCheckpoint cp=checkpoint(checkpointId);PatrolDefinition pat=patrol(cp.patrolId);PatrolProtocol p=protocol(pat.protocolId);authorize(p);requireDraft(p);if(req==null)throw new BadRequestException("Solicitud obligatoria");
        cp.name=clean(req.name(),cp.name);cp.description=req.description()==null?"":req.description().trim();String oldOrigin=cp.originMode;cp.originMode=normalize(req.originMode(),ORIGINS,cp.originMode,"Origen inválido");cp.atsPackageId=req.atsPackageId();cp.atsX=req.atsX();cp.atsY=req.atsY();cp.latitude=req.latitude();cp.longitude=req.longitude();cp.gpsAccuracyM=req.gpsAccuracyM();cp.controlType=normalize(req.controlType(),CONTROL_TYPES,cp.controlType,"Tipo de control inválido");cp.requiresEvidence=req.requiresEvidence();cp.standardImageNotes=req.standardImageNotes()==null?"":req.standardImageNotes().trim();validateOrigin(cp,p);
        if(req.visintEnabled()!=null){if(req.visintEnabled()&&!cp.requiresEvidence)throw new BadRequestException("VISINT requiere que el Hito exija fotos");cp.visintEnabled=req.visintEnabled();}
        if(!cp.requiresEvidence)cp.visintEnabled=false;
        if(("FIELD".equals(cp.originMode)||"MIXED".equals(cp.originMode))&&(!Objects.equals(oldOrigin,cp.originMode)||cp.locationCapturedAt==null)){cp.locationCapturedAt=Instant.now();cp.locationCapturedBy=user();}
        pat.versionNo++;pat.legacyVersion=pat.versionNo;pat.updatedByUsername=user();return checkpointDto(cp);
    }

    @PUT
    @Path("/checkpoints/{checkpointId}/rules")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public CheckpointDto saveRules(@PathParam("checkpointId") UUID checkpointId,SaveRulesRequest req){
        PatrolCheckpoint cp=checkpoint(checkpointId);PatrolDefinition pat=patrol(cp.patrolId);PatrolProtocol p=protocol(pat.protocolId);authorize(p);requireDraft(p);PatrolCheckpointRule.delete("checkpointId=?1 and instanceCountryId=?2",cp.id,tenant.instanceCountryId());
        int order=1; if(req!=null&&req.rules()!=null)for(RuleInput input:req.rules()){String kind=normalize(input.ruleType(),RULE_TYPES,null,"Regla inválida");PatrolCheckpointRule r=new PatrolCheckpointRule();r.instanceCountryId=tenant.instanceCountryId();r.checkpointId=cp.id;r.sortOrder=order++;r.ruleType=kind;r.required=input.required();r.evidenceRequired=input.evidenceRequired();r.persist();}
        pat.versionNo++;pat.legacyVersion=pat.versionNo;pat.updatedByUsername=user();return checkpointDto(cp);
    }

    @DELETE
    @Path("/checkpoints/{checkpointId}")
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public void deleteCheckpoint(@PathParam("checkpointId") UUID checkpointId){PatrolCheckpoint cp=checkpoint(checkpointId);PatrolDefinition pat=patrol(cp.patrolId);PatrolProtocol p=protocol(pat.protocolId);authorize(p);requireDraft(p);cp.delete();List<PatrolCheckpoint> rest=checkpoints(pat.id);int i=1;for(PatrolCheckpoint row:rest){row.sortOrder=i;row.code=String.format(Locale.ROOT,"H%02d",i);i++;}pat.versionNo++;pat.legacyVersion=pat.versionNo;pat.updatedByUsername=user();}

    /** Agrega una foto estándar al Hito (hasta 5). VISINT las recibe todas en referenceImages. */
    @POST
    @Path("/checkpoints/{checkpointId}/standard-images")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public CheckpointDto addStandardImage(@PathParam("checkpointId") UUID checkpointId,@RestForm("file") FileUpload file) throws java.io.IOException{
        if(file==null)throw new BadRequestException("Imagen obligatoria");
        if(file.size()>StandardImageStore.MAX_BYTES)throw new BadRequestException("La foto estándar no puede superar 5 MB");
        PatrolCheckpoint cp=checkpoint(checkpointId);PatrolDefinition pat=patrol(cp.patrolId);PatrolProtocol p=protocol(pat.protocolId);authorize(p);requireDraft(p);
        long count=PatrolCheckpointStandardImage.count("checkpointId",cp.id);
        if(count>=PatrolCheckpointStandardImage.MAX)throw new BadRequestException("El Hito ya tiene "+PatrolCheckpointStandardImage.MAX+" fotos estándar (máximo)");
        StandardImageStore.Stored s=standardImages.save("patrol",java.nio.file.Files.readAllBytes(file.uploadedFile()));
        PatrolCheckpointStandardImage img=new PatrolCheckpointStandardImage();img.id=UUID.randomUUID();img.instanceCountryId=tenant.instanceCountryId();img.checkpointId=cp.id;img.position=(int)count+1;
        img.originalName=clean(file.fileName(),"foto-estandar");img.contentType=s.contentType();img.objectKey=s.objectKey();img.sha256=s.sha256();img.sizeBytes=s.size();img.createdAt=Instant.now();img.persist();
        cp.standardImageVersion++;pat.versionNo++;pat.legacyVersion=pat.versionNo;pat.updatedByUsername=user();return checkpointDto(cp);
    }

    @GET
    @Path("/checkpoints/{checkpointId}/standard-images/{imageId}")
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
    public Response standardImage(@PathParam("checkpointId") UUID checkpointId,@PathParam("imageId") UUID imageId){
        PatrolCheckpoint cp=checkpoint(checkpointId);PatrolDefinition pat=patrol(cp.patrolId);PatrolProtocol p=protocol(pat.protocolId);authorize(p);PatrolCheckpointStandardImage img=standardImage(cp,imageId);
        return Response.ok(standardImages.read(img.objectKey,null)).type(img.contentType).header(HttpHeaders.CACHE_CONTROL,"no-store").build();
    }

    /** Quita una foto estándar y reordena las demás. Sin fotos estándar, VISINT se apaga. */
    @DELETE
    @Path("/checkpoints/{checkpointId}/standard-images/{imageId}")
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public CheckpointDto deleteStandardImage(@PathParam("checkpointId") UUID checkpointId,@PathParam("imageId") UUID imageId){
        PatrolCheckpoint cp=checkpoint(checkpointId);PatrolDefinition pat=patrol(cp.patrolId);PatrolProtocol p=protocol(pat.protocolId);authorize(p);requireDraft(p);
        standardImage(cp,imageId).delete();
        int i=1;for(PatrolCheckpointStandardImage img:PatrolCheckpointStandardImage.of(cp.id))if(!img.id.equals(imageId))img.position=i++;
        if(i==1)cp.visintEnabled=false;
        cp.standardImageVersion++;pat.versionNo++;pat.legacyVersion=pat.versionNo;pat.updatedByUsername=user();return checkpointDto(cp);
    }

    private PatrolCheckpointStandardImage standardImage(PatrolCheckpoint cp,UUID imageId){
        PatrolCheckpointStandardImage img=PatrolCheckpointStandardImage.find("id=?1 and checkpointId=?2",imageId,cp.id).firstResult();
        if(img==null)throw new NotFoundException("Foto estándar no encontrada");
        return img;
    }

    private void validatePatrolForPublish(PatrolDefinition p){validateWindow(p);if("CLOSED".equals(p.structureType)){List<PatrolCheckpoint> items=checkpoints(p.id);long count=items.size();if(count<1)throw new BadRequestException(p.code+" debe tener al menos un Hito");if(count>25)throw new BadRequestException(p.code+" supera el máximo de 25 Hitos");PatrolProtocol protocol=protocol(p.protocolId);for(PatrolCheckpoint checkpoint:items){validateOrigin(checkpoint,protocol);if(checkpoint.visintEnabled&&PatrolCheckpointStandardImage.count("checkpointId",checkpoint.id)==0)throw new BadRequestException(checkpoint.code+": VISINT activo requiere al menos una foto estándar");}}}
    private void validateWindow(PatrolDefinition p){if(!"PROGRAMMED".equals(p.scheduleType))return;if(p.windowStart==null||p.windowEnd==null)throw new BadRequestException("La Patrulla Programada requiere hora Desde y Hasta");long start=p.windowStart.toSecondOfDay()/60,end=p.windowEnd.toSecondOfDay()/60,diff=end-start;if(diff<=0)diff+=1440;if(diff>60)throw new BadRequestException("La ventana de una Patrulla Programada no puede superar 1 hora");}
    private void validateOrigin(PatrolCheckpoint cp,PatrolProtocol protocol){if("ATS".equals(cp.originMode)){if(cp.atsX==null||cp.atsY==null)throw new BadRequestException("El Hito de Plano ATS requiere coordenadas del plano");if(cp.atsX<0||cp.atsX>1||cp.atsY<0||cp.atsY>1)throw new BadRequestException("Coordenadas ATS fuera de rango");if(cp.atsPackageId==null){PostEntity post=post(protocol.postId);AtsPointPackage pkg=AtsPointPackage.find("instanceCountryId=?1 and pointId=?2 and current=true",tenant.instanceCountryId(),post.pointId).firstResult();if(pkg==null)throw new BadRequestException("El Punto no tiene un plano ATS vigente");cp.atsPackageId=pkg.id;}}else if("FIELD".equals(cp.originMode)){if(cp.latitude==null||cp.longitude==null)throw new BadRequestException("El Hito levantado en campo requiere coordenadas GPS");}else if("MIXED".equals(cp.originMode)){if((cp.latitude==null||cp.longitude==null)&&(cp.atsX==null||cp.atsY==null))throw new BadRequestException("El Hito mixto requiere una ubicación ATS o GPS");}}
    private void seedRules(PatrolCheckpoint cp){String[] kinds={"INSPECCION_VISUAL","FOTOGRAFIA","CONFIRMACION"};for(int i=0;i<kinds.length;i++){PatrolCheckpointRule r=new PatrolCheckpointRule();r.instanceCountryId=tenant.instanceCountryId();r.checkpointId=cp.id;r.sortOrder=i+1;r.ruleType=kinds[i];r.required=true;r.evidenceRequired="FOTOGRAFIA".equals(kinds[i]);r.persist();}}
    private PatrolCheckpoint cloneCheckpoint(PatrolCheckpoint old,UUID newPatrolId){PatrolCheckpoint cp=new PatrolCheckpoint();cp.id=UUID.randomUUID();cp.instanceCountryId=tenant.instanceCountryId();cp.patrolId=newPatrolId;cp.sortOrder=old.sortOrder;cp.radiusM=old.radiusM;cp.validationRuleJson=old.validationRuleJson==null?"{}":old.validationRuleJson;cp.code=old.code;cp.name=old.name;cp.description=old.description;cp.originMode=old.originMode;cp.atsPackageId=old.atsPackageId;cp.atsX=old.atsX;cp.atsY=old.atsY;cp.latitude=old.latitude;cp.longitude=old.longitude;cp.gpsAccuracyM=old.gpsAccuracyM;cp.locationCapturedAt=old.locationCapturedAt;cp.locationCapturedBy=old.locationCapturedBy;cp.controlType=old.controlType;cp.requiresEvidence=old.requiresEvidence;cp.standardImageOriginalName=old.standardImageOriginalName;cp.standardImageContentType=old.standardImageContentType;cp.standardImageData=old.standardImageData==null?null:Arrays.copyOf(old.standardImageData,old.standardImageData.length);cp.standardImageObjectKey=old.standardImageObjectKey;cp.standardImageSha256=old.standardImageSha256;cp.standardImageSize=old.standardImageSize;cp.standardImageVersion=old.standardImageVersion;cp.standardImageNotes=old.standardImageNotes;cp.visintEnabled=old.visintEnabled;cp.persist();for(PatrolCheckpointStandardImage o:PatrolCheckpointStandardImage.of(old.id)){PatrolCheckpointStandardImage n=new PatrolCheckpointStandardImage();n.id=UUID.randomUUID();n.instanceCountryId=o.instanceCountryId;n.checkpointId=cp.id;n.position=o.position;n.originalName=o.originalName;n.contentType=o.contentType;n.objectKey=o.objectKey;n.sha256=o.sha256;n.sizeBytes=o.sizeBytes;n.createdAt=o.createdAt;n.persist();}for(PatrolCheckpointRule oldRule:rules(old.id)){PatrolCheckpointRule r=new PatrolCheckpointRule();r.instanceCountryId=tenant.instanceCountryId();r.checkpointId=cp.id;r.sortOrder=oldRule.sortOrder;r.ruleType=oldRule.ruleType;r.required=oldRule.required;r.evidenceRequired=oldRule.evidenceRequired;r.persist();}return cp;}

    private void activateVersion(PatrolProtocol p){
        List<PatrolProtocol> active=PatrolProtocol.list("instanceCountryId=?1 and seriesId=?2 and status='ACTIVO' and id<>?3",tenant.instanceCountryId(),p.seriesId,p.id);
        for(PatrolProtocol previous:active){
            setProtocolState(previous,"INACTIVO");
            previous.updatedByUsername=user();
            PatrolProtocolPostScope.delete("protocolId=?1 and instanceCountryId=?2",previous.id,tenant.instanceCountryId());
        }
        setProtocolState(p,"ACTIVO");
    }
    private void setProtocolState(PatrolProtocol p,String status){
        p.status=status;
        for(PatrolDefinition def:patrols(p.id))def.status=status;
    }
    private List<UUID> scopeIds(PatrolProtocol p){
        return PatrolProtocolPostScope.<PatrolProtocolPostScope>list("instanceCountryId=?1 and protocolId=?2 order by createdAt",tenant.instanceCountryId(),p.id).stream().map(x->x.postId).toList();
    }
    private void setScope(PatrolProtocol p,List<UUID> postIds){
        PostEntity anchorPost=post(p.postId);
        LinkedHashSet<UUID> unique=new LinkedHashSet<>();
        if(postIds!=null)unique.addAll(postIds);
        PatrolProtocolPostScope.delete("protocolId=?1 and instanceCountryId=?2",p.id,tenant.instanceCountryId());
        for(UUID id:unique){
            PostEntity candidate=post(id);
            if(!candidate.pointId.equals(anchorPost.pointId))throw new BadRequestException("Todos los Puestos del alcance deben pertenecer al mismo Punto");
            PatrolProtocolPostScope row=new PatrolProtocolPostScope();row.instanceCountryId=tenant.instanceCountryId();row.protocolId=p.id;row.postId=id;row.persist();
        }
    }

    private int rank(PatrolProtocol p){return "BORRADOR".equals(p.status)?2:1;}
    private List<PatrolDefinition> patrols(UUID protocolId){return PatrolDefinition.list("protocolId=?1 and instanceCountryId=?2 order by code",protocolId,tenant.instanceCountryId());}
    private List<PatrolCheckpoint> checkpoints(UUID patrolId){return PatrolCheckpoint.list("patrolId=?1 and instanceCountryId=?2 order by sortOrder",patrolId,tenant.instanceCountryId());}
    private List<PatrolCheckpointRule> rules(UUID checkpointId){return PatrolCheckpointRule.list("checkpointId=?1 and instanceCountryId=?2 order by sortOrder",checkpointId,tenant.instanceCountryId());}
    private ProtocolDto dto(PatrolProtocol p){return new ProtocolDto(p.id,p.seriesId,p.basedOnProtocolId,p.postId,p.code,p.name,p.description,p.status,p.versionNo,p.lastPublishedAt,p.updatedByUsername,scopeIds(p),patrols(p.id).stream().map(this::patrolDto).toList());}
    private PatrolDto patrolDto(PatrolDefinition p){return new PatrolDto(p.id,p.protocolId,p.code,p.name,p.description,p.structureType,p.scheduleType,p.sequenceType,p.windowStart==null?null:p.windowStart.toString(),p.windowEnd==null?null:p.windowEnd.toString(),p.repetitions,p.versionNo,p.updatedByUsername,checkpoints(p.id).stream().map(this::checkpointDto).toList());}
    private CheckpointDto checkpointDto(PatrolCheckpoint c){List<StandardImageDto> images=PatrolCheckpointStandardImage.of(c.id).stream().map(i->new StandardImageDto(i.id,i.position,i.originalName,i.contentType)).toList();return new CheckpointDto(c.id,c.patrolId,c.sortOrder,c.code,c.name,c.description,c.originMode,c.atsPackageId,c.atsX,c.atsY,c.latitude,c.longitude,c.gpsAccuracyM,c.locationCapturedAt,c.locationCapturedBy,c.controlType,c.requiresEvidence,!images.isEmpty(),images,c.standardImageVersion,c.standardImageNotes,c.visintEnabled,rules(c.id).stream().map(r->new RuleDto(r.id,r.checkpointId,r.sortOrder,r.ruleType,r.required,r.evidenceRequired)).toList());}
    private void requireDraft(PatrolProtocol p){if(!"BORRADOR".equals(p.status))throw new ClientErrorException("La versión publicada es inmutable. Cree una nueva versión en borrador para editar.",409);}
    private void authorize(PatrolProtocol p){PostEntity post=post(p.postId);PointEntity point=point(post.pointId);scope.requireCompany(point.companyId);}
    private PointEntity point(UUID id){if(id==null)throw new BadRequestException("pointId obligatorio");PointEntity p=PointEntity.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(p==null)throw new NotFoundException("Punto no encontrado");return p;}
    private PostEntity post(UUID id){PostEntity p=PostEntity.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(p==null)throw new NotFoundException("Puesto no encontrado");return p;}
    private PatrolProtocol protocol(UUID id){PatrolProtocol p=PatrolProtocol.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(p==null)throw new NotFoundException("Protocolo no encontrado");return p;}
    private PatrolDefinition patrol(UUID id){PatrolDefinition p=PatrolDefinition.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(p==null)throw new NotFoundException("Patrulla no encontrada");return p;}
    private PatrolCheckpoint checkpoint(UUID id){PatrolCheckpoint p=PatrolCheckpoint.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(p==null)throw new NotFoundException("Hito no encontrado");return p;}
    private String user(){return identity.getPrincipal().getName();}
    private String nextProtocolCode(){List<PatrolProtocol> all=PatrolProtocol.list("instanceCountryId=?1",tenant.instanceCountryId());long count=all.stream().map(row->row.seriesId).distinct().count();return String.format(Locale.ROOT,"PRO-PAT-%04d",count+1);}
    private String nextPatrolCode(UUID protocolId){
        int max=0;
        List<PatrolDefinition> rows=PatrolDefinition.list("protocolId=?1 and instanceCountryId=?2",protocolId,tenant.instanceCountryId());
        for(PatrolDefinition row:rows){if(row.code==null)continue;String code=row.code.trim().toUpperCase(Locale.ROOT);if(!code.startsWith("PAT-"))continue;try{max=Math.max(max,Integer.parseInt(code.substring(4)));}catch(NumberFormatException ignored){}}
        return String.format(Locale.ROOT,"PAT-%03d",max+1);
    }
    private String clean(String v,String fallback){return v==null||v.isBlank()?fallback:v.trim();}
    private String normalize(String v,Set<String> allowed,String fallback,String message){String out=v==null||v.isBlank()?fallback:v.trim().toUpperCase(Locale.ROOT);if(out==null||!allowed.contains(out))throw new BadRequestException(message);return out;}
    private LocalTime parseTime(String v,LocalTime fallback){if(v==null||v.isBlank())return fallback;try{return LocalTime.parse(v);}catch(Exception e){throw new BadRequestException("Hora inválida: "+v);}}
}
