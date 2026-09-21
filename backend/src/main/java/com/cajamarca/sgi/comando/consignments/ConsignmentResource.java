package com.cajamarca.sgi.comando.consignments;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.operations.PostEntity;
import com.cajamarca.sgi.comando.outbox.OutboxEvent;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;

import java.time.*;
import java.util.*;

@Path("/api/consignments")
@Produces(MediaType.APPLICATION_JSON)
public class ConsignmentResource {
 private static final Set<String> PROTOCOL_STATUS=Set.of("BORRADOR","PUBLICADO","VIGENTE","NO_VIGENTE");
 private static final Set<String> PRIORITIES=Set.of("LOW","MEDIUM","HIGH","CRITICAL");
 private static final Set<String> SCOPES=Set.of("POINT","POSTS");
 private static final Set<String> VALIDITIES=Set.of("PERMANENT","TEMPORARY");
 private static final Set<String> APPLICATIONS=Set.of("ALL_TIME","CALENDAR");
 private static final Set<String> LOCATION_MODES=Set.of("NONE","ATS","GPS");
 private static final Set<String> EVIDENCE_TYPES=Set.of("PHOTO","DOCUMENT","TEXT","CONFIRMATION");
 private static final Set<String> IMAGE_TYPES=Set.of("image/jpeg","image/png","image/webp");
 private static final long MAX_IMAGE=5L*1024*1024;

 @Inject TenantContext tenant;
 @Inject OperationalScopeService scope;
 @Inject SecurityIdentity identity;
 @Inject ObjectMapper mapper;

 public record EvidenceDto(UUID id,UUID consignmentId,int sortOrder,String name,String description,String evidenceType,boolean required,boolean hasStandardImage,String standardImageOriginalName,int standardImageVersion,String standardImageNotes,boolean visintEnabled){}
 public record ConsignmentDto(UUID id,UUID protocolId,String code,String title,String instruction,String priority,String status,String scopeType,List<UUID> postIds,String validityType,Instant validityFrom,Instant validityUntil,String applicationType,String applicationDaysJson,String applicationTimeFrom,String applicationTimeTo,boolean acknowledgmentRequired,boolean confirmationRequired,boolean evidenceRequired,boolean gpsRequired,boolean observationRequired,String expectedLocationMode,UUID atsPackageId,Double atsX,Double atsY,Double expectedLatitude,Double expectedLongitude,Instant publishedAt,String updatedBy,List<EvidenceDto> evidences){}
 public record ProtocolDto(UUID id,UUID seriesId,UUID basedOnProtocolId,UUID pointId,String code,String name,String description,String status,int versionNo,Instant publishedAt,Instant activatedAt,String updatedBy,List<ConsignmentDto> consignments){}
 public record CreateProtocolRequest(UUID pointId,String name){}
 public record SaveProtocolRequest(String name,String description){}
 public record CreateConsignmentRequest(String title){}
 public record SaveConsignmentRequest(String title,String instruction,String priority,String scopeType,List<UUID> postIds,String validityType,Instant validityFrom,Instant validityUntil,String applicationType,String applicationDaysJson,String applicationTimeFrom,String applicationTimeTo,Boolean acknowledgmentRequired,Boolean confirmationRequired,Boolean evidenceRequired,Boolean gpsRequired,Boolean observationRequired,String expectedLocationMode,UUID atsPackageId,Double atsX,Double atsY,Double expectedLatitude,Double expectedLongitude){}
 public record CreateEvidenceRequest(String name,String description,String evidenceType,Boolean required){}
 public record SaveEvidenceRequest(String name,String description,String evidenceType,boolean required,String standardImageNotes){}

 @GET
 @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
 public List<Consignment> legacyList(@QueryParam("pointId") UUID pointId){PointEntity point=point(pointId);scope.requireCompany(point.companyId);return Consignment.list("pointId=?1 and instanceCountryId=?2 order by createdAt desc",pointId,tenant.instanceCountryId());}

 @GET
 @Path("/protocols")
 @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
 public List<ProtocolDto> protocols(@QueryParam("pointId") UUID pointId){
   PointEntity point=point(pointId);scope.requireCompany(point.companyId);
   List<ConsignmentProtocol> all=ConsignmentProtocol.list("instanceCountryId=?1 and pointId=?2 order by code,versionNo desc",tenant.instanceCountryId(),pointId);
   Map<UUID,ConsignmentProtocol> latest=new LinkedHashMap<>();
   for(ConsignmentProtocol p:all){ConsignmentProtocol current=latest.get(p.seriesId);if(current==null||rank(p)>rank(current)||(rank(p)==rank(current)&&p.versionNo>current.versionNo))latest.put(p.seriesId,p);}
   return latest.values().stream().sorted(Comparator.comparing(p->p.code)).map(this::dto).toList();
 }

 @GET
 @Path("/protocols/current")
 @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
 public ProtocolDto current(@QueryParam("pointId") UUID pointId){PointEntity point=point(pointId);scope.requireCompany(point.companyId);ConsignmentProtocol p=ConsignmentProtocol.find("instanceCountryId=?1 and pointId=?2 and status='VIGENTE'",tenant.instanceCountryId(),pointId).firstResult();return p==null?null:dto(p);}

 @GET
 @Path("/protocols/{id}/history")
 @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
 public List<ProtocolDto> history(@PathParam("id") UUID id){ConsignmentProtocol p=protocol(id);authorize(p);return ConsignmentProtocol.<ConsignmentProtocol>list("instanceCountryId=?1 and seriesId=?2 order by versionNo desc",tenant.instanceCountryId(),p.seriesId).stream().map(this::dto).toList();}

 @POST
 @Path("/protocols")
 @Consumes(MediaType.APPLICATION_JSON)
 @Transactional
 @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public ProtocolDto createProtocol(CreateProtocolRequest req){
   if(req==null||req.pointId()==null)throw new BadRequestException("Punto obligatorio");PointEntity point=point(req.pointId());scope.requireCompany(point.companyId);
   ConsignmentProtocol p=new ConsignmentProtocol();p.id=UUID.randomUUID();p.instanceCountryId=tenant.instanceCountryId();p.seriesId=p.id;p.pointId=point.id;p.code=nextProtocolCode();p.name=clean(req.name(),"Nuevo protocolo de consignas");p.description="";p.status="BORRADOR";p.versionNo=1;p.updatedByUsername=user();p.persist();return dto(p);
 }

 @PUT
 @Path("/protocols/{id}")
 @Consumes(MediaType.APPLICATION_JSON)
 @Transactional
 @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public ProtocolDto saveProtocol(@PathParam("id")UUID id,SaveProtocolRequest req){ConsignmentProtocol p=protocol(id);authorize(p);requireDraft(p);if(req==null)throw new BadRequestException("Solicitud obligatoria");p.name=clean(req.name(),p.name);p.description=req.description()==null?"":req.description().trim();p.updatedByUsername=user();return dto(p);}

 @POST
 @Path("/protocols/{id}/fork")
 @Transactional
 @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public ProtocolDto fork(@PathParam("id")UUID id){
   ConsignmentProtocol source=protocol(id);authorize(source);ConsignmentProtocol existing=ConsignmentProtocol.find("instanceCountryId=?1 and seriesId=?2 and status='BORRADOR'",tenant.instanceCountryId(),source.seriesId).firstResult();if(existing!=null)return dto(existing);
   ConsignmentProtocol target=new ConsignmentProtocol();target.id=UUID.randomUUID();target.instanceCountryId=tenant.instanceCountryId();target.seriesId=source.seriesId;target.basedOnProtocolId=source.id;target.pointId=source.pointId;target.code=source.code;target.name=source.name;target.description=source.description;target.status="BORRADOR";target.versionNo=source.versionNo+1;target.updatedByUsername=user();target.persist();
   for(Consignment old:consignments(source.id))cloneConsignment(old,target.id);
   return dto(target);
 }

 @POST
 @Path("/protocols/{id}/publish")
 @Transactional
 @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public ProtocolDto publish(@PathParam("id")UUID id) throws Exception{
   ConsignmentProtocol p=protocol(id);authorize(p);requireDraft(p);List<Consignment> items=consignments(p.id);if(items.isEmpty())throw new BadRequestException("El protocolo debe contener al menos una Consigna");for(Consignment c:items)validate(c);
   ConsignmentProtocol sameActive=ConsignmentProtocol.find("instanceCountryId=?1 and pointId=?2 and seriesId=?3 and status='VIGENTE'",tenant.instanceCountryId(),p.pointId,p.seriesId).firstResult();
   List<ConsignmentProtocol> oldSeries=ConsignmentProtocol.list("instanceCountryId=?1 and seriesId=?2 and id<>?3 and status<>'BORRADOR'",tenant.instanceCountryId(),p.seriesId,p.id);for(ConsignmentProtocol old:oldSeries)if(!"VIGENTE".equals(old.status))old.status="NO_VIGENTE";
   p.publishedAt=Instant.now();p.updatedByUsername=user();
   if(sameActive!=null){ConsignmentProtocol.update("status='NO_VIGENTE' where id=?1 and instanceCountryId=?2",sameActive.id,tenant.instanceCountryId());p.status="VIGENTE";p.activatedAt=Instant.now();}else{ConsignmentProtocol anyActive=ConsignmentProtocol.find("instanceCountryId=?1 and pointId=?2 and status='VIGENTE'",tenant.instanceCountryId(),p.pointId).firstResult();if(anyActive==null){p.status="VIGENTE";p.activatedAt=Instant.now();}else p.status="PUBLICADO";}
   String itemStatus="VIGENTE".equals(p.status)?"VIGENTE":"PUBLICADO";for(Consignment c:items){c.status=itemStatus;c.publishedAt=p.publishedAt;}
   OutboxEvent.of(tenant.instanceCountryId(),"CONSIGNMENT_PROTOCOL",p.id,"CONSIGNMENT_PROTOCOL_PUBLISHED",mapper.writeValueAsString(Map.of("protocolId",p.id,"pointId",p.pointId,"code",p.code,"version",p.versionNo,"status",p.status))).persist();
   return dto(p);
 }

 @POST
 @Path("/protocols/{id}/activate")
 @Transactional
 @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public ProtocolDto activate(@PathParam("id")UUID id){
   ConsignmentProtocol p=protocol(id);authorize(p);if(!"PUBLICADO".equals(p.status)&&!"VIGENTE".equals(p.status))throw new ClientErrorException("Solo un Protocolo publicado puede activarse.",409);if("VIGENTE".equals(p.status))return dto(p);
   List<ConsignmentProtocol> active=ConsignmentProtocol.list("instanceCountryId=?1 and pointId=?2 and status='VIGENTE'",tenant.instanceCountryId(),p.pointId);for(ConsignmentProtocol old:active){ConsignmentProtocol.update("status='PUBLICADO' where id=?1 and instanceCountryId=?2",old.id,tenant.instanceCountryId());for(Consignment c:consignments(old.id))c.status="PUBLICADO";}
   p.status="VIGENTE";p.activatedAt=Instant.now();p.updatedByUsername=user();for(Consignment c:consignments(p.id))c.status="VIGENTE";return dto(p);
 }

 @POST
 @Path("/protocols/{protocolId}/items")
 @Consumes(MediaType.APPLICATION_JSON)
 @Transactional
 @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public ConsignmentDto createConsignment(@PathParam("protocolId")UUID protocolId,CreateConsignmentRequest req){
   ConsignmentProtocol p=protocol(protocolId);authorize(p);requireDraft(p);Consignment c=new Consignment();c.instanceCountryId=tenant.instanceCountryId();c.protocolId=p.id;c.pointId=p.pointId;c.code=nextConsignmentCode();c.title=clean(req==null?null:req.title(),"Nueva consigna");c.instruction="";c.priority="MEDIUM";c.status="BORRADOR";c.scopeType="POINT";c.validityType="PERMANENT";c.applicationType="ALL_TIME";c.applicationDaysJson="[]";c.acknowledgmentRequired=false;c.confirmationRequired=true;c.evidenceRequired=false;c.gpsRequired=false;c.observationRequired=false;c.expectedLocationMode="NONE";c.updatedByUsername=user();c.persist();return itemDto(c);
 }

 @PUT
 @Path("/items/{id}")
 @Consumes(MediaType.APPLICATION_JSON)
 @Transactional
 @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public ConsignmentDto saveConsignment(@PathParam("id")UUID id,SaveConsignmentRequest req){
   Consignment c=item(id);ConsignmentProtocol p=protocol(c.protocolId);authorize(p);requireDraft(p);if(req==null)throw new BadRequestException("Solicitud obligatoria");
   c.title=clean(req.title(),c.title);c.instruction=req.instruction()==null?"":req.instruction().trim();c.priority=norm(req.priority(),PRIORITIES,c.priority,"Prioridad inválida");c.scopeType=norm(req.scopeType(),SCOPES,c.scopeType,"Alcance inválido");c.validityType=norm(req.validityType(),VALIDITIES,c.validityType,"Vigencia inválida");c.validityFrom=req.validityFrom();c.validityUntil=req.validityUntil();c.applicationType=norm(req.applicationType(),APPLICATIONS,c.applicationType,"Aplicación inválida");c.applicationDaysJson=req.applicationDaysJson()==null?"[]":req.applicationDaysJson();c.applicationTimeFrom=parseTime(req.applicationTimeFrom());c.applicationTimeTo=parseTime(req.applicationTimeTo());c.acknowledgmentRequired=bool(req.acknowledgmentRequired(),c.acknowledgmentRequired);c.confirmationRequired=bool(req.confirmationRequired(),c.confirmationRequired);c.evidenceRequired=bool(req.evidenceRequired(),c.evidenceRequired);c.gpsRequired=bool(req.gpsRequired(),c.gpsRequired);c.observationRequired=bool(req.observationRequired(),c.observationRequired);c.expectedLocationMode=norm(req.expectedLocationMode(),LOCATION_MODES,c.expectedLocationMode,"Ubicación inválida");c.atsPackageId=req.atsPackageId();c.atsX=req.atsX();c.atsY=req.atsY();c.expectedLatitude=req.expectedLatitude();c.expectedLongitude=req.expectedLongitude();c.updatedByUsername=user();
   setScope(c,req.postIds());validate(c);return itemDto(c);
 }

 @DELETE
 @Path("/items/{id}")
 @Transactional
 @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public void deleteConsignment(@PathParam("id")UUID id){Consignment c=item(id);ConsignmentProtocol p=protocol(c.protocolId);authorize(p);requireDraft(p);ConsignmentEvidence.delete("consignmentId=?1 and instanceCountryId=?2",c.id,tenant.instanceCountryId());ConsignmentPostScope.delete("consignmentId=?1 and instanceCountryId=?2",c.id,tenant.instanceCountryId());c.delete();}

 @POST
 @Path("/items/{id}/evidences")
 @Consumes(MediaType.APPLICATION_JSON)
 @Transactional
 @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public EvidenceDto createEvidence(@PathParam("id")UUID id,CreateEvidenceRequest req){Consignment c=item(id);ConsignmentProtocol p=protocol(c.protocolId);authorize(p);requireDraft(p);ConsignmentEvidence e=new ConsignmentEvidence();e.instanceCountryId=tenant.instanceCountryId();e.consignmentId=c.id;e.sortOrder=(int)ConsignmentEvidence.count("consignmentId=?1 and instanceCountryId=?2",c.id,tenant.instanceCountryId())+1;e.name=clean(req==null?null:req.name(),"Nueva evidencia");e.description=req==null||req.description()==null?"":req.description().trim();e.evidenceType=norm(req==null?null:req.evidenceType(),EVIDENCE_TYPES,"PHOTO","Tipo de evidencia inválido");e.required=req==null||req.required()==null||req.required();e.standardImageVersion=0;e.standardImageNotes="";e.visintEnabled=false;e.persist();c.evidenceRequired=true;return evidenceDto(e);}

 @PUT
 @Path("/evidences/{id}")
 @Consumes(MediaType.APPLICATION_JSON)
 @Transactional
 @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public EvidenceDto saveEvidence(@PathParam("id")UUID id,SaveEvidenceRequest req){ConsignmentEvidence e=evidence(id);Consignment c=item(e.consignmentId);ConsignmentProtocol p=protocol(c.protocolId);authorize(p);requireDraft(p);e.name=clean(req.name(),e.name);e.description=req.description()==null?"":req.description().trim();e.evidenceType=norm(req.evidenceType(),EVIDENCE_TYPES,e.evidenceType,"Tipo de evidencia inválido");e.required=req.required();e.standardImageNotes=req.standardImageNotes()==null?"":req.standardImageNotes().trim();return evidenceDto(e);}

 @DELETE
 @Path("/evidences/{id}")
 @Transactional
 @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public void deleteEvidence(@PathParam("id")UUID id){ConsignmentEvidence e=evidence(id);Consignment c=item(e.consignmentId);ConsignmentProtocol p=protocol(c.protocolId);authorize(p);requireDraft(p);e.delete();}

 @POST
 @Path("/evidences/{id}/standard-image")
 @Consumes(MediaType.APPLICATION_OCTET_STREAM)
 @Transactional
 @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public EvidenceDto uploadImage(@PathParam("id")UUID id,@QueryParam("filename")String filename,@QueryParam("contentType")String contentType,byte[] bytes){ConsignmentEvidence e=evidence(id);Consignment c=item(e.consignmentId);ConsignmentProtocol p=protocol(c.protocolId);authorize(p);requireDraft(p);if(bytes==null||bytes.length==0)throw new BadRequestException("Imagen obligatoria");if(bytes.length>MAX_IMAGE)throw new BadRequestException("La foto estándar no puede superar 5 MB");String ct=contentType==null?"":contentType.toLowerCase(Locale.ROOT);if(!IMAGE_TYPES.contains(ct))throw new BadRequestException("Formato de imagen no permitido");e.standardImageData=bytes;e.standardImageOriginalName=clean(filename,"foto-estandar");e.standardImageContentType=ct;e.standardImageVersion=Math.max(1,e.standardImageVersion+1);e.visintEnabled=false;return evidenceDto(e);}

 @GET
 @Path("/evidences/{id}/standard-image")
 @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
 public Response image(@PathParam("id")UUID id){ConsignmentEvidence e=evidence(id);Consignment c=item(e.consignmentId);authorize(protocol(c.protocolId));if(e.standardImageData==null)throw new NotFoundException("La evidencia no tiene foto estándar");return Response.ok(e.standardImageData).type(e.standardImageContentType).header(HttpHeaders.CACHE_CONTROL,"no-store").build();}

 @DELETE
 @Path("/evidences/{id}/standard-image")
 @Transactional
 @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public EvidenceDto deleteImage(@PathParam("id")UUID id){ConsignmentEvidence e=evidence(id);Consignment c=item(e.consignmentId);ConsignmentProtocol p=protocol(c.protocolId);authorize(p);requireDraft(p);e.standardImageData=null;e.standardImageOriginalName=null;e.standardImageContentType=null;e.visintEnabled=false;return evidenceDto(e);}

 private void validate(Consignment c){
   if(c.title==null||c.title.isBlank())throw new BadRequestException("Título obligatorio");if(c.instruction==null||c.instruction.isBlank())throw new BadRequestException(c.code+" requiere una instrucción");
   if("POSTS".equals(c.scopeType)&&scopes(c.id).isEmpty())throw new BadRequestException(c.code+" debe aplicar al menos a un Puesto");
   if("TEMPORARY".equals(c.validityType)&&(c.validityFrom==null||c.validityUntil==null||!c.validityUntil.isAfter(c.validityFrom)))throw new BadRequestException(c.code+" requiere un rango de vigencia válido");
   if("ATS".equals(c.expectedLocationMode)&&(c.atsX==null||c.atsY==null))throw new BadRequestException(c.code+" requiere ubicación en plano ATS");
   if("GPS".equals(c.expectedLocationMode)&&(c.expectedLatitude==null||c.expectedLongitude==null))throw new BadRequestException(c.code+" requiere coordenadas esperadas");
   if(c.evidenceRequired&&evidences(c.id).isEmpty())throw new BadRequestException(c.code+" requiere al menos una Evidencia configurada");
 }
 private void setScope(Consignment c,List<UUID> postIds){ConsignmentPostScope.delete("consignmentId=?1 and instanceCountryId=?2",c.id,tenant.instanceCountryId());c.postId=null;if("POINT".equals(c.scopeType))return;if(postIds==null||postIds.isEmpty())return;for(UUID id:new LinkedHashSet<>(postIds)){PostEntity post=post(id);if(!post.pointId.equals(c.pointId))throw new BadRequestException("El Puesto no pertenece al Punto");ConsignmentPostScope s=new ConsignmentPostScope();s.instanceCountryId=tenant.instanceCountryId();s.consignmentId=c.id;s.postId=id;s.persist();}}
 private Consignment cloneConsignment(Consignment old,UUID protocolId){Consignment c=new Consignment();c.id=UUID.randomUUID();c.instanceCountryId=tenant.instanceCountryId();c.protocolId=protocolId;c.pointId=old.pointId;c.code=old.code;c.title=old.title;c.instruction=old.instruction;c.priority=old.priority;c.status="BORRADOR";c.scopeType=old.scopeType;c.validityType=old.validityType;c.validityFrom=old.validityFrom;c.validityUntil=old.validityUntil;c.applicationType=old.applicationType;c.applicationDaysJson=old.applicationDaysJson;c.applicationTimeFrom=old.applicationTimeFrom;c.applicationTimeTo=old.applicationTimeTo;c.acknowledgmentRequired=old.acknowledgmentRequired;c.confirmationRequired=old.confirmationRequired;c.evidenceRequired=old.evidenceRequired;c.gpsRequired=old.gpsRequired;c.observationRequired=old.observationRequired;c.expectedLocationMode=old.expectedLocationMode;c.atsPackageId=old.atsPackageId;c.atsX=old.atsX;c.atsY=old.atsY;c.expectedLatitude=old.expectedLatitude;c.expectedLongitude=old.expectedLongitude;c.updatedByUsername=user();c.persist();for(ConsignmentPostScope oldScope:scopes(old.id)){ConsignmentPostScope s=new ConsignmentPostScope();s.instanceCountryId=tenant.instanceCountryId();s.consignmentId=c.id;s.postId=oldScope.postId;s.persist();}for(ConsignmentEvidence oldE:evidences(old.id)){ConsignmentEvidence e=new ConsignmentEvidence();e.instanceCountryId=tenant.instanceCountryId();e.consignmentId=c.id;e.sortOrder=oldE.sortOrder;e.name=oldE.name;e.description=oldE.description;e.evidenceType=oldE.evidenceType;e.required=oldE.required;e.standardImageOriginalName=oldE.standardImageOriginalName;e.standardImageContentType=oldE.standardImageContentType;e.standardImageData=oldE.standardImageData==null?null:Arrays.copyOf(oldE.standardImageData,oldE.standardImageData.length);e.standardImageVersion=oldE.standardImageVersion;e.standardImageNotes=oldE.standardImageNotes;e.visintEnabled=oldE.visintEnabled;e.persist();}return c;}
 private ProtocolDto dto(ConsignmentProtocol p){return new ProtocolDto(p.id,p.seriesId,p.basedOnProtocolId,p.pointId,p.code,p.name,p.description,p.status,p.versionNo,p.publishedAt,p.activatedAt,p.updatedByUsername,consignments(p.id).stream().map(this::itemDto).toList());}
 private ConsignmentDto itemDto(Consignment c){return new ConsignmentDto(c.id,c.protocolId,c.code,c.title,c.instruction,c.priority,c.status,c.scopeType,scopes(c.id).stream().map(s->s.postId).toList(),c.validityType,c.validityFrom,c.validityUntil,c.applicationType,c.applicationDaysJson,c.applicationTimeFrom==null?null:c.applicationTimeFrom.toString(),c.applicationTimeTo==null?null:c.applicationTimeTo.toString(),c.acknowledgmentRequired,c.confirmationRequired,c.evidenceRequired,c.gpsRequired,c.observationRequired,c.expectedLocationMode,c.atsPackageId,c.atsX,c.atsY,c.expectedLatitude,c.expectedLongitude,c.publishedAt,c.updatedByUsername,evidences(c.id).stream().map(this::evidenceDto).toList());}
 private EvidenceDto evidenceDto(ConsignmentEvidence e){return new EvidenceDto(e.id,e.consignmentId,e.sortOrder,e.name,e.description,e.evidenceType,e.required,e.standardImageData!=null&&e.standardImageData.length>0,e.standardImageOriginalName,e.standardImageVersion,e.standardImageNotes,e.visintEnabled);}
 private List<Consignment> consignments(UUID protocolId){return Consignment.list("protocolId=?1 and instanceCountryId=?2 order by code",protocolId,tenant.instanceCountryId());}
 private List<ConsignmentPostScope> scopes(UUID id){return ConsignmentPostScope.list("consignmentId=?1 and instanceCountryId=?2 order by createdAt",id,tenant.instanceCountryId());}
 private List<ConsignmentEvidence> evidences(UUID id){return ConsignmentEvidence.list("consignmentId=?1 and instanceCountryId=?2 order by sortOrder",id,tenant.instanceCountryId());}
 private int rank(ConsignmentProtocol p){return "BORRADOR".equals(p.status)?4:"VIGENTE".equals(p.status)?3:"PUBLICADO".equals(p.status)?2:1;}
 private void requireDraft(ConsignmentProtocol p){if(!"BORRADOR".equals(p.status))throw new ClientErrorException("La versión publicada es inmutable. Edite el Protocolo para generar una nueva versión borrador.",409);}
 private void authorize(ConsignmentProtocol p){PointEntity point=point(p.pointId);scope.requireCompany(point.companyId);}
 private PointEntity point(UUID id){if(id==null)throw new BadRequestException("pointId obligatorio");PointEntity p=PointEntity.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(p==null)throw new NotFoundException("Punto no encontrado");return p;}
 private PostEntity post(UUID id){PostEntity p=PostEntity.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(p==null)throw new NotFoundException("Puesto no encontrado");return p;}
 private ConsignmentProtocol protocol(UUID id){ConsignmentProtocol p=ConsignmentProtocol.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(p==null)throw new NotFoundException("Protocolo no encontrado");return p;}
 private Consignment item(UUID id){Consignment c=Consignment.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(c==null)throw new NotFoundException("Consigna no encontrada");return c;}
 private ConsignmentEvidence evidence(UUID id){ConsignmentEvidence e=ConsignmentEvidence.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(e==null)throw new NotFoundException("Evidencia no encontrada");return e;}
 private String user(){return identity.getPrincipal().getName();}
 private String nextProtocolCode(){long n=ConsignmentProtocol.<ConsignmentProtocol>list("instanceCountryId=?1",tenant.instanceCountryId()).stream().map(p->p.seriesId).distinct().count()+1;String code;do{code=String.format(Locale.ROOT,"PRO-CON-%04d",n++);}while(ConsignmentProtocol.count("instanceCountryId=?1 and code=?2",tenant.instanceCountryId(),code)>0);return code;}
 private String nextConsignmentCode(){long n=Consignment.count("instanceCountryId",tenant.instanceCountryId())+1;String code;do{code=String.format(Locale.ROOT,"CON-%06d",n++);}while(Consignment.count("instanceCountryId=?1 and code=?2",tenant.instanceCountryId(),code)>0);return code;}
 private String clean(String v,String fallback){return v==null||v.isBlank()?fallback:v.trim();}
 private String norm(String v,Set<String> allowed,String fallback,String message){String out=v==null||v.isBlank()?fallback:v.trim().toUpperCase(Locale.ROOT);if(out==null||!allowed.contains(out))throw new BadRequestException(message);return out;}
 private LocalTime parseTime(String v){if(v==null||v.isBlank())return null;try{return LocalTime.parse(v);}catch(Exception e){throw new BadRequestException("Hora inválida: "+v);}}
 private boolean bool(Boolean v,boolean fallback){return v==null?fallback:v;}
}
