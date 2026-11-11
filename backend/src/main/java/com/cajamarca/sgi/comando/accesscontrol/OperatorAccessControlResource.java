package com.cajamarca.sgi.comando.accesscontrol;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.operations.ServiceEntity;
import com.cajamarca.sgi.comando.operator.OperatorContext;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Path("/api/v1/operator/access-control")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD"})
public class OperatorAccessControlResource {
    private static final ZoneId OPERATING_ZONE=ZoneId.of("America/Guayaquil");
    @Inject TenantContext tenant;
    @Inject OperatorContext operator;

    public record ResolveRequest(UUID assignmentId,UUID pointId,String qrValue) {}
    public record PersonDto(UUID entryId,String identification,String lastNames,String firstNames,String accreditation,String organizationalReference,LocalDate validUntil) {}
    public record ResolveResponse(String decision,String message,PersonDto person) {}
    public record CatalogEntry(String tokenHash,String identification,String lastNames,String firstNames,String accreditation,String organizationalReference,LocalDate validFrom,LocalDate validUntil,String decision,String message) {}

    @POST @Path("/qr/resolve") @Consumes(MediaType.APPLICATION_JSON)
    public ResolveResponse resolve(ResolveRequest request){
        if(request==null||request.assignmentId()==null||request.pointId()==null||request.qrValue()==null||request.qrValue().isBlank())throw new BadRequestException("Asignación, Punto y código QR son obligatorios.");
        PointEntity point=assignedPoint(request.assignmentId(),request.pointId());ServiceEntity service=service(point);String hash=hashQr(request.qrValue());
        AccessControlPerson person=AccessControlPerson.find("instanceCountryId=?1 and qrTokenHash=?2",tenant.instanceCountryId(),hash).firstResult();
        if(person==null||person.qrRevokedAt!=null)return new ResolveResponse("NOT_FOUND","El código QR no corresponde a una persona con credencial vigente.",null);
        List<AccessControlEntry> authorizations=AccessControlEntry.list("instanceCountryId=?1 and personId=?2 and clientId=?3 and listType='WHITE'",tenant.instanceCountryId(),person.id,service.clientId);
        List<AccessControlEntry> applicable=authorizations.stream().filter(row->applies(row.id,point.id)).toList();
        if(applicable.isEmpty())return new ResolveResponse("NOT_APPLICABLE","La persona no tiene autorización para este Punto.",null);
        AccessControlEntry white=applicable.stream().filter(this::valid).max(Comparator.comparing(row->row.validUntil)).orElse(null);
        if(white==null)return new ResolveResponse("NOT_VALID","La persona no tiene una autorización vigente para este Punto.",null);
        AccessControlEntry black=activeBlack(white,point.id);
        PersonDto result=new PersonDto(white.id,person.identification,person.lastNames,person.firstNames,white.accreditation,white.organizationalReference,white.validUntil);
        if(black!=null)return new ResolveResponse("DENIED",black.observations==null?"La persona tiene prohibido el ingreso a este Punto.":black.observations,result);
        return new ResolveResponse("AUTHORIZED","Autorización vigente. Verifique los datos antes de registrar el ingreso.",result);
    }

    @GET @Path("/catalog")
    public List<CatalogEntry> catalog(@QueryParam("assignmentId") UUID assignmentId,@QueryParam("pointId") UUID pointId){
        PointEntity point=assignedPoint(assignmentId,pointId);ServiceEntity service=service(point);LocalDate today=today();
        List<AccessControlEntry> whites=AccessControlEntry.list("instanceCountryId=?1 and clientId=?2 and listType='WHITE' and status='ACTIVE'",tenant.instanceCountryId(),service.clientId);
        return whites.stream().filter(row->applies(row.id,point.id)&&!today.isBefore(row.validFrom)&&!today.isAfter(row.validUntil)).map(row->new AbstractMap.SimpleEntry<>(row,person(row))).filter(pair->pair.getValue().qrTokenHash!=null&&pair.getValue().qrRevokedAt==null).map(pair->{AccessControlEntry row=pair.getKey();AccessControlPerson person=pair.getValue();AccessControlEntry black=activeBlack(row,point.id);return new CatalogEntry(person.qrTokenHash,person.identification,person.lastNames,person.firstNames,row.accreditation,row.organizationalReference,row.validFrom,row.validUntil,black==null?"AUTHORIZED":"DENIED",black==null?"Autorización vigente.":black.observations);}).toList();
    }

    private AccessControlEntry activeBlack(AccessControlEntry white,UUID pointId){List<AccessControlEntry> rows=AccessControlEntry.list("instanceCountryId=?1 and clientId=?2 and listType='BLACK' and personId=?3",tenant.instanceCountryId(),white.clientId,white.personId);return rows.stream().filter(this::valid).filter(row->applies(row.id,pointId)).findFirst().orElse(null);}
    private AccessControlPerson person(AccessControlEntry row){AccessControlPerson person=AccessControlPerson.find("id=?1 and instanceCountryId=?2",row.personId,tenant.instanceCountryId()).firstResult();if(person==null)throw new NotFoundException("Identidad asociada no encontrada.");return person;}
    private boolean valid(AccessControlEntry row){LocalDate today=today();return "ACTIVE".equals(row.status)&&!today.isBefore(row.validFrom)&&!today.isAfter(row.validUntil);}
    private static LocalDate today(){return LocalDate.now(OPERATING_ZONE);}
    private boolean applies(UUID entryId,UUID pointId){return AccessControlEntryPoint.count("entryId=?1 and pointId=?2 and instanceCountryId=?3",entryId,pointId,tenant.instanceCountryId())>0;}
    private PointEntity assignedPoint(UUID assignmentId,UUID pointId){
        if(assignmentId==null||pointId==null)throw new BadRequestException("Asignación y Punto son obligatorios.");
        OperatorContext.Assignment assignment=operator.assignment(assignmentId,operator.employee());
        Instant now=Instant.now();
        if(now.isBefore(assignment.shift().startsAt.minusSeconds(3540))||now.isAfter(assignment.shift().endsAt))
            throw new ForbiddenException("La asignación no está vigente para validar accesos.");
        if(!assignment.point().id.equals(pointId))throw new ForbiddenException("El Punto no corresponde a la asignación vigente.");
        return assignment.point();
    }
    private ServiceEntity service(PointEntity point){ServiceEntity service=ServiceEntity.find("id=?1 and instanceCountryId=?2",point.serviceId,tenant.instanceCountryId()).firstResult();if(service==null)throw new NotFoundException("Servicio no encontrado.");return service;}
    private void requireClient(PointEntity point,UUID clientId){if(!clientId.equals(service(point).clientId))throw new BadRequestException("La autorización pertenece a otro Cliente.");}
    private static String hashQr(String value){String trimmed=value.trim();if(!trimmed.startsWith("SGI-WL1."))throw new BadRequestException("Formato de código QR no reconocido.");try{byte[] token=Base64.getUrlDecoder().decode(trimmed.substring("SGI-WL1.".length()));if(token.length!=32)throw new BadRequestException("Código QR inválido.");return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token));}catch(IllegalArgumentException ex){throw new BadRequestException("Código QR inválido.");}catch(Exception ex){throw new InternalServerErrorException("No fue posible validar el código QR.");}}
}
