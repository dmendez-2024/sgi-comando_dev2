package com.cajamarca.sgi.comando.storage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;

/**
 * Foto estándar en MinIO. La clave depende del contenido (sha256), así que dos versiones de un
 * protocolo con la misma foto comparten el mismo objeto. Mientras existan filas sin migrar, la
 * lectura cae al bytea antiguo.
 */
@ApplicationScoped
public class StandardImageStore {
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public record Stored(String objectKey, String sha256, long size, String contentType) {}

    @Inject StorageService storage;

    public Stored save(String module, byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new BadRequestException("Imagen obligatoria");
        if (bytes.length > MAX_BYTES) throw new BadRequestException("La foto estándar no puede superar 5 MB");
        String ct = ImageSniffer.detect(bytes);
        if (ct == null) throw new BadRequestException("Formato de imagen no permitido");
        String sha = Digests.sha256Hex(bytes);
        String key = "standard/" + module + "/" + sha + "." + ImageSniffer.extension(ct);
        if (!storage.exists(storage.standardBucket(), key)) storage.put(storage.standardBucket(), key, bytes, ct);
        return new Stored(key, sha, bytes.length, ct);
    }

    public byte[] read(String objectKey, byte[] legacy) {
        if (objectKey != null) return storage.read(storage.standardBucket(), objectKey);
        if (legacy != null && legacy.length > 0) return legacy;
        throw new NotFoundException("No hay foto estándar");
    }

    public static boolean has(String objectKey, byte[] legacy) { return objectKey != null || (legacy != null && legacy.length > 0); }
}
