package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.execution.EvidenceObject;
import com.cajamarca.sgi.comando.storage.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import java.io.*;
import java.nio.file.Files;
import java.time.*;
import java.util.*;

/**
 * Fotos del agente por multipart/form-data: campo "metadata" (JSON como texto) y 1..N partes "files".
 * Idempotente por clientEvidenceId: reenviar la misma foto responde ALREADY_STORED; otra foto con el mismo id, 409.
 */
@Path("/api/v1/operator") @Authenticated @Produces(MediaType.APPLICATION_JSON)
public class OperatorEvidenceResource {
    public record UploadResult(UUID clientEvidenceId, UUID evidenceId, String status, String reason, List<String> flags) {}
    public record UploadResponse(List<UploadResult> results) {}

    @Inject OperatorContext ctx;
    @Inject OperatorPatrols patrols;
    @Inject OperatorTasks tasks;
    @Inject StandardReferenceImages references;
    @Inject StorageService storage;
    @Inject StandardImageStore standardImages;
    @Inject TenantContext tenant;
    @Inject ObjectMapper mapper;
    @ConfigProperty(name="sgi.evidence.max-file-bytes") long maxFileBytes;
    @ConfigProperty(name="sgi.evidence.max-files-per-request") int maxFiles;

    @org.jboss.resteasy.reactive.server.ServerExceptionMapper
    public Response mapError(WebApplicationException e) { return OperatorErrors.withMessage(e); }

    @POST @Path("/evidences") @Consumes(MediaType.MULTIPART_FORM_DATA) @Transactional
    public UploadResponse upload(@RestForm("metadata") String metadataJson, @RestForm("files") List<FileUpload> files) throws IOException {
        UUID employee = ctx.employee();
        EvidenceUploadContract.Metadata meta = EvidenceUploadContract.parse(metadataJson, mapper);
        List<FileUpload> uploads = files == null ? List.of() : files;
        Map<UUID,Integer> index = EvidenceUploadContract.matchFiles(meta, uploads.stream().map(FileUpload::fileName).toList(), maxFiles, Instant.now());
        OperatorContext.Assignment a = ctx.assignment(meta.assignmentId(), employee);
        OperatorTasks.Target target = tasks.require(meta.targetType(), meta.targetId(), a.post());
        if (!target.requiresEvidence()) throw new BadRequestException("Este Hito no requiere fotos");
        ctx.lock(meta.assignmentId());
        UUID t = tenant.instanceCountryId();

        // 1) Conflictos antes de guardar nada: el mismo clientEvidenceId solo puede repetirse con el mismo contenido.
        Map<UUID,String> sha = new HashMap<>();
        for (EvidenceUploadContract.Item it : meta.items()) {
            FileUpload f = uploads.get(index.get(it.clientEvidenceId()));
            sha.put(it.clientEvidenceId(), f.size() > maxFileBytes ? null : Digests.sha256Hex(f.uploadedFile()));
            EvidenceObject prev = EvidenceObject.byClientId(t, it.clientEvidenceId());
            if (prev != null && (!prev.username.equals(ctx.username()) || !prev.eventId.equals(meta.eventId()) || !prev.sha256.equals(sha.get(it.clientEvidenceId()))))
                throw new ClientErrorException("La foto " + it.clientEvidenceId() + " ya fue cargada con otro contenido", 409);
        }

        // 2) Guardar cada foto válida; las inválidas se informan sin cortar el resto del envío.
        long stored = EvidenceObject.count("instanceCountryId=?1 and eventId=?2 and targetId=?3", t, meta.eventId(), meta.targetId());
        List<UploadResult> results = new ArrayList<>();
        for (EvidenceUploadContract.Item it : meta.items()) {
            FileUpload f = uploads.get(index.get(it.clientEvidenceId()));
            EvidenceObject prev = EvidenceObject.byClientId(t, it.clientEvidenceId());
            if (prev != null) { results.add(new UploadResult(it.clientEvidenceId(), prev.id, "ALREADY_STORED", null, flags(prev.flags))); continue; }
            if (f.size() > maxFileBytes) { results.add(rejected(it, "FILE_TOO_LARGE")); continue; }
            String ct = ImageSniffer.detect(f.uploadedFile());
            if (ct == null) { results.add(rejected(it, "UNSUPPORTED_FORMAT")); continue; }
            if (!sha.get(it.clientEvidenceId()).equals(it.sha256())) { results.add(rejected(it, "CHECKSUM_MISMATCH")); continue; }
            if (stored >= 1) { results.add(rejected(it, "TOO_MANY_PHOTOS")); continue; }

            EvidenceObject e = new EvidenceObject();
            e.id = UUID.randomUUID(); e.instanceCountryId = t; e.clientEvidenceId = it.clientEvidenceId(); e.uploadBatchId = meta.uploadBatchId();
            e.eventId = meta.eventId(); e.assignmentId = meta.assignmentId(); e.employeeId = employee; e.username = ctx.username();
            e.targetType = meta.targetType(); e.targetId = meta.targetId(); e.bucket = storage.evidenceBucket();
            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
            e.objectKey = String.format("evidence/%04d/%02d/%s/%s.%s", now.getYear(), now.getMonthValue(), meta.eventId(), e.id, ImageSniffer.extension(ct));
            e.contentType = ct; e.sizeBytes = f.size(); e.sha256 = it.sha256(); e.capturedAt = it.capturedAt();
            e.latitude = it.latitude(); e.longitude = it.longitude(); e.accuracyM = it.accuracyM(); e.source = it.source();
            e.flags = String.join(",", flagsFor(e, target)); e.status = "STORED"; e.receivedAt = Instant.now();
            try (InputStream in = Files.newInputStream(f.uploadedFile())) { storage.put(e.bucket, e.objectKey, in, f.size(), ct); }
            e.persist();
            stored++;
            results.add(new UploadResult(it.clientEvidenceId(), e.id, "STORED", null, flags(e.flags)));
        }
        return new UploadResponse(results);
    }

    /** Foto estándar de un Hito, evidencia de Consigna, campo de Bitácora o del propio Puesto (relevo), para que el agente la use como guía. */
    @GET @Path("/standard-images/{imageId}")
    public Response standardImage(@PathParam("imageId") UUID imageId, @QueryParam("assignmentId") UUID assignmentId) {
        if (assignmentId == null) throw new BadRequestException("assignmentId es obligatorio");
        OperatorContext.Assignment a = ctx.assignment(assignmentId, ctx.employee());
        StandardReferenceImage img = StandardReferenceImage.find("id=?1 and instanceCountryId=?2", imageId, tenant.instanceCountryId()).firstResult();
        if (img == null) throw new NotFoundException("Foto estándar no encontrada");
        if (StandardReferenceImage.POST_CONFIG.equals(img.targetType)) { if (!img.targetId.equals(a.post().id)) throw new NotFoundException("Foto estándar no encontrada"); }
        else tasks.require(img.targetType, img.targetId, a.post());
        return Response.ok(references.read(img)).type(img.contentType).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    /** Compatibilidad: foto estándar de un Hito de patrulla. */
    @GET @Path("/checkpoints/{checkpointId}/standard-images/{imageId}")
    public Response checkpointStandardImage(@PathParam("checkpointId") UUID checkpointId, @PathParam("imageId") UUID imageId, @QueryParam("assignmentId") UUID assignmentId) {
        return standardImage(imageId, assignmentId);
    }

    private List<String> flagsFor(EvidenceObject e, OperatorTasks.Target t) {
        List<String> f = new ArrayList<>();
        if ("GALLERY".equals(e.source)) f.add("GALLERY");
        if (e.latitude != null && e.longitude != null && t.latitude() != null && t.longitude() != null && t.radiusM() != null) {
            double meters = GeoDistance.meters(e.latitude, e.longitude, t.latitude(), t.longitude());
            e.referenceDistanceM = (int) Math.round(meters); e.referenceRadiusM = (int) Math.round(t.radiusM());
            if (meters > t.radiusM()) f.add("OUT_OF_RANGE");
        }
        if (EvidenceObject.count("instanceCountryId=?1 and sha256=?2 and eventId<>?3", e.instanceCountryId, e.sha256, e.eventId) > 0) f.add("SUSPECTED_REUSE");
        return f;
    }

    private static List<String> flags(String csv) { return csv == null || csv.isBlank() ? List.of() : List.of(csv.split(",")); }
    private static UploadResult rejected(EvidenceUploadContract.Item it, String reason) { return new UploadResult(it.clientEvidenceId(), null, "REJECTED", reason, List.of()); }
}
