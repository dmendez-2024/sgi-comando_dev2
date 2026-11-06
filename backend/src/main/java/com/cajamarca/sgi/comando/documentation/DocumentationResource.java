package com.cajamarca.sgi.comando.documentation;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.companies.Company;
import com.cajamarca.sgi.comando.storage.Digests;
import com.cajamarca.sgi.comando.storage.StorageService;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;

@Path("/api/documents")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({
    "PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL",
    "DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA",
    "ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"
})
public class DocumentationResource {
    private static final Set<String> STATUSES=Set.of("VIGENTE","NO_VIGENTE");
    private static final byte[] PDF_MAGIC="%PDF-".getBytes(StandardCharsets.US_ASCII);

    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject StorageService storage;
    @Inject EntityManager em;
    @ConfigProperty(name="sgi.documents.max-file-bytes") long maxFileBytes;

    public record DocumentView(UUID id,String name,String fileName,String version,String status,UUID companyId,
                               Instant updatedAt,long sizeBytes,String uploadedBy){
        static DocumentView of(InstitutionalDocument document){
            return new DocumentView(document.id,document.name,document.originalName,document.version,document.status,
                document.companyId,document.updatedAt,document.sizeBytes,document.uploadedBy);
        }
    }

    @GET
    public List<DocumentView> list(){
        UUID instanceCountryId=tenant.instanceCountryId();
        List<InstitutionalDocument> documents;
        if(scope.countryWide()){
            documents=InstitutionalDocument.list("instanceCountryId=?1 order by updatedAt desc,name",instanceCountryId);
        }else{
            Set<UUID> companies=scope.allowedCompanyIdsForCompanyRoles();
            documents=companies.isEmpty()
                ?InstitutionalDocument.list("instanceCountryId=?1 and companyId is null order by updatedAt desc,name",instanceCountryId)
                :InstitutionalDocument.list("instanceCountryId=?1 and (companyId is null or companyId in ?2) order by updatedAt desc,name",instanceCountryId,companies);
        }
        return documents.stream().map(DocumentView::of).toList();
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @RolesAllowed("PRESIDENTE")
    @Transactional
    public DocumentView upload(@RestForm("name") String name,
                               @RestForm("version") String version,
                               @RestForm("status") String status,
                               @RestForm("companyId") String companyIdValue,
                               @RestForm("file") FileUpload file) throws Exception {
        name=required(name,"El nombre del documento es obligatorio",180);
        version=required(version,"La versión es obligatoria",40);
        status=status==null?"":status.trim().toUpperCase(Locale.ROOT);
        if(!STATUSES.contains(status))throw new BadRequestException("Estado de documento inválido");
        if(file==null||file.size()<1)throw new BadRequestException("Seleccione un archivo PDF");
        if(file.size()>maxFileBytes)throw new BadRequestException("El PDF supera el tamaño máximo de 20 MiB");
        if(!isPdf(file))throw new BadRequestException("El archivo no es un PDF válido");

        UUID companyId=parseCompany(companyIdValue);
        if(companyId!=null){
            Company company=Company.find("id=?1 and instanceCountryId=?2",companyId,tenant.instanceCountryId()).firstResult();
            if(company==null)throw new BadRequestException("La Compañía seleccionada no existe");
            scope.requireCompany(companyId);
        }

        InstitutionalDocument document=new InstitutionalDocument();
        document.id=UUID.randomUUID();
        document.instanceCountryId=tenant.instanceCountryId();
        document.companyId=companyId;
        document.name=name;
        document.version=version;
        document.status=status;
        document.originalName=safeFileName(file.fileName());
        document.contentType="application/pdf";
        document.bucket=storage.documentsBucket();
        document.objectKey="documents/"+document.instanceCountryId+"/"+document.id+".pdf";
        document.sha256=Digests.sha256Hex(file.uploadedFile());
        document.sizeBytes=file.size();
        document.uploadedBy=scope.username();
        document.createdAt=Instant.now();
        document.updatedAt=document.createdAt;

        boolean stored=false;
        try(InputStream input=Files.newInputStream(file.uploadedFile())){
            storage.put(document.bucket,document.objectKey,input,document.sizeBytes,document.contentType);
            stored=true;
            document.persist();
            em.flush();
            return DocumentView.of(document);
        }catch(RuntimeException exception){
            if(stored)try{storage.delete(document.bucket,document.objectKey);}catch(RuntimeException ignored){}
            if(isDuplicate(exception))throw new ClientErrorException("Ya existe esa versión del documento para el mismo alcance",409);
            throw exception;
        }
    }

    @GET
    @Path("/{id}/content")
    @Produces("application/pdf")
    public Response download(@PathParam("id") UUID id){
        InstitutionalDocument document=visible(id);
        byte[] content=storage.read(document.bucket,document.objectKey);
        String downloadName=document.originalName.replace("\"","").replace("\r","").replace("\n","");
        return Response.ok(content,document.contentType)
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+downloadName+"\"")
            .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
            .header("X-Content-Type-Options","nosniff")
            .build();
    }

    InstitutionalDocument visible(UUID id){
        InstitutionalDocument document=InstitutionalDocument.find("id=?1 and instanceCountryId=?2",id,tenant.instanceCountryId()).firstResult();
        if(document==null)throw new NotFoundException("Documento no encontrado");
        if(document.companyId!=null&&!scope.countryWide())scope.requireCompanyForCompanyRoles(document.companyId);
        return document;
    }

    private static UUID parseCompany(String value){
        if(value==null||value.isBlank())return null;
        try{return UUID.fromString(value.trim());}catch(IllegalArgumentException exception){throw new BadRequestException("Compañía inválida");}
    }

    private static String required(String value,String message,int max){
        String normalized=value==null?"":value.trim();
        if(normalized.isEmpty())throw new BadRequestException(message);
        if(normalized.length()>max)throw new BadRequestException(message+" (máximo "+max+" caracteres)");
        return normalized;
    }

    private static boolean isPdf(FileUpload file)throws Exception{
        try(InputStream input=Files.newInputStream(file.uploadedFile())){
            return Arrays.equals(PDF_MAGIC,input.readNBytes(PDF_MAGIC.length));
        }
    }

    private static String safeFileName(String value){
        String name=value==null?"documento.pdf":value.replace('\\','/');
        name=name.substring(name.lastIndexOf('/')+1).replace("\"","").replace("\r","").replace("\n","");
        if(name.isBlank())name="documento.pdf";
        if(name.length()>255)name=name.substring(name.length()-255);
        return name.toLowerCase(Locale.ROOT).endsWith(".pdf")?name:name+".pdf";
    }

    private static boolean isDuplicate(Throwable exception){
        for(Throwable current=exception;current!=null;current=current.getCause())
            if(current.getMessage()!=null&&current.getMessage().contains("uq_institutional_document_version"))return true;
        return false;
    }
}
