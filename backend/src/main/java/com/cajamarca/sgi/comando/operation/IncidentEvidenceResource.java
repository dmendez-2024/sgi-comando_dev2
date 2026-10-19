package com.cajamarca.sgi.comando.operation;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.execution.EvidenceObject;
import com.cajamarca.sgi.comando.storage.*;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import java.io.*;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;

/** Fotos de incidentes: mismo registro y almacenamiento canónico de evidencias, sin agente ficticio. */
@Path("/api/v1/incidents/{incidentId}/evidences")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
public class IncidentEvidenceResource {
    private static final org.jboss.logging.Logger LOG=org.jboss.logging.Logger.getLogger(IncidentEvidenceResource.class);
    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;
    @Inject StorageService storage;
    @ConfigProperty(name="sgi.evidence.max-file-bytes") long maxBytes;

    @org.jboss.resteasy.reactive.server.ServerExceptionMapper
    public Response error(WebApplicationException e){
        return Response.status(e.getResponse().getStatus()).entity(Map.of("message",e.getMessage())).type(MediaType.APPLICATION_JSON).build();
    }

    IncidentNotificationEntity visible(UUID id, boolean lock) {
        IncidentNotificationEntity r=IncidentNotificationEntity.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();
        if(r==null)throw new NotFoundException("Incidente no encontrado");
        if(r.companyId==null){if(!Objects.equals(r.createdBy,scope.username()))throw new ForbiddenException("Borrador fuera de alcance");}
        else scope.requireCompany(r.companyId);
        if(lock)em.refresh(r,LockModeType.PESSIMISTIC_WRITE);
        // Recheck after acquiring the lock in case another editor moved its company.
        if(r.companyId==null){if(!Objects.equals(r.createdBy,scope.username()))throw new ForbiddenException("Borrador fuera de alcance");}
        else scope.requireCompany(r.companyId);
        return r;
    }

    static String field(String section) {
        return switch(section==null?"":section){case "description" -> "descriptionImages";case "resolution" -> "resolutionImages";default -> throw new BadRequestException("Sección de imagen inválida");};
    }
    static String target(String section){field(section);return "description".equals(section)?"INCIDENT_DESCRIPTION":"INCIDENT_RESOLUTION";}

    @POST @Consumes(MediaType.MULTIPART_FORM_DATA) @Transactional
    public ObjectNode upload(@PathParam("incidentId") UUID id,@RestForm("section") String section,
                             @RestForm("clientEvidenceId") UUID clientId,@RestForm("file") FileUpload file) throws IOException {
        IncidentNotificationEntity r=visible(id,true);
        String key=field(section),type=target(section);
        if(clientId==null||file==null)throw new BadRequestException("Imagen e identificador son obligatorios");
        if(file.size()<1||file.size()>maxBytes)throw new BadRequestException("La imagen debe tener entre 1 byte y 5 MiB");
        String ct=ImageSniffer.detect(file.uploadedFile());
        if(ct==null)throw new BadRequestException("Solo se admiten imágenes JPEG, PNG o WebP");
        String hash=Digests.sha256Hex(file.uploadedFile());
        EvidenceObject e=EvidenceObject.byClientId(tenant.instanceCountryId(),clientId);
        if(e!=null&&(!id.equals(e.incidentId)||!type.equals(e.targetType)||!hash.equals(e.sha256)))
            throw new ClientErrorException("El identificador de imagen ya está asociado a otro contenido o sección",409);
        ObjectNode payload=(ObjectNode)mapper.readTree(r.payloadJson);
        ArrayNode items=payload.withArray(key);
        int existing=-1;
        for(int i=0;i<items.size();i++)if(clientId.toString().equals(items.get(i).path("id").asText()))existing=i;
        if(existing<0&&items.size()>=5)throw new BadRequestException("El máximo es 5 imágenes por sección");
        if(e==null){
            e=new EvidenceObject();e.id=UUID.randomUUID();e.instanceCountryId=tenant.instanceCountryId();e.clientEvidenceId=clientId;
            e.uploadBatchId=clientId;e.eventId=id;e.targetId=id;e.incidentId=id;e.targetType=type;e.username=scope.username();
            e.bucket=storage.evidenceBucket();e.objectKey="evidence/"+e.instanceCountryId+"/incidents/"+id+"/"+e.id+"."+ImageSniffer.extension(ct);
            e.contentType=ct;e.sizeBytes=file.size();e.sha256=hash;e.capturedAt=Instant.now();e.receivedAt=e.capturedAt;e.source="GALLERY";e.status="ATTACHED";
            try(InputStream in=Files.newInputStream(file.uploadedFile())){storage.put(e.bucket,e.objectKey,in,file.size(),ct);}
            e.persist();
        }
        String name=file.fileName()==null?"imagen":file.fileName().replace('\\','/');name=name.substring(name.lastIndexOf('/')+1);
        if(name.length()>255)name=name.substring(0,255);
        ObjectNode meta=metadata(e,name);
        if(existing>=0)items.set(existing,meta);else items.add(meta);
        r.payloadJson=payload.toString();r.updatedBy=scope.username();r.updatedAt=Instant.now();
        em.flush();
        LOG.infof("incident_image_upload incident=%s evidence=%s actor=%s section=%s bytes=%d",id,e.id,scope.username(),section,e.sizeBytes);
        return meta;
    }

    @GET
    public ArrayNode list(@PathParam("incidentId") UUID id) throws IOException{return listed(visible(id,false));}

    ArrayNode listed(IncidentNotificationEntity r) throws IOException {
        JsonNode payload=mapper.readTree(r.payloadJson);ArrayNode out=mapper.createArrayNode();
        for(String section:List.of("description","resolution"))for(JsonNode ref:payload.path(field(section))){
            EvidenceObject e=byReference(r.id,ref.path("id").asText(),target(section));
            if(e!=null)out.addObject().put("id",e.id.toString()).put("source","OBJECT").put("section",section);
        }
        return out;
    }

    @GET @Path("/{photoId}/content") @Produces("image/*")
    public Response content(@PathParam("incidentId") UUID id,@PathParam("photoId") UUID photoId) throws IOException {
        IncidentNotificationEntity r=visible(id,false);
        for(JsonNode ref:listed(r))if(photoId.toString().equals(ref.path("id").asText())){
            EvidenceObject e=EvidenceObject.find("id=?1 and instanceCountryId=?2 and incidentId=?3",photoId,tenant.instanceCountryId(),id).firstResult();
            return Response.ok(storage.read(e.bucket,e.objectKey)).type(e.contentType)
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store").header("X-Content-Type-Options","nosniff").build();
        }
        throw new NotFoundException("Fotografía no encontrada");
    }

    /** Payload cannot forge object keys, hashes, tenant or links to another incident. Old metadata remains visibly unavailable. */
    void normalize(IncidentNotificationEntity r,ObjectNode body) throws IOException {
        JsonNode previous=r.payloadJson==null?mapper.createObjectNode():mapper.readTree(r.payloadJson);
        for(String section:List.of("description","resolution")){
            String key=field(section);JsonNode requested=body.has(key)?body.get(key):previous.path(key);
            if(!requested.isMissingNode()&&!requested.isArray())throw new BadRequestException("Lista de imágenes inválida");
            if(requested.size()>5)throw new BadRequestException("El máximo es 5 imágenes por sección");
            ArrayNode result=mapper.createArrayNode();Set<String> ids=new HashSet<>();
            for(JsonNode ref:requested){String id=ref.path("id").asText();
                if(!ids.add(id))throw new BadRequestException("Imagen duplicada");
                EvidenceObject e=byReference(r.id,id,target(section));
                if(e!=null)result.add(metadata(e,ref.path("name").asText("imagen")));
                else {
                    JsonNode legacy=null;for(JsonNode old:previous.path(key))if(id.equals(old.path("id").asText()))legacy=old;
                    if(legacy==null)throw new BadRequestException("La imagen todavía no se ha cargado o no pertenece al incidente");
                    ObjectNode unavailable=legacy.deepCopy();unavailable.put("stored",false);result.add(unavailable);
                }
            }
            body.set(key,result);
        }
    }

    private EvidenceObject byReference(UUID incident,String id,String type){
        UUID client;try{client=UUID.fromString(id);}catch(Exception e){return null;}
        return EvidenceObject.find("instanceCountryId=?1 and incidentId=?2 and clientEvidenceId=?3 and targetType=?4 and status='ATTACHED'",tenant.instanceCountryId(),incident,client,type).firstResult();
    }
    private ObjectNode metadata(EvidenceObject e,String name){return mapper.createObjectNode().put("id",e.clientEvidenceId.toString()).put("evidenceId",e.id.toString())
        .put("name",name).put("size",e.sizeBytes).put("type",e.contentType).put("sha256",e.sha256).put("stored",true);}
}
