package com.cajamarca.sgi.comando.storage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;

/** Operaciones sobre las fotos estándar (hasta 5) de cualquier destino: agregar, leer, quitar reordenando y copiar a una nueva versión. */
@ApplicationScoped
public class StandardReferenceImages {
    public record Dto(UUID id, int position, String originalName, String contentType) {}

    @Inject StandardImageStore store;

    public List<Dto> dtos(String targetType, UUID targetId) {
        return StandardReferenceImage.of(targetType, targetId).stream().map(i -> new Dto(i.id, i.position, i.originalName, i.contentType)).toList();
    }

    /** Agrega la foto al final. module: carpeta en MinIO (patrol, consignment, bitacora). */
    public StandardReferenceImage add(UUID tenant, String targetType, UUID targetId, String module, FileUpload file) {
        if (file == null) throw new BadRequestException("Imagen obligatoria");
        if (file.size() > StandardImageStore.MAX_BYTES) throw new BadRequestException("La foto estándar no puede superar 5 MB");
        long count = StandardReferenceImage.countOf(targetType, targetId);
        if (count >= StandardReferenceImage.MAX) throw new BadRequestException("Ya tiene " + StandardReferenceImage.MAX + " fotos estándar (máximo)");
        byte[] bytes;
        try { bytes = Files.readAllBytes(file.uploadedFile()); } catch (IOException e) { throw new BadRequestException("No se pudo leer la imagen"); }
        StandardImageStore.Stored s = store.save(module, bytes);
        StandardReferenceImage img = new StandardReferenceImage();
        img.id = UUID.randomUUID(); img.instanceCountryId = tenant; img.targetType = targetType; img.targetId = targetId; img.position = (int) count + 1;
        img.originalName = file.fileName() == null || file.fileName().isBlank() ? "foto-estandar" : file.fileName().trim();
        img.contentType = s.contentType(); img.objectKey = s.objectKey(); img.sha256 = s.sha256(); img.sizeBytes = s.size(); img.createdAt = Instant.now();
        img.persist();
        return img;
    }

    public StandardReferenceImage get(String targetType, UUID targetId, UUID imageId) {
        StandardReferenceImage img = StandardReferenceImage.find("id=?1 and targetType=?2 and targetId=?3", imageId, targetType, targetId).firstResult();
        if (img == null) throw new NotFoundException("Foto estándar no encontrada");
        return img;
    }

    public byte[] read(StandardReferenceImage img) { return store.read(img.objectKey, null); }

    /** Quita la foto y reordena las demás (1..n). Devuelve cuántas quedan. */
    public int delete(String targetType, UUID targetId, UUID imageId) {
        get(targetType, targetId, imageId).delete();
        int i = 1;
        for (StandardReferenceImage img : StandardReferenceImage.of(targetType, targetId)) if (!img.id.equals(imageId)) img.position = i++;
        return i - 1;
    }

    /** Copia las fotos estándar a la nueva versión del destino (mismo objeto en MinIO). */
    public void copy(String targetType, UUID from, UUID to) {
        for (StandardReferenceImage o : StandardReferenceImage.of(targetType, from)) {
            StandardReferenceImage n = new StandardReferenceImage();
            n.id = UUID.randomUUID(); n.instanceCountryId = o.instanceCountryId; n.targetType = targetType; n.targetId = to; n.position = o.position;
            n.originalName = o.originalName; n.contentType = o.contentType; n.objectKey = o.objectKey; n.sha256 = o.sha256; n.sizeBytes = o.sizeBytes; n.createdAt = o.createdAt;
            n.persist();
        }
    }
}
