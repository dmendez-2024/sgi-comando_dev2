package com.cajamarca.sgi.comando.consignments;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.common.ProtocolLibraryExport;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.operations.PostEntity;
import com.cajamarca.sgi.comando.secelec.ElectronicMonitoringDevice;
import com.cajamarca.sgi.comando.outbox.OutboxEvent;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import com.cajamarca.sgi.comando.storage.*;

import java.time.*;
import java.util.*;

@Path("/api/consignments")
@Produces(MediaType.APPLICATION_JSON)
public class ConsignmentResource {
 private static final Set<String> PROTOCOL_STATUS=Set.of("BORRADOR","INACTIVO","ACTIVO");
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
 @Inject com.cajamarca.sgi.comando.storage.StandardImageStore standardImages;
 @Inject ObjectMapper mapper;
 @Inject StandardReferenceImages references;

 public record EvidenceDto(UUID id,UUID consignmentId,int sortOrder,String name,String description,String evidenceType,boolean required,boolean hasStandardImage,List<StandardReferenceImages.Dto> standardImages,int standardImageVersion,String standardImageNotes,boolean visintEnabled,Double matchThreshold){}
 public record ConsignmentDto(UUID id,UUID protocolId,String code,String title,String instruction,String priority,String status,String scopeType,List<UUID> postIds,List<UUID> electronicDeviceIds,boolean obsint,String validityType,Instant validityFrom,Instant validityUntil,String applicationType,String applicationDaysJson,String applicationTimeFrom,String applicationTimeTo,boolean acknowledgmentRequired,boolean confirmationRequired,boolean evidenceRequired,boolean gpsRequired,boolean observationRequired,String expectedLocationMode,UUID atsPackageId,Double atsX,Double atsY,Double expectedLatitude,Double expectedLongitude,Integer expectedRadiusM,Instant publishedAt,String updatedBy,List<EvidenceDto> evidences){}
 public record ProtocolDto(UUID id,UUID seriesId,UUID basedOnProtocolId,UUID pointId,String code,String name,String description,String status,int versionNo,Instant publishedAt,Instant activatedAt,String updatedBy,List<ConsignmentDto> consignments){}
 public record CreateProtocolRequest(UUID pointId,String name){}
 public record SaveProtocolRequest(String name,String description){}
 public record ExportRequest(String scope){}
 public record ImportRequest(UUID postId){}
 public record LibraryEntryDto(UUID id,String scope,String code,String name,String description,int versionNo,int itemCount,int detailCount,String exportedBy,Instant exportedAt,boolean alreadyExported){}
 public record CreateConsignmentRequest(String title){}
 public record SaveConsignmentRequest(String title,String instruction,String priority,String scopeType,List<UUID> postIds,List<UUID> electronicDeviceIds,Boolean obsint,String validityType,Instant validityFrom,Instant validityUntil,String applicationType,String applicationDaysJson,String applicationTimeFrom,String applicationTimeTo,Boolean acknowledgmentRequired,Boolean confirmationRequired,Boolean evidenceRequired,Boolean gpsRequired,Boolean observationRequired,String expectedLocationMode,UUID atsPackageId,Double atsX,Double atsY,Double expectedLatitude,Double expectedLongitude,Integer expectedRadiusM){}
 public record CreateEvidenceRequest(String name,String description,String evidenceType,Boolean required){}
 public record SaveEvidenceRequest(String name,String description,String evidenceType,boolean required,String standardImageNotes,Double matchThreshold){}

 @GET
 @RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
 public List<Consignment> legacyList(@QueryParam("pointId") UUID pointId){PointEntity point=point(pointId);scope.requireCompanyForCompanyRoles(point.companyId);return Consignment.list("pointId=?1 and instanceCountryId=?2 order by createdAt desc",pointId,tenant.instanceCountryId());}

 @GET
 @Path("/protocols")
 @RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
 public List<ProtocolDto> protocols(@QueryParam("pointId") UUID pointId){
   PointEntity point=point(pointId);scope.requireCompanyForCompanyRoles(point.companyId);
   List<ConsignmentProtocol> all=ConsignmentProtocol.list("instanceCountryId=?1 and pointId=?2 order by code,versionNo desc",tenant.instanceCountryId(),pointId);
   Map<UUID,ConsignmentProtocol> latest=new LinkedHashMap<>();
   for(ConsignmentProtocol p:all){ConsignmentProtocol current=latest.get(p.seriesId);if(current==null||rank(p)>rank(current)||(rank(p)==rank(current)&&p.versionNo>current.versionNo))latest.put(p.seriesId,p);}
   return latest.values().stream().sorted(Comparator.comparing(p->p.code)).map(this::dto).toList();
 }

 @GET
 @Path("/protocols/current")
 @RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
 public ProtocolDto current(@QueryParam("pointId") UUID pointId){PointEntity point=point(pointId);scope.requireCompanyForCompanyRoles(point.companyId);ConsignmentProtocol p=ConsignmentProtocol.find("instanceCountryId=?1 and pointId=?2 and status='ACTIVO'",tenant.instanceCountryId(),pointId).firstResult();return p==null?null:dto(p);}

 @GET
 @Path("/protocols/{id}/history")
 @RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
 public List<ProtocolDto> history(@PathParam("id") UUID id){ConsignmentProtocol p=protocol(id);authorize(p);return ConsignmentProtocol.<ConsignmentProtocol>list("instanceCountryId=?1 and seriesId=?2 order by versionNo desc",tenant.instanceCountryId(),p.seriesId).stream().map(this::dto).toList();}

 @GET
 @Path("/protocol-library")
 @RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public List<LibraryEntryDto> library(@QueryParam("postId") UUID postId){
   PostEntity destination=post(postId);PointEntity destinationPoint=point(destination.pointId);scope.requireCompanyForCompanyRoles(destinationPoint.companyId);
   return ProtocolLibraryExport.<ProtocolLibraryExport>list("instanceCountryId=?1 and module='CONSIGNMENT' order by createdAt desc",tenant.instanceCountryId())
       .stream().filter(row->libraryVisible(row,destinationPoint.companyId)).map(row->libraryDto(row,protocol(row.sourceProtocolId),true)).toList();
 }

 @GET
 @Path("/protocols/{protocolId}/exports")
 @RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public List<LibraryEntryDto> exports(@PathParam("protocolId") UUID protocolId){
   ConsignmentProtocol source=protocol(protocolId);authorize(source);PointEntity sourcePoint=point(source.pointId);
   return ProtocolLibraryExport.<ProtocolLibraryExport>list("instanceCountryId=?1 and module='CONSIGNMENT' and sourceProtocolId=?2 order by createdAt",tenant.instanceCountryId(),source.id)
       .stream().filter(row->"COUNTRY".equals(row.scope)||Objects.equals(row.companyId,sourcePoint.companyId)).map(row->libraryDto(row,source,true)).toList();
 }

 @POST
 @Path("/protocols/{protocolId}/exports")
 @Consumes(MediaType.APPLICATION_JSON)
 @Transactional
 @RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public LibraryEntryDto exportProtocol(@PathParam("protocolId") UUID protocolId,ExportRequest request){
   ConsignmentProtocol source=protocol(protocolId);authorize(source);
   if("BORRADOR".equals(source.status))throw new ClientErrorException("Publique el protocolo antes de exportarlo.",409);
   if(request==null||request.scope()==null)throw new BadRequestException("Seleccione la biblioteca de destino.");
   String targetScope=request.scope().trim().toUpperCase(Locale.ROOT);
   if(!Set.of("COMPANY","COUNTRY").contains(targetScope))throw new BadRequestException("Biblioteca inválida.");
   PointEntity sourcePoint=point(source.pointId);UUID companyId="COMPANY".equals(targetScope)?sourcePoint.companyId:null;
   if("COMPANY".equals(targetScope)&&companyId==null)throw new BadRequestException("El Punto requiere una Compañía para exportar.");
   if("COUNTRY".equals(targetScope)&&!canExportCountryStandard())throw new ForbiddenException("Solo Dirección Zonal o superior puede exportar Estándares Cajamarca.");
   ProtocolLibraryExport existing="COMPANY".equals(targetScope)
       ?ProtocolLibraryExport.find("instanceCountryId=?1 and module='CONSIGNMENT' and sourceProtocolId=?2 and scope='COMPANY' and companyId=?3",tenant.instanceCountryId(),source.id,companyId).firstResult()
       :ProtocolLibraryExport.find("instanceCountryId=?1 and module='CONSIGNMENT' and sourceProtocolId=?2 and scope='COUNTRY'",tenant.instanceCountryId(),source.id).firstResult();
   if(existing!=null)return libraryDto(existing,source,true);
   ProtocolLibraryExport row=new ProtocolLibraryExport();row.module="CONSIGNMENT";row.instanceCountryId=tenant.instanceCountryId();row.sourceProtocolId=source.id;row.scope=targetScope;row.companyId=companyId;row.exportedByUsername=user();row.persist();return libraryDto(row,source,false);
 }

 @POST
 @Path("/protocol-library/{exportId}/import")
 @Consumes(MediaType.APPLICATION_JSON)
 @Transactional
 @RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public ProtocolDto importProtocol(@PathParam("exportId") UUID exportId,ImportRequest request){
   if(request==null||request.postId()==null)throw new BadRequestException("Seleccione un Puesto destino.");
   PostEntity destination=post(request.postId());PointEntity destinationPoint=point(destination.pointId);scope.requireCompanyForCompanyRoles(destinationPoint.companyId);
   ProtocolLibraryExport entry=ProtocolLibraryExport.find("id=?1 and instanceCountryId=?2 and module='CONSIGNMENT'",exportId,tenant.instanceCountryId()).firstResult();
   if(entry==null||!libraryVisible(entry,destinationPoint.companyId))throw new NotFoundException("Modelo no disponible para este Puesto.");
   ConsignmentProtocol source=protocol(entry.sourceProtocolId);
   if("BORRADOR".equals(source.status))throw new ClientErrorException("El modelo de origen debe estar publicado.",409);
   boolean samePoint=source.pointId.equals(destination.pointId);
   ConsignmentProtocol target=new ConsignmentProtocol();target.id=UUID.randomUUID();target.instanceCountryId=tenant.instanceCountryId();target.seriesId=target.id;
   target.basedOnProtocolId=source.id;target.pointId=destination.pointId;target.code=nextProtocolCode();target.name=source.name;target.description=source.description;
   target.status="BORRADOR";target.versionNo=1;target.updatedByUsername=user();target.persist();
   for(Consignment old:consignments(source.id)){
     Consignment copy=cloneConsignment(old,target.id);copy.pointId=destination.pointId;
     if("POSTS".equals(copy.scopeType))setScope(copy,List.of(destination.id),List.of());
     if(!samePoint){copy.atsPackageId=null;copy.atsX=null;copy.atsY=null;copy.expectedLatitude=null;copy.expectedLongitude=null;}
   }
   return dto(target);
 }

 private LibraryEntryDto libraryDto(ProtocolLibraryExport entry,ConsignmentProtocol source,boolean alreadyExported){
   List<Consignment> items=consignments(source.id);int evidenceCount=items.stream().mapToInt(item->evidences(item.id).size()).sum();
   return new LibraryEntryDto(entry.id,entry.scope,source.code,source.name,source.description,source.versionNo,items.size(),evidenceCount,entry.exportedByUsername,entry.createdAt,alreadyExported);
 }
 private boolean libraryVisible(ProtocolLibraryExport entry,UUID companyId){return "COUNTRY".equals(entry.scope)||("COMPANY".equals(entry.scope)&&companyId!=null&&companyId.equals(entry.companyId));}
 private boolean canExportCountryStandard(){return java.util.stream.Stream.of("PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL").anyMatch(identity::hasRole);}

 @POST
 @Path("/protocols")
 @Consumes(MediaType.APPLICATION_JSON)
 @Transactional
 @RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public ProtocolDto createProtocol(CreateProtocolRequest req){
   if(req==null||req.pointId()==null)throw new BadRequestException("Punto obligatorio");PointEntity point=point(req.pointId());scope.requireCompanyForCompanyRoles(point.companyId);
   ConsignmentProtocol p=new ConsignmentProtocol();p.id=UUID.randomUUID();p.instanceCountryId=tenant.instanceCountryId();p.seriesId=p.id;p.pointId=point.id;p.code=nextProtocolCode();p.name=clean(req.name(),"Nuevo protocolo de consignas");p.description="";p.status="BORRADOR";p.versionNo=1;p.updatedByUsername=user();p.persist();return dto(p);
 }

 @PUT
 @Path("/protocols/{id}")
 @Consumes(MediaType.APPLICATION_JSON)
 @Transactional
 @RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public ProtocolDto saveProtocol(@PathParam("id")UUID id,SaveProtocolRequest req){ConsignmentProtocol p=protocol(id);authorize(p);requireDraft(p);if(req==null)throw new BadRequestException("Solicitud obligatoria");p.name=clean(req.name(),p.name);p.description=req.description()==null?"":req.description().trim();p.updatedByUsername=user();return dto(p);}

 @POST
 @Path("/protocols/{id}/fork")
 @Transactional
 @RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public ProtocolDto fork(@PathParam("id")UUID id){
   ConsignmentProtocol source=protocol(id);authorize(source);ConsignmentProtocol existing=ConsignmentProtocol.find("instanceCountryId=?1 and seriesId=?2 and status='BORRADOR'",tenant.instanceCountryId(),source.seriesId).firstResult();if(existing!=null)return dto(existing);
   ConsignmentProtocol target=new ConsignmentProtocol();target.id=UUID.randomUUID();target.instanceCountryId=tenant.instanceCountryId();target.seriesId=source.seriesId;target.basedOnProtocolId=source.id;target.pointId=source.pointId;target.code=source.code;target.name=source.name;target.description=source.description;target.status="BORRADOR";target.versionNo=source.versionNo+1;target.updatedByUsername=user();target.persist();
   for(Consignment old:consignments(source.id))cloneConsignment(old,target.id);
   return dto(target);
 }

 @POST
 @Path("/protocols/{id}/publish")
 @Transactional
 @RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public ProtocolDto publish(@PathParam("id")UUID id) throws Exception{
   ConsignmentProtocol p=protocol(id);authorize(p);requireDraft(p);List<Consignment> items=consignments(p.id);if(items.isEmpty())throw new BadRequestException("El protocolo debe contener al menos una Consigna");for(Consignment c:items){validate(c);for(ConsignmentEvidence e:evidences(c.id))if(e.visintEnabled&&StandardReferenceImage.countOf(StandardReferenceImage.CONSIGNMENT_EVIDENCE,e.id)==0)throw new BadRequestException(c.code+": la evidencia \""+e.name+"\" requiere al menos una foto estándar (VISINT)");}
   p.publishedAt=Instant.now();p.status="INACTIVO";p.activatedAt=null;p.updatedByUsername=user();for(Consignment c:items){c.status="PUBLICADO";c.publishedAt=p.publishedAt;}
   OutboxEvent.of(tenant.instanceCountryId(),"CONSIGNMENT_PROTOCOL",p.id,"CONSIGNMENT_PROTOCOL_PUBLISHED",mapper.writeValueAsString(Map.of("protocolId",p.id,"pointId",p.pointId,"code",p.code,"version",p.versionNo,"status",p.status))).persist();
   return dto(p);
 }

 @POST
 @Path("/protocols/{id}/activate")
 @Transactional
 @RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public ProtocolDto activate(@PathParam("id")UUID id){
   ConsignmentProtocol p=protocol(id);authorize(p);if(!"INACTIVO".equals(p.status)&&!"ACTIVO".equals(p.status))throw new ClientErrorException("Solo un Protocolo inactivo puede activarse.",409);if("ACTIVO".equals(p.status))return dto(p);
   List<ConsignmentProtocol> active=ConsignmentProtocol.list("instanceCountryId=?1 and pointId=?2 and status='ACTIVO'",tenant.instanceCountryId(),p.pointId);for(ConsignmentProtocol old:active){old.status="INACTIVO";for(Consignment c:consignments(old.id))c.status="PUBLICADO";}
   // El índice único admite un solo ACTIVO por Punto: se guarda la desactivación antes de activar el nuevo.
   ConsignmentProtocol.flush();
   p.status="ACTIVO";p.activatedAt=Instant.now();p.updatedByUsername=user();for(Consignment c:consignments(p.id))c.status="VIGENTE";return dto(p);
 }

 @POST
 @Path("/protocols/{id}/deactivate")
 @Transactional
 @RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public ProtocolDto deactivate(@PathParam("id")UUID id){
   ConsignmentProtocol p=protocol(id);authorize(p);if(!"ACTIVO".equals(p.status))throw new ClientErrorException("Solo un Protocolo activo puede inactivarse.",409);p.status="INACTIVO";p.updatedByUsername=user();for(Consignment c:consignments(p.id))c.status="PUBLICADO";return dto(p);
 }

 @POST
 @Path("/protocols/{protocolId}/items")
 @Consumes(MediaType.APPLICATION_JSON)
 @Transactional
 @RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public ConsignmentDto createConsignment(@PathParam("protocolId")UUID protocolId,CreateConsignmentRequest req){
   ConsignmentProtocol p=protocol(protocolId);authorize(p);requireDraft(p);if(consignments(p.id).size()>=20)throw new ClientErrorException("Este Protocolo ya alcanzó el máximo de 20 Consignas. Cree un nuevo Protocolo para continuar.",409);Consignment c=new Consignment();c.instanceCountryId=tenant.instanceCountryId();c.protocolId=p.id;c.pointId=p.pointId;c.code=nextConsignmentCode();c.title=clean(req==null?null:req.title(),"Nueva consigna");c.instruction="";c.priority="MEDIUM";c.status="BORRADOR";c.scopeType="POINT";c.validityType="PERMANENT";c.applicationType="ALL_TIME";c.applicationDaysJson="[]";c.acknowledgmentRequired=false;c.confirmationRequired=true;c.evidenceRequired=false;c.gpsRequired=false;c.observationRequired=false;c.expectedLocationMode="NONE";c.updatedByUsername=user();c.persist();return itemDto(c);
 }

 @PUT
 @Path("/items/{id}")
 @Consumes(MediaType.APPLICATION_JSON)
 @Transactional
 @RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public ConsignmentDto saveConsignment(@PathParam("id")UUID id,SaveConsignmentRequest req){
   Consignment c=item(id);ConsignmentProtocol p=protocol(c.protocolId);authorize(p);requireDraft(p);if(req==null)throw new BadRequestException("Solicitud obligatoria");
   c.title=clean(req.title(),c.title);c.instruction=req.instruction()==null?"":req.instruction().trim();c.priority=norm(req.priority(),PRIORITIES,c.priority,"Prioridad inválida");c.scopeType=norm(req.scopeType(),SCOPES,c.scopeType,"Alcance inválido");c.obsint=bool(req.obsint(),c.obsint);c.validityType=norm(req.validityType(),VALIDITIES,c.validityType,"Vigencia inválida");c.validityFrom=req.validityFrom();c.validityUntil=req.validityUntil();c.applicationType=norm(req.applicationType(),APPLICATIONS,c.applicationType,"Aplicación inválida");c.applicationDaysJson=req.applicationDaysJson()==null?"[]":req.applicationDaysJson();c.applicationTimeFrom=parseTime(req.applicationTimeFrom());c.applicationTimeTo=parseTime(req.applicationTimeTo());c.acknowledgmentRequired=bool(req.acknowledgmentRequired(),c.acknowledgmentRequired);c.confirmationRequired=bool(req.confirmationRequired(),c.confirmationRequired);c.evidenceRequired=bool(req.evidenceRequired(),c.evidenceRequired);c.gpsRequired=bool(req.gpsRequired(),c.gpsRequired);c.observationRequired=bool(req.observationRequired(),c.observationRequired);c.expectedLocationMode=norm(req.expectedLocationMode(),LOCATION_MODES,c.expectedLocationMode,"Ubicación inválida");c.atsPackageId=req.atsPackageId();c.atsX=req.atsX();c.atsY=req.atsY();c.expectedLatitude=req.expectedLatitude();c.expectedLongitude=req.expectedLongitude();c.expectedRadiusM=com.cajamarca.sgi.comando.settings.EvidenceLocationSettings.validRadius(req.expectedRadiusM(),"de la consigna");c.updatedByUsername=user();
   setScope(c,req.postIds(),req.electronicDeviceIds());validate(c);return itemDto(c);
 }

 @DELETE
 @Path("/items/{id}")
 @Transactional
 @RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public void deleteConsignment(@PathParam("id")UUID id){Consignment c=item(id);ConsignmentProtocol p=protocol(c.protocolId);authorize(p);requireDraft(p);for(ConsignmentEvidence e:evidences(c.id))StandardReferenceImage.delete("targetType=?1 and targetId=?2",StandardReferenceImage.CONSIGNMENT_EVIDENCE,e.id);ConsignmentEvidence.delete("consignmentId=?1 and instanceCountryId=?2",c.id,tenant.instanceCountryId());ConsignmentPostScope.delete("consignmentId=?1 and instanceCountryId=?2",c.id,tenant.instanceCountryId());c.delete();}

 @POST
 @Path("/items/{id}/evidences")
 @Consumes(MediaType.APPLICATION_JSON)
 @Transactional
 @RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public EvidenceDto createEvidence(@PathParam("id")UUID id,CreateEvidenceRequest req){Consignment c=item(id);ConsignmentProtocol p=protocol(c.protocolId);authorize(p);requireDraft(p);ConsignmentEvidence e=new ConsignmentEvidence();e.instanceCountryId=tenant.instanceCountryId();e.consignmentId=c.id;e.sortOrder=(int)ConsignmentEvidence.count("consignmentId=?1 and instanceCountryId=?2",c.id,tenant.instanceCountryId())+1;e.name=clean(req==null?null:req.name(),"Nueva evidencia");e.description=req==null||req.description()==null?"":req.description().trim();e.evidenceType=norm(req==null?null:req.evidenceType(),EVIDENCE_TYPES,"PHOTO","Tipo de evidencia inválido");e.required=req==null||req.required()==null||req.required();e.standardImageVersion=0;e.standardImageNotes="";e.visintEnabled=visint(e);e.persist();c.evidenceRequired=true;return evidenceDto(e);}

 @PUT
 @Path("/evidences/{id}")
 @Consumes(MediaType.APPLICATION_JSON)
 @Transactional
 @RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public EvidenceDto saveEvidence(@PathParam("id")UUID id,SaveEvidenceRequest req){ConsignmentEvidence e=evidence(id);Consignment c=item(e.consignmentId);ConsignmentProtocol p=protocol(c.protocolId);authorize(p);requireDraft(p);e.name=clean(req.name(),e.name);e.description=req.description()==null?"":req.description().trim();e.evidenceType=norm(req.evidenceType(),EVIDENCE_TYPES,e.evidenceType,"Tipo de evidencia inválido");e.required=req.required();e.standardImageNotes=req.standardImageNotes()==null?"":req.standardImageNotes().trim();if(req.matchThreshold()!=null)e.matchThreshold=com.cajamarca.sgi.comando.visint.MatchThreshold.normalize(req.matchThreshold());e.visintEnabled=visint(e);return evidenceDto(e);}

 @DELETE
 @Path("/evidences/{id}")
 @Transactional
 @RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public void deleteEvidence(@PathParam("id")UUID id){ConsignmentEvidence e=evidence(id);Consignment c=item(e.consignmentId);ConsignmentProtocol p=protocol(c.protocolId);authorize(p);requireDraft(p);StandardReferenceImage.delete("targetType=?1 and targetId=?2",StandardReferenceImage.CONSIGNMENT_EVIDENCE,e.id);e.delete();}

 /** Agrega una foto estándar a la evidencia (hasta 5). VISINT las recibe todas en referenceImages. */
 @POST
 @Path("/evidences/{id}/standard-images")
 @Consumes(MediaType.MULTIPART_FORM_DATA)
 @Transactional
 @RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public EvidenceDto addStandardImage(@PathParam("id")UUID id,@org.jboss.resteasy.reactive.RestForm("file") org.jboss.resteasy.reactive.multipart.FileUpload file){ConsignmentEvidence e=evidence(id);Consignment c=item(e.consignmentId);ConsignmentProtocol p=protocol(c.protocolId);authorize(p);requireDraft(p);references.add(tenant.instanceCountryId(),StandardReferenceImage.CONSIGNMENT_EVIDENCE,e.id,"consignment",file);e.standardImageVersion++;return evidenceDto(e);}

 @GET
 @Path("/evidences/{id}/standard-images/{imageId}")
 @RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
 public Response standardImage(@PathParam("id")UUID id,@PathParam("imageId")UUID imageId){ConsignmentEvidence e=evidence(id);Consignment c=item(e.consignmentId);authorize(protocol(c.protocolId));StandardReferenceImage img=references.get(StandardReferenceImage.CONSIGNMENT_EVIDENCE,e.id,imageId);return Response.ok(references.read(img)).type(img.contentType).header(HttpHeaders.CACHE_CONTROL,"no-store").build();}

 @DELETE
 @Path("/evidences/{id}/standard-images/{imageId}")
 @Transactional
 @RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
 public EvidenceDto deleteStandardImage(@PathParam("id")UUID id,@PathParam("imageId")UUID imageId){ConsignmentEvidence e=evidence(id);Consignment c=item(e.consignmentId);ConsignmentProtocol p=protocol(c.protocolId);authorize(p);requireDraft(p);references.delete(StandardReferenceImage.CONSIGNMENT_EVIDENCE,e.id,imageId);e.standardImageVersion++;return evidenceDto(e);}

 private void validate(Consignment c){
   if(c.title==null||c.title.isBlank())throw new BadRequestException("Título obligatorio");if(c.instruction==null||c.instruction.isBlank())throw new BadRequestException(c.code+" requiere una instrucción");
   if("POSTS".equals(c.scopeType)&&!c.obsint&&scopes(c.id).isEmpty())throw new BadRequestException(c.code+" debe aplicar al menos a un Puesto u OBSINT");
   if("TEMPORARY".equals(c.validityType)&&(c.validityFrom==null||c.validityUntil==null||!c.validityUntil.isAfter(c.validityFrom)))throw new BadRequestException(c.code+" requiere un rango de vigencia válido");
   if("ATS".equals(c.expectedLocationMode)&&(c.atsX==null||c.atsY==null))throw new BadRequestException(c.code+" requiere ubicación en plano ATS");
   if("GPS".equals(c.expectedLocationMode)&&(c.expectedLatitude==null||c.expectedLongitude==null))throw new BadRequestException(c.code+" requiere coordenadas esperadas");
   if(c.evidenceRequired&&evidences(c.id).isEmpty())throw new BadRequestException(c.code+" requiere al menos una Evidencia configurada");
 }
 private void setScope(Consignment c,List<UUID> postIds,List<UUID> electronicDeviceIds){ConsignmentPostScope.delete("consignmentId=?1 and instanceCountryId=?2",c.id,tenant.instanceCountryId());c.postId=null;if("POINT".equals(c.scopeType))return;if(postIds!=null)for(UUID id:new LinkedHashSet<>(postIds)){PostEntity post=post(id);if(!post.pointId.equals(c.pointId))throw new BadRequestException("El Puesto no pertenece al Punto");ConsignmentPostScope s=new ConsignmentPostScope();s.instanceCountryId=tenant.instanceCountryId();s.consignmentId=c.id;s.postId=id;s.persist();}if(electronicDeviceIds!=null)for(UUID id:new LinkedHashSet<>(electronicDeviceIds)){ElectronicMonitoringDevice device=electronicDevice(id);if(!device.pointId.equals(c.pointId))throw new BadRequestException("El Dispositivo no pertenece al Punto");ConsignmentPostScope s=new ConsignmentPostScope();s.instanceCountryId=tenant.instanceCountryId();s.consignmentId=c.id;s.electronicDeviceId=id;s.persist();}}
 private Consignment cloneConsignment(Consignment old,UUID protocolId){Consignment c=new Consignment();c.id=UUID.randomUUID();c.instanceCountryId=tenant.instanceCountryId();c.protocolId=protocolId;c.pointId=old.pointId;c.code=old.code;c.title=old.title;c.instruction=old.instruction;c.priority=old.priority;c.status="BORRADOR";c.scopeType=old.scopeType;c.obsint=old.obsint;c.validityType=old.validityType;c.validityFrom=old.validityFrom;c.validityUntil=old.validityUntil;c.applicationType=old.applicationType;c.applicationDaysJson=old.applicationDaysJson;c.applicationTimeFrom=old.applicationTimeFrom;c.applicationTimeTo=old.applicationTimeTo;c.acknowledgmentRequired=old.acknowledgmentRequired;c.confirmationRequired=old.confirmationRequired;c.evidenceRequired=old.evidenceRequired;c.gpsRequired=old.gpsRequired;c.observationRequired=old.observationRequired;c.expectedLocationMode=old.expectedLocationMode;c.atsPackageId=old.atsPackageId;c.atsX=old.atsX;c.atsY=old.atsY;c.expectedLatitude=old.expectedLatitude;c.expectedLongitude=old.expectedLongitude;c.expectedRadiusM=old.expectedRadiusM;c.updatedByUsername=user();c.persist();for(ConsignmentPostScope oldScope:scopes(old.id)){ConsignmentPostScope s=new ConsignmentPostScope();s.instanceCountryId=tenant.instanceCountryId();s.consignmentId=c.id;s.postId=oldScope.postId;s.electronicDeviceId=oldScope.electronicDeviceId;s.persist();}for(ConsignmentEvidence oldE:evidences(old.id)){ConsignmentEvidence e=new ConsignmentEvidence();e.instanceCountryId=tenant.instanceCountryId();e.consignmentId=c.id;e.sortOrder=oldE.sortOrder;e.name=oldE.name;e.description=oldE.description;e.evidenceType=oldE.evidenceType;e.required=oldE.required;e.standardImageOriginalName=oldE.standardImageOriginalName;e.standardImageContentType=oldE.standardImageContentType;e.standardImageData=oldE.standardImageData==null?null:Arrays.copyOf(oldE.standardImageData,oldE.standardImageData.length);e.standardImageObjectKey=oldE.standardImageObjectKey;e.standardImageSha256=oldE.standardImageSha256;e.standardImageSize=oldE.standardImageSize;e.standardImageVersion=oldE.standardImageVersion;e.standardImageNotes=oldE.standardImageNotes;e.visintEnabled=oldE.visintEnabled;e.matchThreshold=oldE.matchThreshold;e.persist();references.copy(StandardReferenceImage.CONSIGNMENT_EVIDENCE,oldE.id,e.id);}return c;}
 private ProtocolDto dto(ConsignmentProtocol p){return new ProtocolDto(p.id,p.seriesId,p.basedOnProtocolId,p.pointId,p.code,p.name,p.description,p.status,p.versionNo,p.publishedAt,p.activatedAt,p.updatedByUsername,consignments(p.id).stream().map(this::itemDto).toList());}
 private ConsignmentDto itemDto(Consignment c){List<ConsignmentPostScope> rows=scopes(c.id);return new ConsignmentDto(c.id,c.protocolId,c.code,c.title,c.instruction,c.priority,c.status,c.scopeType,rows.stream().map(s->s.postId).filter(Objects::nonNull).toList(),rows.stream().map(s->s.electronicDeviceId).filter(Objects::nonNull).toList(),c.obsint,c.validityType,c.validityFrom,c.validityUntil,c.applicationType,c.applicationDaysJson,c.applicationTimeFrom==null?null:c.applicationTimeFrom.toString(),c.applicationTimeTo==null?null:c.applicationTimeTo.toString(),c.acknowledgmentRequired,c.confirmationRequired,c.evidenceRequired,c.gpsRequired,c.observationRequired,c.expectedLocationMode,c.atsPackageId,c.atsX,c.atsY,c.expectedLatitude,c.expectedLongitude,c.expectedRadiusM,c.publishedAt,c.updatedByUsername,evidences(c.id).stream().map(this::evidenceDto).toList());}
 private EvidenceDto evidenceDto(ConsignmentEvidence e){return new EvidenceDto(e.id,e.consignmentId,e.sortOrder,e.name,e.description,e.evidenceType,e.required,StandardReferenceImage.countOf(StandardReferenceImage.CONSIGNMENT_EVIDENCE,e.id)>0,references.dtos(StandardReferenceImage.CONSIGNMENT_EVIDENCE,e.id),e.standardImageVersion,e.standardImageNotes,e.visintEnabled,e.matchThreshold);}
 /** VISINT valida las evidencias tipo Foto (activo por defecto, sin opción en pantalla). */
 private static boolean visint(ConsignmentEvidence e){return "PHOTO".equals(e.evidenceType);}
 private List<Consignment> consignments(UUID protocolId){return Consignment.list("protocolId=?1 and instanceCountryId=?2 order by code",protocolId,tenant.instanceCountryId());}
 private List<ConsignmentPostScope> scopes(UUID id){return ConsignmentPostScope.list("consignmentId=?1 and instanceCountryId=?2 order by createdAt",id,tenant.instanceCountryId());}
 private List<ConsignmentEvidence> evidences(UUID id){return ConsignmentEvidence.list("consignmentId=?1 and instanceCountryId=?2 order by sortOrder",id,tenant.instanceCountryId());}
 private int rank(ConsignmentProtocol p){return "BORRADOR".equals(p.status)?2:1;}
 private void requireDraft(ConsignmentProtocol p){if(!"BORRADOR".equals(p.status))throw new ClientErrorException("La versión publicada es inmutable. Edite el Protocolo para generar una nueva versión borrador.",409);}
 private void authorize(ConsignmentProtocol p){PointEntity point=point(p.pointId);scope.requireCompanyForCompanyRoles(point.companyId);}
 private PointEntity point(UUID id){if(id==null)throw new BadRequestException("pointId obligatorio");PointEntity p=PointEntity.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(p==null)throw new NotFoundException("Punto no encontrado");return p;}
 private PostEntity post(UUID id){PostEntity p=PostEntity.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(p==null)throw new NotFoundException("Puesto no encontrado");return p;}
 private ElectronicMonitoringDevice electronicDevice(UUID id){ElectronicMonitoringDevice d=ElectronicMonitoringDevice.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(d==null)throw new NotFoundException("Dispositivo no encontrado");return d;}
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
