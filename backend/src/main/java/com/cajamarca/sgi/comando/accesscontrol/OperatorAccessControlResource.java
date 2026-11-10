package com.cajamarca.sgi.comando.accesscontrol;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.operations.ServiceEntity;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

@Path("/api/v1/operator/access-control")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD"})
public class OperatorAccessControlResource {
    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;

    public record ResolveRequest(UUID pointId,String qrValue) {}
    public record PersonDto(UUID entryId,String identification,String lastNames,String firstNames,String accreditation,String organizationalReference,LocalDate validUntil) {}
    public record ResolveResponse(String decision,String message,PersonDto person) {}
    public record CatalogEntry(String tokenHash,String identification,String lastNames,String firstNames,String accreditation,String organizationalReference,LocalDate validFrom,LocalDate validUntil,String decision,String message) {}

    @POST @Path("/qr/resolve") @Consumes(MediaType.APPLICATION_JSON)
    public ResolveResponse resolve(ResolveRequest request){
        if(request==null||request.pointId()==null||request.qrValue()==null||request.qrValue().isBlank())throw new BadRequestException("Punto y código QR son obligatorios.");
        PointEntity point=point(request.pointId());String hash=hashQr(request.qrValue());
        AccessControlEntry white=AccessControlEntry.find("instanceCountryId=?1 and listType='WHITE' and qrTokenHash=?2",tenant.instanceCountryId(),hash).firstResult();
        if(white==null||white.qrRevokedAt!=null)return new ResolveResponse("NOT_FOUND","El código QR no corresponde a una autorización vigente.",null);
        requireClient(point,white.clientId);
        if(!applies(white.id,point.id))return new ResolveResponse("NOT_APPLICABLE","La autorización no aplica para este Punto.",null);
        if(!valid(white))return new ResolveResponse("NOT_VALID","La autorización está inactiva o fuera de vigencia.",null);
        AccessControlEntry black=activeBlack(white,point.id);
        PersonDto person=new PersonDto(white.id,white.identification,white.lastNames,white.firstNames,white.accreditation,white.organizationalReference,white.validUntil);
        if(black!=null)return new ResolveResponse("DENIED",black.observations==null?"La persona tiene prohibido el ingreso a este Punto.":black.observations,person);
        return new ResolveResponse("AUTHORIZED","Autorización vigente. Verifique los datos antes de registrar el ingreso.",person);
    }

    @GET @Path("/catalog")
    public List<CatalogEntry> catalog(@QueryParam("pointId") UUID pointId){
        PointEntity point=point(pointId);ServiceEntity service=service(point);LocalDate today=LocalDate.now();
        List<AccessControlEntry> whites=AccessControlEntry.list("instanceCountryId=?1 and clientId=?2 and listType='WHITE' and status='ACTIVE' and qrRevokedAt is null",tenant.instanceCountryId(),service.clientId);
        return whites.stream().filter(row->row.qrTokenHash!=null&&applies(row.id,point.id)&&!today.isBefore(row.validFrom)&&!today.isAfter(row.validUntil)).map(row->{AccessControlEntry black=activeBlack(row,point.id);return new CatalogEntry(row.qrTokenHash,row.identification,row.lastNames,row.firstNames,row.accreditation,row.organizationalReference,row.validFrom,row.validUntil,black==null?"AUTHORIZED":"DENIED",black==null?"Autorización vigente.":black.observations);}).toList();
    }

    private AccessControlEntry activeBlack(AccessControlEntry white,UUID pointId){List<AccessControlEntry> rows=AccessControlEntry.list("instanceCountryId=?1 and clientId=?2 and listType='BLACK' and objectType=?3 and identificationNormalized=?4",tenant.instanceCountryId(),white.clientId,white.objectType,white.identificationNormalized);return rows.stream().filter(this::valid).filter(row->applies(row.id,pointId)).findFirst().orElse(null);}
    private boolean valid(AccessControlEntry row){LocalDate today=LocalDate.now();return "ACTIVE".equals(row.status)&&!today.isBefore(row.validFrom)&&!today.isAfter(row.validUntil);}
    private boolean applies(UUID entryId,UUID pointId){return AccessControlEntryPoint.count("entryId=?1 and pointId=?2 and instanceCountryId=?3",entryId,pointId,tenant.instanceCountryId())>0;}
    private PointEntity point(UUID id){if(id==null)throw new BadRequestException("Punto obligatorio.");PointEntity point=PointEntity.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();if(point==null)throw new NotFoundException("Punto no encontrado.");scope.requireCompanyForCompanyRoles(point.companyId);return point;}
    private ServiceEntity service(PointEntity point){ServiceEntity service=ServiceEntity.find("id=?1 and instanceCountryId=?2",point.serviceId,tenant.instanceCountryId()).firstResult();if(service==null)throw new NotFoundException("Servicio no encontrado.");return service;}
    private void requireClient(PointEntity point,UUID clientId){if(!clientId.equals(service(point).clientId))throw new BadRequestException("La autorización pertenece a otro Cliente.");}
    private static String hashQr(String value){String trimmed=value.trim();if(!trimmed.startsWith("SGI-WL1."))throw new BadRequestException("Formato de código QR no reconocido.");try{byte[] token=Base64.getUrlDecoder().decode(trimmed.substring("SGI-WL1.".length()));if(token.length!=32)throw new BadRequestException("Código QR inválido.");return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token));}catch(IllegalArgumentException ex){throw new BadRequestException("Código QR inválido.");}catch(Exception ex){throw new InternalServerErrorException("No fue posible validar el código QR.");}}
}
