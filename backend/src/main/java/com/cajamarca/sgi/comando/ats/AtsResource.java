package com.cajamarca.sgi.comando.ats;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.operations.PostEntity;
import com.cajamarca.sgi.comando.postconfig.PostOperationalConfig;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Path("/api/points/{pointId}/ats")
@Produces(MediaType.APPLICATION_JSON)
public class AtsResource {
    private static final long MAX_ARCHIVE_BYTES = 20L * 1024 * 1024;
    private static final long MAX_UNCOMPRESSED_BYTES = 40L * 1024 * 1024;
    private static final long MAX_ENTRY_BYTES = 20L * 1024 * 1024;
    private static final Set<String> PLAN_TYPES = Set.of("image/png","image/jpeg","image/webp");

    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject SecurityIdentity identity;
    @Inject ObjectMapper mapper;

    public record AtsDto(
        UUID id, UUID pointId, int revisionNo, boolean current,
        String originalFilename, String atsSchemaVersion, String packageType,
        String sourcePtoId, String sourcePtoCode, String sourcePtoName,
        Integer modelRevision, String modelHash,
        String publicationId, String publicationVersion, String publicationStatus, Instant publishedAt,
        String packageHash, String archiveSha256, Double riskIndex,
        String planLevelId, String planLevelCode, String planLevelName,
        String planOriginalFilename, String planContentType, Integer planWidthPx, Integer planHeightPx,
        String planSha256, String importedByUsername, Instant importedAt
    ) {}

    @GET
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
    public AtsDto current(@PathParam("pointId") UUID pointId){
        pointAndScope(pointId);
        AtsPointPackage pkg=currentPackage(pointId);
        if(pkg==null) throw new NotFoundException("El punto todavía no tiene un archivo .ats importado");
        return dto(pkg);
    }

    @GET
    @Path("/history")
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
    public List<AtsDto> history(@PathParam("pointId") UUID pointId){
        pointAndScope(pointId);
        return AtsPointPackage.<AtsPointPackage>list("instanceCountryId=?1 and pointId=?2 order by revisionNo desc",tenant.instanceCountryId(),pointId)
            .stream().map(this::dto).toList();
    }

    @POST
    @Path("/upload")
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
    public AtsDto upload(@PathParam("pointId") UUID pointId, @QueryParam("filename") String filename, byte[] body){
        PointEntity point=pointAndScope(pointId);
        if(filename==null||filename.isBlank()||!filename.toLowerCase(Locale.ROOT).endsWith(".ats")) throw new BadRequestException("Debe seleccionar un archivo con extensión .ats");
        if(body==null||body.length==0) throw new BadRequestException("El archivo .ats está vacío");
        if(body.length>MAX_ARCHIVE_BYTES) throw new BadRequestException("El archivo .ats supera el tamaño máximo permitido de 20 MB");

        ParsedAts parsed=parse(body);
        List<AtsPointPackage> previous=AtsPointPackage.list("instanceCountryId=?1 and pointId=?2",tenant.instanceCountryId(),pointId);
        int nextRevision=1;
        for(AtsPointPackage item:previous){
            nextRevision=Math.max(nextRevision,item.revisionNo+1);
            if(item.current) item.current=false;
        }

        AtsPointPackage pkg=new AtsPointPackage();
        pkg.instanceCountryId=tenant.instanceCountryId();
        pkg.pointId=point.id;
        pkg.revisionNo=nextRevision;
        pkg.current=true;
        pkg.originalFilename=cleanFilename(filename);
        pkg.atsSchemaVersion=parsed.atsSchemaVersion;
        pkg.packageType=parsed.packageType;
        pkg.sourcePtoId=parsed.sourcePtoId;
        pkg.sourcePtoCode=parsed.sourcePtoCode;
        pkg.sourcePtoName=parsed.sourcePtoName;
        pkg.modelRevision=parsed.modelRevision;
        pkg.modelHash=parsed.modelHash;
        pkg.publicationId=parsed.publicationId;
        pkg.publicationVersion=parsed.publicationVersion;
        pkg.publicationStatus=parsed.publicationStatus;
        pkg.publishedAt=parsed.publishedAt;
        pkg.packageHash=parsed.packageHash;
        pkg.archiveSha256=sha256(body);
        pkg.riskIndex=parsed.riskIndex;
        pkg.planLevelId=parsed.planLevelId;
        pkg.planLevelCode=parsed.planLevelCode;
        pkg.planLevelName=parsed.planLevelName;
        pkg.planOriginalFilename=parsed.planOriginalFilename;
        pkg.planInternalPath=parsed.planInternalPath;
        pkg.planContentType=parsed.planContentType;
        pkg.planWidthPx=parsed.planWidthPx;
        pkg.planHeightPx=parsed.planHeightPx;
        pkg.planSha256=parsed.planSha256;
        pkg.atsFile=body;
        pkg.planImage=parsed.planImage;
        pkg.importedByUsername=identity.getPrincipal().getName();
        pkg.persist();

        // Una nueva revisión ATS invalida únicamente el vínculo espacial previo.
        // Se conserva la configuración del Puesto, pero debe revalidarse sobre el plano vigente.
        List<PostEntity> posts=PostEntity.list("pointId=?1 and instanceCountryId=?2",point.id,tenant.instanceCountryId());
        if(!posts.isEmpty()){
            Set<UUID> postIds=new HashSet<>();
            for(PostEntity post:posts){postIds.add(post.id);post.configStatus="PENDING";}
            List<PostOperationalConfig> configs=PostOperationalConfig.list("instanceCountryId=?1 and postId in ?2",tenant.instanceCountryId(),postIds);
            for(PostOperationalConfig config:configs){
                config.configStatus="DRAFT";
                config.atsPackageId=null;
                config.atsLocationX=null;
                config.atsLocationY=null;
                config.atsLocationKey="";
                config.atsLocationLabel="";
            }
        }
        return dto(pkg);
    }

    @GET
    @Path("/plan")
    @Produces({"image/png","image/jpeg","image/webp"})
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
    public Response plan(@PathParam("pointId") UUID pointId){
        pointAndScope(pointId);
        AtsPointPackage pkg=currentPackage(pointId);
        if(pkg==null) throw new NotFoundException("El punto todavía no tiene un archivo .ats importado");
        return Response.ok(pkg.planImage,pkg.planContentType)
            .header(HttpHeaders.CACHE_CONTROL,"no-store")
            .header("X-ATS-Revision",pkg.revisionNo)
            .build();
    }

    @GET
    @Path("/download")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
    public Response download(@PathParam("pointId") UUID pointId){
        pointAndScope(pointId);
        AtsPointPackage pkg=currentPackage(pointId);
        if(pkg==null) throw new NotFoundException("El punto todavía no tiene un archivo .ats importado");
        return Response.ok(pkg.atsFile,MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+pkg.originalFilename.replace("\"","")+"\"")
            .build();
    }

    private PointEntity pointAndScope(UUID pointId){
        if(pointId==null) throw new BadRequestException("pointId obligatorio");
        PointEntity point=PointEntity.find("id=?1 and instanceCountryId=?2",pointId,tenant.instanceCountryId()).firstResult();
        if(point==null) throw new NotFoundException("Punto no encontrado");
        scope.requireCompany(point.companyId);
        return point;
    }

    private AtsPointPackage currentPackage(UUID pointId){
        return AtsPointPackage.find("instanceCountryId=?1 and pointId=?2 and current=true",tenant.instanceCountryId(),pointId).firstResult();
    }

    private AtsDto dto(AtsPointPackage p){
        return new AtsDto(p.id,p.pointId,p.revisionNo,p.current,p.originalFilename,p.atsSchemaVersion,p.packageType,
            p.sourcePtoId,p.sourcePtoCode,p.sourcePtoName,p.modelRevision,p.modelHash,p.publicationId,p.publicationVersion,p.publicationStatus,p.publishedAt,
            p.packageHash,p.archiveSha256,p.riskIndex,p.planLevelId,p.planLevelCode,p.planLevelName,p.planOriginalFilename,p.planContentType,p.planWidthPx,p.planHeightPx,
            p.planSha256,p.importedByUsername,p.createdAt);
    }

    private record ParsedAts(
        String atsSchemaVersion,String packageType,String sourcePtoId,String sourcePtoCode,String sourcePtoName,Integer modelRevision,String modelHash,
        String publicationId,String publicationVersion,String publicationStatus,Instant publishedAt,String packageHash,Double riskIndex,
        String planLevelId,String planLevelCode,String planLevelName,String planOriginalFilename,String planInternalPath,String planContentType,Integer planWidthPx,Integer planHeightPx,String planSha256,byte[] planImage
    ) {}

    private ParsedAts parse(byte[] archive){
        Map<String,byte[]> entries=readZip(archive);
        JsonNode manifest=json(entries,"manifest.json");
        JsonNode pto=json(entries,"model/pto.json");
        String schema=text(manifest,"atsSchemaVersion");
        if(schema==null||schema.isBlank()) throw new BadRequestException("manifest.json no contiene atsSchemaVersion");
        String packageType=text(manifest,"packageType");
        if(packageType==null||!packageType.startsWith("ATS_")) throw new BadRequestException("El paquete no corresponde a una publicación ATS reconocida");

        JsonNode levels=pto.path("levels");
        if(!levels.isArray()||levels.isEmpty()) throw new BadRequestException("model/pto.json no contiene niveles con un plano");
        JsonNode chosen=null;
        for(JsonNode level:levels){if(level.hasNonNull("plan")){chosen=level;break;}}
        if(chosen==null) throw new BadRequestException("El archivo .ats no contiene un plano asociado a ningún nivel");
        JsonNode plan=chosen.path("plan");
        String planPath=text(plan,"internalPath");
        if(planPath==null||planPath.isBlank()) throw new BadRequestException("El plano ATS no contiene internalPath");
        byte[] planImage=entries.get(planPath);
        if(planImage==null||planImage.length==0) throw new BadRequestException("El plano declarado por ATS no está incluido en el paquete: "+planPath);
        String contentType=text(plan,"contentType");
        if(contentType==null||!PLAN_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) throw new BadRequestException("Formato de imagen de plano no soportado: "+contentType);
        String expectedPlanSha=text(plan,"sha256");
        if(expectedPlanSha!=null&&!expectedPlanSha.equalsIgnoreCase(sha256(planImage))) throw new BadRequestException("La integridad SHA-256 del plano no coincide con el manifiesto ATS");

        JsonNode publication=manifest.path("publication");
        Instant publishedAt=parseInstant(text(publication,"publishedAt"));
        Double riskIndex=findRiskIndex(entries);
        return new ParsedAts(
            schema,packageType,text(manifest,"ptoId"),text(manifest,"ptoCode"),text(manifest,"ptoName"),integer(manifest,"modelRevision"),text(manifest,"modelHash"),
            text(publication,"publicationId"),text(publication,"publicationVersion"),text(publication,"status"),publishedAt,text(publication,"packageHash"),riskIndex,
            text(chosen,"id"),text(chosen,"code"),text(chosen,"name"),text(plan,"originalFilename"),planPath,contentType,integer(plan,"widthPx"),integer(plan,"heightPx"),expectedPlanSha,planImage
        );
    }

    private Map<String,byte[]> readZip(byte[] bytes){
        Map<String,byte[]> entries=new HashMap<>();
        long total=0;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)){
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){
                String name=entry.getName().replace('\\','/');
                if(entry.isDirectory()) continue;
                if(name.startsWith("/")||name.contains("../")) throw new BadRequestException("El archivo .ats contiene una ruta interna inválida");
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                byte[] buffer=new byte[8192];
                int read;
                long entryBytes=0;
                while((read=zip.read(buffer))!=-1){
                    entryBytes+=read;
                    total+=read;
                    if(entryBytes>MAX_ENTRY_BYTES||total>MAX_UNCOMPRESSED_BYTES) throw new BadRequestException("El archivo .ats excede los límites seguros de descompresión");
                    out.write(buffer,0,read);
                }
                entries.put(name,out.toByteArray());
            }
        }catch(BadRequestException e){throw e;}catch(Exception e){throw new BadRequestException("No se pudo leer el archivo .ats como paquete ZIP válido: "+e.getMessage());}
        if(!entries.containsKey("manifest.json")||!entries.containsKey("model/pto.json")) throw new BadRequestException("El archivo .ats no contiene manifest.json y model/pto.json");
        return entries;
    }

    private JsonNode json(Map<String,byte[]> entries,String path){
        try{return mapper.readTree(entries.get(path));}catch(Exception e){throw new BadRequestException("JSON ATS inválido en "+path+": "+e.getMessage());}
    }

    private Double findRiskIndex(Map<String,byte[]> entries){
        Set<String> names=Set.of("riskindex","pointriskindex","indicederiesgodepunto","indicederiesgo","riesgopunto");
        for(Map.Entry<String,byte[]> entry:entries.entrySet()){
            if(!entry.getKey().endsWith(".json")) continue;
            try{
                JsonNode node=mapper.readTree(entry.getValue());
                Double found=findNumber(node,names);
                if(found!=null) return found;
            }catch(Exception ignored){}
        }
        return null;
    }

    private Double findNumber(JsonNode node,Set<String> names){
        if(node==null) return null;
        if(node.isObject()){
            Iterator<Map.Entry<String,JsonNode>> fields=node.fields();
            while(fields.hasNext()){
                Map.Entry<String,JsonNode> field=fields.next();
                String normalized=Normalizer.normalize(field.getKey(),Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
                if(names.contains(normalized)&&field.getValue().isNumber()) return field.getValue().asDouble();
                Double nested=findNumber(field.getValue(),names);
                if(nested!=null) return nested;
            }
        }else if(node.isArray()){
            for(JsonNode child:node){Double nested=findNumber(child,names);if(nested!=null)return nested;}
        }
        return null;
    }

    private String text(JsonNode node,String field){JsonNode value=node==null?null:node.get(field);return value==null||value.isNull()?null:value.asText();}
    private Integer integer(JsonNode node,String field){JsonNode value=node==null?null:node.get(field);return value==null||!value.isNumber()?null:value.asInt();}
    private Instant parseInstant(String value){try{return value==null?null:Instant.parse(value);}catch(Exception e){return null;}}
    private String cleanFilename(String filename){
        String normalized=filename.replace('\\','/');
        int index=normalized.lastIndexOf('/');
        String base=index>=0?normalized.substring(index+1):normalized;
        if(base.length()>255) base=base.substring(0,255);
        return base;
    }
    private String sha256(byte[] bytes){
        try{
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            byte[] hash=md.digest(bytes);
            StringBuilder out=new StringBuilder(hash.length*2);
            for(byte b:hash) out.append(String.format("%02x",b));
            return out.toString();
        }catch(Exception e){throw new IllegalStateException(e);}
    }
}
