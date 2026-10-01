package com.cajamarca.sgi.comando.bitacora;

import com.cajamarca.sgi.comando.common.TenantContext;
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
import jakarta.ws.rs.core.HttpHeaders;
import com.cajamarca.sgi.comando.storage.*;
import java.time.Instant;
import java.util.*;

@Path("/api/bitacora")
@Produces(MediaType.APPLICATION_JSON)
public class BitacoraResource {
    private static final Set<String> OBJECT_TYPES=Set.of("PAX","VHL","CONT");
    private static final Set<String> APPLICATION_TYPES=Set.of("INGRESO","EGRESO","AMBOS");
    private static final Set<String> LOGICS=Set.of("ALL","ANY");
    private static final Set<String> SECTIONS=Set.of("IDENTIFICACION","VERIFICACION");
    private static final Set<String> FIELD_TYPES=Set.of("DOCUMENTO","IMAGEN","TEXTO","SELECCION","CODIGO","OTRO");
    private static final Set<String> CAPTURE_MODES=Set.of("MANUAL","CAMARA","MANUAL_QR","QR","CODIGO_BARRAS","NFC");

    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject SecurityIdentity identity;
    @Inject com.cajamarca.sgi.comando.storage.StandardImageStore standardImages;
    @Inject StandardReferenceImages references;

    public record FieldDto(UUID id,UUID accreditationId,String section,int sortOrder,String name,String description,String fieldType,boolean required,boolean evidenceRequired,String captureMode,boolean customField,boolean hasStandardImage,List<StandardReferenceImages.Dto> standardImages,int standardImageVersion,String standardImageNotes,boolean visintEnabled) {}
    public record AccreditationDto(UUID id,UUID protocolId,String code,String name,String description,String identificationLogic,String verificationLogic,boolean authPreapproval,boolean authClient,boolean authSupervisor,boolean whiteListEnabled,boolean blackListEnabled,boolean captureManual,boolean captureQr,boolean captureBarcode,boolean captureNfc,boolean captureAutomatic,List<FieldDto> fields) {}
    public record ProtocolDto(UUID id,UUID seriesId,UUID basedOnProtocolId,UUID postId,String code,String name,String objectType,String applicationType,String description,String status,int versionNo,String sourceModelType,String sourceModelName,Instant lastPublishedAt,String updatedBy,List<UUID> applicablePostIds,List<AccreditationDto> accreditations) {}
    public record CreateProtocolRequest(UUID postId,String name,String objectType,String applicationType) {}
    public record SaveProtocolRequest(String name,String objectType,String applicationType,String description,String status,List<UUID> applicablePostIds) {}
    public record SaveScopeRequest(List<UUID> applicablePostIds) {}
    public record CreateAccreditationRequest(String name,String description) {}
    public record SaveAccreditationRequest(String name,String description,String identificationLogic,String verificationLogic,boolean authPreapproval,boolean authClient,boolean authSupervisor,boolean whiteListEnabled,boolean blackListEnabled,boolean captureManual,boolean captureQr,boolean captureBarcode,boolean captureNfc,boolean captureAutomatic) {}
    public record SaveFieldRequest(String section,Integer sortOrder,String name,String description,String fieldType,boolean required,boolean evidenceRequired,String captureMode,boolean customField,String standardImageNotes) {}

    @GET
    @Path("/protocols")
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
    public List<ProtocolDto> list(@QueryParam("pointId") UUID pointId){
        PointEntity point=point(pointId);scope.requireCompany(point.companyId);
        List<PostEntity> posts=PostEntity.list("pointId=?1 and instanceCountryId=?2",pointId,tenant.instanceCountryId());
        if(posts.isEmpty()) return List.of();
        Set<UUID> postIds=new HashSet<>();posts.forEach(post->postIds.add(post.id));
        List<LogbookProtocol> all=LogbookProtocol.list("instanceCountryId=?1 and postId in ?2 order by code,versionNo desc",tenant.instanceCountryId(),postIds);
        Map<UUID,LogbookProtocol> latest=new LinkedHashMap<>();
        for(LogbookProtocol p:all){LogbookProtocol current=latest.get(p.seriesId);if(current==null||rank(p)>rank(current)||(rank(p)==rank(current)&&p.versionNo>current.versionNo))latest.put(p.seriesId,p);}
        return latest.values().stream().sorted(Comparator.comparing((LogbookProtocol p)->p.code)).map(this::dto).toList();
    }

    @GET
    @Path("/protocols/{protocolId}/history")
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
    public List<ProtocolDto> history(@PathParam("protocolId") UUID protocolId){LogbookProtocol p=protocol(protocolId);authorize(p);return LogbookProtocol.<LogbookProtocol>list("instanceCountryId=?1 and seriesId=?2 order by versionNo desc",tenant.instanceCountryId(),p.seriesId).stream().map(this::dto).toList();}

    @POST
    @Path("/protocols")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public ProtocolDto create(CreateProtocolRequest request){
        if(request==null||request.postId()==null)throw new BadRequestException("Puesto obligatorio");PostEntity post=post(request.postId());PointEntity point=point(post.pointId);scope.requireCompany(point.companyId);
        LogbookProtocol p=new LogbookProtocol();p.id=UUID.randomUUID();p.instanceCountryId=tenant.instanceCountryId();p.seriesId=p.id;p.postId=post.id;p.code=nextProtocolCode();p.name=cleanOrDefault(request.name(),"Nuevo protocolo");p.objectType=normalize(request.objectType(),OBJECT_TYPES,"PAX","Objeto inválido");p.applicationType=normalize(request.applicationType(),APPLICATION_TYPES,"INGRESO","Aplicación inválida");p.description="";p.status="BORRADOR";p.versionNo=1;
        p.identificationLogic="ALL";p.verificationLogic="ALL";p.authPreapproval=true;p.authClient=true;p.authSupervisor=false;p.whiteListEnabled=true;p.blackListEnabled=true;p.captureManual=true;p.captureQr=true;p.captureBarcode=false;p.captureNfc=false;p.captureAutomatic=false;p.sourceModelType="LOCAL";p.updatedByUsername=user();p.persist();
        setScope(p,List.of(post.id));
        LogbookAccreditation accreditation=createDefaultAccreditation(p,"Acreditación principal","Perfil inicial de acreditación del protocolo.");seedDefaultFields(p,accreditation);return dto(p);
    }

    @PUT
    @Path("/protocols/{protocolId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public ProtocolDto save(@PathParam("protocolId") UUID protocolId,SaveProtocolRequest request){
        LogbookProtocol p=protocol(protocolId);authorize(p);requireDraft(p);if(request==null)throw new BadRequestException("Solicitud obligatoria");if(request.name()==null||request.name().isBlank())throw new BadRequestException("El nombre del protocolo es obligatorio");p.name=request.name().trim();p.objectType=normalize(request.objectType(),OBJECT_TYPES,p.objectType,"Objeto inválido");p.applicationType=normalize(request.applicationType(),APPLICATION_TYPES,p.applicationType,"Aplicación inválida");p.description=request.description()==null?"":request.description().trim();p.status="BORRADOR";p.updatedByUsername=user();setScope(p,request.applicablePostIds());return dto(p);
    }


    @PUT
    @Path("/protocols/{protocolId}/scope")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public ProtocolDto saveScope(@PathParam("protocolId") UUID protocolId,SaveScopeRequest request){
        LogbookProtocol p=protocol(protocolId);authorize(p);
        if(request==null)throw new BadRequestException("Solicitud obligatoria");
        setScope(p,request.applicablePostIds());
        if(!"BORRADOR".equals(p.status)){
            if(scopeIds(p).isEmpty())p.status="INACTIVO";
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
        LogbookProtocol source=protocol(protocolId);authorize(source);LogbookProtocol existing=LogbookProtocol.find("instanceCountryId=?1 and seriesId=?2 and status='BORRADOR'",tenant.instanceCountryId(),source.seriesId).firstResult();if(existing!=null)return dto(existing);
        LogbookProtocol target=new LogbookProtocol();target.id=UUID.randomUUID();target.instanceCountryId=tenant.instanceCountryId();target.seriesId=source.seriesId;target.basedOnProtocolId=source.id;target.postId=source.postId;target.code=source.code;target.name=source.name;target.objectType=source.objectType;target.applicationType=source.applicationType;target.description=source.description;target.status="BORRADOR";target.versionNo=source.versionNo+1;target.identificationLogic=source.identificationLogic;target.verificationLogic=source.verificationLogic;target.authPreapproval=source.authPreapproval;target.authClient=source.authClient;target.authSupervisor=source.authSupervisor;target.whiteListEnabled=source.whiteListEnabled;target.blackListEnabled=source.blackListEnabled;target.captureManual=source.captureManual;target.captureQr=source.captureQr;target.captureBarcode=source.captureBarcode;target.captureNfc=source.captureNfc;target.captureAutomatic=source.captureAutomatic;target.sourceModelType=source.sourceModelType;target.sourceModelName=source.sourceModelName;target.updatedByUsername=user();target.persist();
        setScope(target,scopeIds(source));
        for(LogbookAccreditation old:accreditations(source.id)){LogbookAccreditation copy=cloneAccreditation(old,target.id);for(LogbookProtocolField field:fields(old.id))cloneField(field,target.id,copy.id);}
        return dto(target);
    }

    @POST
    @Path("/protocols/{protocolId}/publish")
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public ProtocolDto publish(@PathParam("protocolId") UUID protocolId){
        LogbookProtocol p=protocol(protocolId);authorize(p);requireDraft(p);List<LogbookAccreditation> accreditations=accreditations(p.id);if(accreditations.isEmpty())throw new BadRequestException("El protocolo debe tener al menos una acreditación");if(accreditations.size()>10)throw new BadRequestException("El protocolo no puede contener más de 10 acreditaciones");for(LogbookAccreditation accreditation:accreditations){long identification=LogbookProtocolField.count("accreditationId=?1 and instanceCountryId=?2 and section='IDENTIFICACION'",accreditation.id,tenant.instanceCountryId());if(identification==0)throw new BadRequestException("La acreditación "+accreditation.code+" debe tener al menos un campo de identificación");for(LogbookProtocolField field:fields(accreditation.id))if(field.visintEnabled&&StandardReferenceImage.countOf(StandardReferenceImage.LOGBOOK_FIELD,field.id)==0)throw new BadRequestException(accreditation.code+": el campo \""+field.name+"\" requiere al menos una foto estándar (VISINT)");}
        if(scopeIds(p).isEmpty())p.status="INACTIVO";else activateVersion(p);p.lastPublishedAt=Instant.now();p.updatedByUsername=user();return dto(p);
    }

    @POST
    @Path("/protocols/{protocolId}/activate")
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public ProtocolDto activate(@PathParam("protocolId") UUID protocolId){
        LogbookProtocol p=protocol(protocolId);authorize(p);if(!"INACTIVO".equals(p.status)&&!"ACTIVO".equals(p.status))throw new ClientErrorException("Solo un Protocolo inactivo puede activarse.",409);if("ACTIVO".equals(p.status))return dto(p);
        List<LogbookProtocol> active=LogbookProtocol.list("instanceCountryId=?1 and seriesId=?2 and status='ACTIVO'",tenant.instanceCountryId(),p.seriesId);for(LogbookProtocol previous:active)previous.status="INACTIVO";p.status="ACTIVO";p.updatedByUsername=user();return dto(p);
    }

    @POST
    @Path("/protocols/{protocolId}/deactivate")
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public ProtocolDto deactivate(@PathParam("protocolId") UUID protocolId){
        LogbookProtocol p=protocol(protocolId);authorize(p);if(!"ACTIVO".equals(p.status))throw new ClientErrorException("Solo un Protocolo activo puede inactivarse.",409);p.status="INACTIVO";p.updatedByUsername=user();return dto(p);
    }

    @POST
    @Path("/protocols/{protocolId}/accreditations")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public AccreditationDto createAccreditation(@PathParam("protocolId") UUID protocolId,CreateAccreditationRequest request){LogbookProtocol p=protocol(protocolId);authorize(p);requireDraft(p);long count=LogbookAccreditation.count("protocolId=?1 and instanceCountryId=?2",p.id,tenant.instanceCountryId());if(count>=10)throw new BadRequestException("Este Protocolo ya alcanzó el máximo de 10 acreditaciones. Para continuar, crea un nuevo Protocolo.");String name=cleanOrDefault(request==null?null:request.name(),"Nueva acreditación");String description=request==null||request.description()==null?"":request.description().trim();LogbookAccreditation accreditation=createDefaultAccreditation(p,name,description);if("PAX".equals(p.objectType))seedDefaultFields(p,accreditation);p.updatedByUsername=user();return accreditationDto(accreditation);}

    @PUT
    @Path("/accreditations/{accreditationId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public AccreditationDto saveAccreditation(@PathParam("accreditationId") UUID accreditationId,SaveAccreditationRequest request){LogbookAccreditation accreditation=accreditation(accreditationId);LogbookProtocol p=protocol(accreditation.protocolId);authorize(p);requireDraft(p);if(request==null)throw new BadRequestException("Solicitud obligatoria");if(request.name()==null||request.name().isBlank())throw new BadRequestException("El nombre de la acreditación es obligatorio");accreditation.name=request.name().trim();accreditation.description=request.description()==null?"":request.description().trim();accreditation.identificationLogic=normalize(request.identificationLogic(),LOGICS,accreditation.identificationLogic,"Lógica de identificación inválida");accreditation.verificationLogic=normalize(request.verificationLogic(),LOGICS,accreditation.verificationLogic,"Lógica de verificación inválida");accreditation.authPreapproval=request.authPreapproval();accreditation.authClient=request.authClient();accreditation.authSupervisor=request.authSupervisor();accreditation.whiteListEnabled=request.whiteListEnabled();accreditation.blackListEnabled=request.blackListEnabled();accreditation.captureManual=true;accreditation.captureQr=request.captureQr();accreditation.captureBarcode=request.captureBarcode();accreditation.captureNfc=request.captureNfc();accreditation.captureAutomatic=request.captureAutomatic();p.updatedByUsername=user();return accreditationDto(accreditation);}

    @DELETE
    @Path("/accreditations/{accreditationId}")
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public void deleteAccreditation(@PathParam("accreditationId") UUID accreditationId){LogbookAccreditation a=accreditation(accreditationId);LogbookProtocol p=protocol(a.protocolId);authorize(p);requireDraft(p);if(LogbookAccreditation.count("protocolId=?1 and instanceCountryId=?2",p.id,tenant.instanceCountryId())<=1)throw new BadRequestException("El protocolo debe conservar al menos una acreditación");a.delete();p.updatedByUsername=user();}

    @POST
    @Path("/accreditations/{accreditationId}/fields")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public FieldDto createField(@PathParam("accreditationId") UUID accreditationId,SaveFieldRequest request){LogbookAccreditation a=accreditation(accreditationId);LogbookProtocol p=protocol(a.protocolId);authorize(p);requireDraft(p);LogbookProtocolField field=new LogbookProtocolField();field.instanceCountryId=tenant.instanceCountryId();field.protocolId=p.id;field.accreditationId=a.id;apply(field,request,true);field.persist();p.updatedByUsername=user();return fieldDto(field);}

    @PUT
    @Path("/fields/{fieldId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public FieldDto saveField(@PathParam("fieldId") UUID fieldId,SaveFieldRequest request){LogbookProtocolField field=field(fieldId);LogbookProtocol p=protocol(field.protocolId);authorize(p);requireDraft(p);apply(field,request,false);p.updatedByUsername=user();return fieldDto(field);}

    @DELETE
    @Path("/fields/{fieldId}")
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public void deleteField(@PathParam("fieldId") UUID fieldId){LogbookProtocolField f=field(fieldId);LogbookProtocol p=protocol(f.protocolId);authorize(p);requireDraft(p);StandardReferenceImage.delete("targetType=?1 and targetId=?2",StandardReferenceImage.LOGBOOK_FIELD,f.id);f.delete();p.updatedByUsername=user();}

    /** Agrega una foto estándar al campo (hasta 5). En campos tipo DOCUMENTO, VISINT las recibe en referenceImages. */
    @POST
    @Path("/fields/{fieldId}/standard-images")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public FieldDto addStandardImage(@PathParam("fieldId") UUID fieldId,@org.jboss.resteasy.reactive.RestForm("file") org.jboss.resteasy.reactive.multipart.FileUpload file){LogbookProtocolField field=field(fieldId);LogbookProtocol p=protocol(field.protocolId);authorize(p);requireDraft(p);references.add(tenant.instanceCountryId(),StandardReferenceImage.LOGBOOK_FIELD,field.id,"bitacora",file);field.standardImageVersion++;p.updatedByUsername=user();return fieldDto(field);}

    @GET
    @Path("/fields/{fieldId}/standard-images/{imageId}")
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
    public Response standardImage(@PathParam("fieldId") UUID fieldId,@PathParam("imageId") UUID imageId){LogbookProtocolField field=field(fieldId);authorize(protocol(field.protocolId));StandardReferenceImage img=references.get(StandardReferenceImage.LOGBOOK_FIELD,field.id,imageId);return Response.ok(references.read(img)).type(img.contentType).header(HttpHeaders.CACHE_CONTROL,"no-store").build();}

    @DELETE
    @Path("/fields/{fieldId}/standard-images/{imageId}")
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public FieldDto deleteStandardImage(@PathParam("fieldId") UUID fieldId,@PathParam("imageId") UUID imageId){LogbookProtocolField field=field(fieldId);LogbookProtocol p=protocol(field.protocolId);authorize(p);requireDraft(p);references.delete(StandardReferenceImage.LOGBOOK_FIELD,field.id,imageId);field.standardImageVersion++;p.updatedByUsername=user();return fieldDto(field);}

    private LogbookAccreditation cloneAccreditation(LogbookAccreditation old,UUID protocolId){LogbookAccreditation a=new LogbookAccreditation();a.id=UUID.randomUUID();a.instanceCountryId=tenant.instanceCountryId();a.protocolId=protocolId;a.code=old.code;a.name=old.name;a.description=old.description;a.identificationLogic=old.identificationLogic;a.verificationLogic=old.verificationLogic;a.authPreapproval=old.authPreapproval;a.authClient=old.authClient;a.authSupervisor=old.authSupervisor;a.whiteListEnabled=old.whiteListEnabled;a.blackListEnabled=old.blackListEnabled;a.captureManual=old.captureManual;a.captureQr=old.captureQr;a.captureBarcode=old.captureBarcode;a.captureNfc=old.captureNfc;a.captureAutomatic=old.captureAutomatic;a.persist();return a;}
    private void cloneField(LogbookProtocolField old,UUID protocolId,UUID accreditationId){LogbookProtocolField f=new LogbookProtocolField();f.instanceCountryId=tenant.instanceCountryId();f.protocolId=protocolId;f.accreditationId=accreditationId;f.section=old.section;f.sortOrder=old.sortOrder;f.name=old.name;f.description=old.description;f.fieldType=old.fieldType;f.required=old.required;f.evidenceRequired=old.evidenceRequired;f.captureMode=old.captureMode;f.customField=old.customField;f.standardImageOriginalName=old.standardImageOriginalName;f.standardImageContentType=old.standardImageContentType;f.standardImageData=old.standardImageData==null?null:Arrays.copyOf(old.standardImageData,old.standardImageData.length);f.standardImageObjectKey=old.standardImageObjectKey;f.standardImageSha256=old.standardImageSha256;f.standardImageSize=old.standardImageSize;f.standardImageVersion=old.standardImageVersion;f.standardImageNotes=old.standardImageNotes;f.visintEnabled=old.visintEnabled;f.persist();references.copy(StandardReferenceImage.LOGBOOK_FIELD,old.id,f.id);}
    private void apply(LogbookProtocolField field,SaveFieldRequest request,boolean creating){if(request==null)throw new BadRequestException("Solicitud obligatoria");String section=normalize(request.section(),SECTIONS,creating?"IDENTIFICACION":field.section,"Sección inválida");String name=request.name()==null?"":request.name().trim();if(name.isBlank())throw new BadRequestException("El nombre del campo es obligatorio");if(name.length()>160)throw new BadRequestException("El nombre del campo no puede superar 160 caracteres");field.section=section;field.sortOrder=request.sortOrder()==null?nextSort(field.accreditationId,section):Math.max(1,request.sortOrder());field.name=name;field.description=request.description()==null?"":request.description().trim();field.fieldType=normalize(request.fieldType(),FIELD_TYPES,creating?"TEXTO":field.fieldType,"Tipo de campo inválido");field.required=request.required();field.evidenceRequired=request.evidenceRequired();field.captureMode=normalize(request.captureMode(),CAPTURE_MODES,creating?"MANUAL":field.captureMode,"Modo de captura inválido");field.customField=request.customField();field.standardImageNotes=request.standardImageNotes()==null?"":request.standardImageNotes().trim();if(creating)field.standardImageVersion=0;field.visintEnabled=visint(field);}
    private LogbookAccreditation createDefaultAccreditation(LogbookProtocol p,String name,String description){LogbookAccreditation a=new LogbookAccreditation();a.instanceCountryId=tenant.instanceCountryId();a.protocolId=p.id;a.code=nextAccreditationCode(p.id);a.name=name;a.description=description;a.identificationLogic="ALL";a.verificationLogic="ALL";a.authPreapproval=true;a.authClient=true;a.authSupervisor=false;a.whiteListEnabled=true;a.blackListEnabled=true;a.captureManual=true;a.captureQr=true;a.captureBarcode=false;a.captureNfc=false;a.captureAutomatic=false;a.persist();return a;}
    private void seedDefaultFields(LogbookProtocol p,LogbookAccreditation a){if(!"PAX".equals(p.objectType))return;createSeedField(p,a,"IDENTIFICACION",1,"Cédula","Documento oficial de identidad.","DOCUMENTO",true,true,"MANUAL_QR",false);createSeedField(p,a,"IDENTIFICACION",2,"Pasaporte","Documento de viaje cuando aplique.","DOCUMENTO",false,true,"MANUAL",false);createSeedField(p,a,"IDENTIFICACION",3,"Credencial","Credencial emitida por el cliente o tercero autorizado.","DOCUMENTO",true,true,"MANUAL_QR",false);createSeedField(p,a,"IDENTIFICACION",4,"Rostro","Fotografía frontal de la persona.","IMAGEN",true,true,"CAMARA",false);createSeedField(p,a,"VERIFICACION",1,"Persona en lista autorizada","Validar que la persona conste en la lista aplicable.","TEXTO",true,false,"MANUAL",false);createSeedField(p,a,"VERIFICACION",2,"Empresa","Empresa u organización a la que pertenece.","TEXTO",true,false,"MANUAL",false);createSeedField(p,a,"VERIFICACION",3,"Motivo de visita","Motivo declarado y validado del ingreso.","TEXTO",true,false,"MANUAL",false);createSeedField(p,a,"VERIFICACION",4,"Persona anfitriona","Persona responsable de recibir al visitante.","TEXTO",true,false,"MANUAL",false);}
    private void createSeedField(LogbookProtocol p,LogbookAccreditation a,String section,int order,String name,String description,String type,boolean required,boolean evidence,String capture,boolean custom){LogbookProtocolField f=new LogbookProtocolField();f.instanceCountryId=tenant.instanceCountryId();f.protocolId=p.id;f.accreditationId=a.id;f.section=section;f.sortOrder=order;f.name=name;f.description=description;f.fieldType=type;f.required=required;f.evidenceRequired=evidence;f.captureMode=capture;f.customField=custom;f.standardImageVersion=0;f.standardImageNotes="";f.visintEnabled=visint(f);f.persist();}

    private void activateVersion(LogbookProtocol p){
        List<LogbookProtocol> active=LogbookProtocol.list("instanceCountryId=?1 and seriesId=?2 and status='ACTIVO' and id<>?3",tenant.instanceCountryId(),p.seriesId,p.id);
        for(LogbookProtocol previous:active){
            previous.status="INACTIVO";
            previous.updatedByUsername=user();
            LogbookProtocolPostScope.delete("protocolId=?1 and instanceCountryId=?2",previous.id,tenant.instanceCountryId());
        }
        p.status="ACTIVO";
    }

    /** VISINT valida los campos tipo DOCUMENTO con evidencia (Cédula, Pasaporte, Credencial…). Rostro (IMAGEN) no: reconocimiento facial fuera de alcance. */
    private static boolean visint(LogbookProtocolField f){return f.evidenceRequired&&"DOCUMENTO".equals(f.fieldType);}
    private int rank(LogbookProtocol p){return "BORRADOR".equals(p.status)?2:1;}
    private void requireDraft(LogbookProtocol p){if(!"BORRADOR".equals(p.status))throw new ClientErrorException("La versión publicada es inmutable. Cree una nueva versión en borrador para editar.",409);}
    private int nextSort(UUID accreditationId,String section){List<LogbookProtocolField> rows=LogbookProtocolField.list("accreditationId=?1 and instanceCountryId=?2 and section=?3 order by sortOrder desc",accreditationId,tenant.instanceCountryId(),section);return rows.isEmpty()?1:rows.get(0).sortOrder+1;}
    private String nextProtocolCode(){List<LogbookProtocol> all=LogbookProtocol.list("instanceCountryId=?1",tenant.instanceCountryId());long count=all.stream().map(p->p.seriesId).distinct().count();return String.format(Locale.ROOT,"PRO-BA-%04d",count+1);}
    private String nextAccreditationCode(UUID protocolId){
        // Codes are identifiers, not a live-count. If an accreditation is deleted from a
        // draft, reusing count+1 can collide with an existing code (e.g. 7 rows while
        // ACC-010 still exists => count+1 would incorrectly try ACC-008).
        // Always advance from the highest numeric suffix already used in this protocol.
        int max=0;
        List<LogbookAccreditation> rows=LogbookAccreditation.list(
            "protocolId=?1 and instanceCountryId=?2",protocolId,tenant.instanceCountryId());
        for(LogbookAccreditation row:rows){
            if(row.code==null)continue;
            String code=row.code.trim().toUpperCase(Locale.ROOT);
            if(!code.startsWith("ACC-"))continue;
            try{max=Math.max(max,Integer.parseInt(code.substring(4)));}
            catch(NumberFormatException ignored){/* Imported/non-canonical code: do not reuse it. */}
        }
        return String.format(Locale.ROOT,"ACC-%03d",max+1);
    }
    private String cleanOrDefault(String value,String fallback){return value==null||value.isBlank()?fallback:value.trim();}
    private String normalize(String value,Set<String> allowed,String fallback,String message){String v=value==null||value.isBlank()?fallback:value.trim().toUpperCase(Locale.ROOT);if(v==null||!allowed.contains(v))throw new BadRequestException(message);return v;}
    private String user(){return identity.getPrincipal().getName();}
    private ProtocolDto dto(LogbookProtocol p){return new ProtocolDto(p.id,p.seriesId,p.basedOnProtocolId,p.postId,p.code,p.name,p.objectType,p.applicationType,p.description,p.status,p.versionNo,p.sourceModelType,p.sourceModelName,p.lastPublishedAt,p.updatedByUsername,scopeIds(p),accreditations(p.id).stream().map(this::accreditationDto).toList());}
    private AccreditationDto accreditationDto(LogbookAccreditation a){List<LogbookProtocolField> fields=fields(a.id);return new AccreditationDto(a.id,a.protocolId,a.code,a.name,a.description,a.identificationLogic,a.verificationLogic,a.authPreapproval,a.authClient,a.authSupervisor,a.whiteListEnabled,a.blackListEnabled,a.captureManual,a.captureQr,a.captureBarcode,a.captureNfc,a.captureAutomatic,fields.stream().map(this::fieldDto).toList());}
    private FieldDto fieldDto(LogbookProtocolField f){return new FieldDto(f.id,f.accreditationId,f.section,f.sortOrder,f.name,f.description,f.fieldType,f.required,f.evidenceRequired,f.captureMode,f.customField,StandardReferenceImage.countOf(StandardReferenceImage.LOGBOOK_FIELD,f.id)>0,references.dtos(StandardReferenceImage.LOGBOOK_FIELD,f.id),f.standardImageVersion,f.standardImageNotes,f.visintEnabled);}
    private List<UUID> scopeIds(LogbookProtocol p){
        return LogbookProtocolPostScope.<LogbookProtocolPostScope>list("instanceCountryId=?1 and protocolId=?2 order by createdAt",tenant.instanceCountryId(),p.id).stream().map(x->x.postId).toList();
    }
    private void setScope(LogbookProtocol p,List<UUID> postIds){
        PostEntity anchorPost=post(p.postId);
        LinkedHashSet<UUID> unique=new LinkedHashSet<>();
        if(postIds!=null)unique.addAll(postIds);
        LogbookProtocolPostScope.delete("protocolId=?1 and instanceCountryId=?2",p.id,tenant.instanceCountryId());
        for(UUID id:unique){
            PostEntity candidate=post(id);
            if(!candidate.pointId.equals(anchorPost.pointId))throw new BadRequestException("Todos los Puestos del alcance deben pertenecer al mismo Punto");
            LogbookProtocolPostScope scopeRow=new LogbookProtocolPostScope();
            scopeRow.instanceCountryId=tenant.instanceCountryId();
            scopeRow.protocolId=p.id;
            scopeRow.postId=id;
            scopeRow.persist();
        }
    }
    private List<LogbookAccreditation> accreditations(UUID protocolId){return LogbookAccreditation.list("protocolId=?1 and instanceCountryId=?2 order by code",protocolId,tenant.instanceCountryId());}
    private List<LogbookProtocolField> fields(UUID accreditationId){return LogbookProtocolField.list("accreditationId=?1 and instanceCountryId=?2 order by section,sortOrder,name",accreditationId,tenant.instanceCountryId());}
    private void authorize(LogbookProtocol p){PostEntity post=post(p.postId);PointEntity point=point(post.pointId);scope.requireCompany(point.companyId);}
    private PointEntity point(UUID id){if(id==null)throw new BadRequestException("pointId obligatorio");PointEntity p=PointEntity.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(p==null)throw new NotFoundException("Punto no encontrado");return p;}
    private PostEntity post(UUID id){PostEntity p=PostEntity.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(p==null)throw new NotFoundException("Puesto no encontrado");return p;}
    private LogbookProtocol protocol(UUID id){LogbookProtocol p=LogbookProtocol.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(p==null)throw new NotFoundException("Protocolo no encontrado");return p;}
    private LogbookAccreditation accreditation(UUID id){LogbookAccreditation a=LogbookAccreditation.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(a==null)throw new NotFoundException("Acreditación no encontrada");return a;}
    private LogbookProtocolField field(UUID id){LogbookProtocolField f=LogbookProtocolField.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(f==null)throw new NotFoundException("Campo no encontrado");return f;}
    private String safeFilename(String value){return value==null?"estandar":value.replaceAll("[^A-Za-z0-9._-]","_");}
}
