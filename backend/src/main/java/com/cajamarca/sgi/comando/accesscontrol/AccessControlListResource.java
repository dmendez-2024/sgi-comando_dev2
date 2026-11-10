package com.cajamarca.sgi.comando.accesscontrol;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.operations.ServiceEntity;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.time.*;
import java.util.*;
import java.io.ByteArrayOutputStream;
import java.security.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

@Path("/api/access-control-lists")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
public class AccessControlListResource {
    private static final Set<String> LIST_TYPES=Set.of("WHITE","BLACK"),STATUSES=Set.of("ACTIVE","INACTIVE"),OBJECT_TYPES=Set.of("PAX","VHL","CONT");
    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject SecurityIdentity identity;

    public record PointDto(UUID id,String code,String name) {}
    public record EntryDto(UUID id,UUID clientId,String listType,String objectType,String identification,String lastNames,String firstNames,String accreditation,String organizationalReference,LocalDate validFrom,LocalDate validUntil,String status,String effectiveStatus,String observations,boolean hasPhoto,boolean hasQr,List<UUID> pointIds,List<PointDto> points,String createdBy,Instant createdAt,String updatedBy,Instant updatedAt) {}
    public record SaveRequest(UUID clientId,String listType,String objectType,String identification,String lastNames,String firstNames,String accreditation,String organizationalReference,LocalDate validFrom,LocalDate validUntil,String status,String observations,List<UUID> pointIds,String photoBase64,String photoContentType,String photoFilename) {}

    @GET
    public List<EntryDto> list(@QueryParam("clientId") UUID clientId,@QueryParam("listType") String listType,@QueryParam("pointId") UUID pointId,@QueryParam("status") String status,@QueryParam("q") String query){
        requireClient(clientId);String type=normalize(listType,LIST_TYPES,"Tipo de lista inválido");
        List<AccessControlEntry> rows=AccessControlEntry.list("instanceCountryId=?1 and clientId=?2 and listType=?3 order by updatedAt desc",tenant.instanceCountryId(),clientId,type);
        if(status!=null&&!status.isBlank()){String state=normalize(status,STATUSES,"Estado inválido");rows=rows.stream().filter(row->state.equals(row.status)).toList();}
        if(query!=null&&!query.isBlank()){String q=query.trim().toLowerCase(Locale.ROOT);rows=rows.stream().filter(row->{AccessControlPerson person=person(row);return person.identification.toLowerCase(Locale.ROOT).contains(q)||person.firstNames.toLowerCase(Locale.ROOT).contains(q)||person.lastNames.toLowerCase(Locale.ROOT).contains(q);}).toList();}
        if(pointId!=null){pointForClient(pointId,clientId);Set<UUID> ids=new HashSet<>(AccessControlEntryPoint.<AccessControlEntryPoint>list("instanceCountryId=?1 and pointId=?2",tenant.instanceCountryId(),pointId).stream().map(link->link.entryId).toList());rows=rows.stream().filter(row->ids.contains(row.id)).toList();}
        return rows.stream().map(this::dto).toList();
    }

    @GET @Path("/points")
    public List<PointDto> points(@QueryParam("clientId") UUID clientId){requireClient(clientId);return pointsForClient(clientId).stream().map(p->new PointDto(p.id,p.code,p.name)).toList();}

    @POST @Consumes(MediaType.APPLICATION_JSON) @Transactional
    public EntryDto create(SaveRequest request){validate(request,null);validateWhiteAgainstBlack(request);AccessControlPerson person=personFor(request,null);AccessControlEntry row=new AccessControlEntry();row.id=UUID.randomUUID();row.instanceCountryId=tenant.instanceCountryId();row.personId=person.id;apply(row,request,true);row.createdByUsername=user();row.updatedByUsername=user();if("WHITE".equals(row.listType)&&person.qrTokenHash==null)generateQr(person);row.persistAndFlush();replacePoints(row,request.pointIds());return dto(row);}

    @PUT @Path("/{id}") @Consumes(MediaType.APPLICATION_JSON) @Transactional
    public EntryDto update(@PathParam("id") UUID id,SaveRequest request){AccessControlEntry row=entry(id);validate(request,row);validateWhiteAgainstBlack(request);AccessControlPerson person=personFor(request,row);row.personId=person.id;apply(row,request,false);row.updatedByUsername=user();replacePoints(row,request.pointIds());return dto(row);}

    @GET @Path("/{id}/photo") @Produces("image/*")
    public Response photo(@PathParam("id") UUID id){AccessControlEntry row=entry(id);requireClient(row.clientId);AccessControlPerson person=person(row);if(person.photoData==null||person.photoData.length==0)throw new NotFoundException("La persona no tiene fotografía registrada.");String filename=person.photoFilename==null?"foto":person.photoFilename.replace("\"","");return Response.ok(person.photoData,person.photoContentType==null?"image/jpeg":person.photoContentType).header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\""+filename+"\"").build();}

    @GET @Path("/{id}/qr") @Produces("image/png")
    public Response qr(@PathParam("id") UUID id){AccessControlEntry row=entry(id);requireClient(row.clientId);AccessControlPerson person=person(row);if(!"WHITE".equals(row.listType)||person.qrPng==null||person.qrRevokedAt!=null)throw new NotFoundException("La persona no tiene un código QR vigente.");return Response.ok(person.qrPng,"image/png").header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"QR-"+person.identification.replaceAll("[^A-Za-z0-9]","")+".png\"").build();}

    @POST @Path("/{id}/qr/regenerate") @Transactional
    public EntryDto regenerateQr(@PathParam("id") UUID id){AccessControlEntry row=entry(id);requireClient(row.clientId);if(!"WHITE".equals(row.listType))throw new BadRequestException("Solo las personas de Lista Blanca pueden tener código QR.");AccessControlPerson person=person(row);generateQr(person);person.updatedByUsername=user();return dto(row);}

    @DELETE @Path("/{id}/qr") @Transactional
    public EntryDto revokeQr(@PathParam("id") UUID id){AccessControlEntry row=entry(id);requireClient(row.clientId);if(!"WHITE".equals(row.listType))throw new BadRequestException("Solo las personas de Lista Blanca pueden tener código QR.");AccessControlPerson person=person(row);person.qrRevokedAt=Instant.now();person.updatedByUsername=user();return dto(row);}

    private void apply(AccessControlEntry row,SaveRequest request,boolean creating){row.clientId=request.clientId();row.listType=normalize(request.listType(),LIST_TYPES,"Tipo de lista inválido");row.objectType=normalizeDefault(request.objectType(),OBJECT_TYPES,"PAX","Objeto inválido");row.identification=request.identification().trim();row.identificationNormalized=normalizeIdentity(row.identification);row.lastNames=request.lastNames().trim();row.firstNames=request.firstNames().trim();row.accreditation=request.accreditation().trim();row.organizationalReference=clean(request.organizationalReference());row.validFrom=request.validFrom();row.validUntil=request.validUntil();row.status=normalizeDefault(request.status(),STATUSES,"ACTIVE","Estado inválido");row.observations=clean(request.observations());if(request.photoBase64()!=null&&!request.photoBase64().isBlank()){String raw=request.photoBase64();int comma=raw.indexOf(',');if(comma>=0)raw=raw.substring(comma+1);try{row.photoData=Base64.getDecoder().decode(raw);}catch(IllegalArgumentException ex){throw new BadRequestException("La fotografía no tiene un formato válido.");}if(row.photoData.length>5*1024*1024)throw new BadRequestException("La fotografía no puede superar 5 MB.");row.photoContentType=clean(request.photoContentType());row.photoFilename=clean(request.photoFilename());}else if(creating){row.photoData=null;}}
    private AccessControlPerson personFor(SaveRequest request,AccessControlEntry existing){
        String objectType=normalizeDefault(request.objectType(),OBJECT_TYPES,"PAX","Objeto inválido");
        String identity=normalizeIdentity(request.identification());
        AccessControlPerson person=AccessControlPerson.find("instanceCountryId=?1 and objectType=?2 and identificationNormalized=?3",tenant.instanceCountryId(),objectType,identity).firstResult();
        boolean creating=person==null;
        if(creating){person=new AccessControlPerson();person.id=UUID.randomUUID();person.instanceCountryId=tenant.instanceCountryId();person.objectType=objectType;person.identificationNormalized=identity;person.createdByUsername=user();}
        String identification=request.identification().trim(),lastNames=request.lastNames().trim(),firstNames=request.firstNames().trim();
        boolean changed=creating||!Objects.equals(person.identification,identification)||!Objects.equals(person.lastNames,lastNames)||!Objects.equals(person.firstNames,firstNames);
        person.identification=identification;person.lastNames=lastNames;person.firstNames=firstNames;
        if(request.photoBase64()!=null&&!request.photoBase64().isBlank()){
            String raw=request.photoBase64();int comma=raw.indexOf(',');if(comma>=0)raw=raw.substring(comma+1);
            try{person.photoData=Base64.getDecoder().decode(raw);}catch(IllegalArgumentException ex){throw new BadRequestException("La fotografía no tiene un formato válido.");}
            if(person.photoData.length>5*1024*1024)throw new BadRequestException("La fotografía no puede superar 5 MB.");
            person.photoContentType=clean(request.photoContentType());person.photoFilename=clean(request.photoFilename());
            changed=true;
        }
        if(changed)person.updatedByUsername=user();
        if(creating)person.persistAndFlush();
        return person;
    }
    private void validate(SaveRequest request,AccessControlEntry existing){if(request==null)throw new BadRequestException("Solicitud obligatoria.");requireClient(request.clientId());if(blank(request.identification())||blank(request.lastNames())||blank(request.firstNames())||blank(request.accreditation()))throw new BadRequestException("Identificación, apellidos, nombres y acreditación son obligatorios.");String type=normalize(request.listType(),LIST_TYPES,"Tipo de lista inválido");if(request.validFrom()==null||request.validUntil()==null)throw new BadRequestException("La vigencia es obligatoria.");if(request.validUntil().isBefore(request.validFrom()))throw new BadRequestException("La fecha fin no puede ser anterior a la fecha de inicio.");if(request.pointIds()==null||request.pointIds().isEmpty())throw new BadRequestException("Seleccione al menos un Punto.");request.pointIds().forEach(id->pointForClient(id,request.clientId()));String identity=normalizeIdentity(request.identification());if(existing!=null&&!existing.identificationNormalized.equals(identity))throw new BadRequestException("La identificación de una persona no puede modificarse. Registre una identidad nueva.");List<AccessControlEntry> periods=AccessControlEntry.list("instanceCountryId=?1 and clientId=?2 and listType=?3 and objectType=?4 and identificationNormalized=?5",tenant.instanceCountryId(),request.clientId(),type,normalizeDefault(request.objectType(),OBJECT_TYPES,"PAX","Objeto inválido"),identity);boolean overlaps=periods.stream().filter(row->existing==null||!row.id.equals(existing.id)).anyMatch(row->!request.validUntil().isBefore(row.validFrom)&&!request.validFrom().isAfter(row.validUntil));if(overlaps)throw new ClientErrorException("La persona ya tiene una autorización cuya vigencia se superpone con las fechas seleccionadas.",409);}
    private void replacePoints(AccessControlEntry row,List<UUID> pointIds){AccessControlEntryPoint.delete("entryId=?1 and instanceCountryId=?2",row.id,tenant.instanceCountryId());for(UUID pointId:new LinkedHashSet<>(pointIds)){AccessControlEntryPoint link=new AccessControlEntryPoint();link.entryId=row.id;link.pointId=pointId;link.instanceCountryId=tenant.instanceCountryId();link.createdAt=Instant.now();link.persist();}}
    private void validateWhiteAgainstBlack(SaveRequest request){if(!"WHITE".equalsIgnoreCase(request.listType()))return;String identity=normalizeIdentity(request.identification());String objectType=normalizeDefault(request.objectType(),OBJECT_TYPES,"PAX","Objeto inválido");List<AccessControlEntry> restrictions=AccessControlEntry.list("instanceCountryId=?1 and clientId=?2 and listType='BLACK' and status='ACTIVE' and objectType=?3 and identificationNormalized=?4",tenant.instanceCountryId(),request.clientId(),objectType,identity);Set<UUID> requested=new HashSet<>(request.pointIds());for(AccessControlEntry restriction:restrictions){List<AccessControlEntryPoint> links=AccessControlEntryPoint.list("entryId=?1 and instanceCountryId=?2",restriction.id,tenant.instanceCountryId());for(AccessControlEntryPoint link:links)if(requested.contains(link.pointId)){PointEntity point=pointForClient(link.pointId,request.clientId());throw new ClientErrorException("No se puede autorizar a la persona: mantiene una restricción activa en el Punto "+point.name+". Desactive primero el registro de Lista Negra.",409);}}}
    private EntryDto dto(AccessControlEntry row){
        AccessControlPerson person=person(row);
        List<AccessControlEntryPoint> links=AccessControlEntryPoint.list("entryId=?1 and instanceCountryId=?2",row.id,tenant.instanceCountryId());
        List<PointDto> points=new ArrayList<>();
        for(AccessControlEntryPoint link:links){
            PointEntity point=PointEntity.find("id=?1 and instanceCountryId=?2",link.pointId,tenant.instanceCountryId()).firstResult();
            if(point!=null)points.add(new PointDto(point.id,point.code,point.name));
        }
        points.sort(Comparator.comparing(PointDto::name));
        LocalDate today=LocalDate.now();
        String effective=!"ACTIVE".equals(row.status)?"INACTIVE":today.isAfter(row.validUntil)?"EXPIRED":today.isBefore(row.validFrom)?"SCHEDULED":!row.validUntil.isAfter(today.plusDays(30))?"EXPIRING":"VALID";
        return new EntryDto(row.id,row.clientId,row.listType,row.objectType,person.identification,person.lastNames,person.firstNames,row.accreditation,row.organizationalReference,row.validFrom,row.validUntil,row.status,effective,row.observations,person.photoData!=null&&person.photoData.length>0,"WHITE".equals(row.listType)&&person.qrPng!=null&&person.qrRevokedAt==null,points.stream().map(PointDto::id).toList(),points,row.createdByUsername,row.createdAt,row.updatedByUsername,row.updatedAt);
    }
    private void generateQr(AccessControlPerson person){try{byte[] token=new byte[32];SecureRandom.getInstanceStrong().nextBytes(token);String encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(token);person.qrTokenHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token));BitMatrix matrix=new QRCodeWriter().encode("SGI-WL1."+encoded,BarcodeFormat.QR_CODE,640,640);ByteArrayOutputStream output=new ByteArrayOutputStream();MatrixToImageWriter.writeToStream(matrix,"PNG",output);person.qrPng=output.toByteArray();person.qrGeneratedAt=Instant.now();person.qrRevokedAt=null;Arrays.fill(token,(byte)0);}catch(Exception ex){throw new InternalServerErrorException("No fue posible generar el código QR.",ex);}}
    private AccessControlPerson person(AccessControlEntry row){AccessControlPerson person=AccessControlPerson.find("id=?1 and instanceCountryId=?2",row.personId,tenant.instanceCountryId()).firstResult();if(person==null)throw new NotFoundException("Identidad asociada no encontrada.");return person;}
    private AccessControlEntry entry(UUID id){AccessControlEntry row=AccessControlEntry.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(row==null)throw new NotFoundException("Registro no encontrado.");return row;}
    private void requireClient(UUID clientId){if(clientId==null)throw new BadRequestException("Cliente obligatorio.");ServiceEntity service=ServiceEntity.find("clientId=?1 and instanceCountryId=?2",clientId,tenant.instanceCountryId()).firstResult();if(service==null)throw new NotFoundException("Cliente sin servicios disponibles.");PointEntity point=PointEntity.find("serviceId=?1 and instanceCountryId=?2",service.id,tenant.instanceCountryId()).firstResult();if(point!=null)scope.requireCompanyForCompanyRoles(point.companyId);}
    private List<PointEntity> pointsForClient(UUID clientId){List<ServiceEntity> services=ServiceEntity.list("clientId=?1 and instanceCountryId=?2",clientId,tenant.instanceCountryId());Set<UUID> ids=new HashSet<>(services.stream().map(s->s.id).toList());return ids.isEmpty()?List.of():PointEntity.<PointEntity>list("serviceId in ?1 and instanceCountryId=?2 order by name",ids,tenant.instanceCountryId());}
    private PointEntity pointForClient(UUID pointId,UUID clientId){PointEntity point=PointEntity.find("id=?1 and instanceCountryId=?2",pointId,tenant.instanceCountryId()).firstResult();if(point==null)throw new NotFoundException("Punto no encontrado.");ServiceEntity service=ServiceEntity.find("id=?1 and instanceCountryId=?2",point.serviceId,tenant.instanceCountryId()).firstResult();if(service==null||!clientId.equals(service.clientId))throw new BadRequestException("El Punto no pertenece al Cliente seleccionado.");scope.requireCompanyForCompanyRoles(point.companyId);return point;}
    private String user(){return identity.getPrincipal().getName();}
    private static String normalize(String value,Set<String> allowed,String message){if(blank(value))throw new BadRequestException(message);String normalized=value.trim().toUpperCase(Locale.ROOT);if(!allowed.contains(normalized))throw new BadRequestException(message);return normalized;}
    private static String normalizeDefault(String value,Set<String> allowed,String fallback,String message){return blank(value)?fallback:normalize(value,allowed,message);}
    private static String normalizeIdentity(String value){return value==null?"":value.replaceAll("[^A-Za-z0-9]","").toUpperCase(Locale.ROOT);}
    private static String clean(String value){return value==null?null:value.trim();}
    private static boolean blank(String value){return value==null||value.isBlank();}
}
