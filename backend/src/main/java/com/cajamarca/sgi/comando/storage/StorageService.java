package com.cajamarca.sgi.comando.storage;

import io.minio.*;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.io.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Almacenamiento de archivos en MinIO. Los buckets se crean de forma perezosa para que el
 * backend arranque aunque MinIO no esté disponible todavía.
 */
@ApplicationScoped
public class StorageService {
    @ConfigProperty(name="sgi.minio.endpoint") String endpoint;
    @ConfigProperty(name="sgi.minio.access-key") String accessKey;
    @ConfigProperty(name="sgi.minio.secret-key") String secretKey;
    @ConfigProperty(name="sgi.storage.standard-bucket") String standardBucket;
    @ConfigProperty(name="sgi.storage.evidence-bucket") String evidenceBucket;
    private MinioClient client;
    private final Set<String> ready = ConcurrentHashMap.newKeySet();

    @PostConstruct
    void init() { client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build(); }

    public String standardBucket() { return standardBucket; }
    public String evidenceBucket() { return evidenceBucket; }

    public void put(String bucket, String key, InputStream in, long size, String contentType) {
        ensureBucket(bucket);
        try { client.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(in, size, -1).contentType(contentType).build()); }
        catch (Exception e) { throw new StorageUnavailableException("No se pudo guardar el archivo; el almacenamiento no está disponible", e); }
    }

    public void put(String bucket, String key, byte[] bytes, String contentType) {
        put(bucket, key, new ByteArrayInputStream(bytes), bytes.length, contentType);
    }

    public byte[] read(String bucket, String key) {
        try (InputStream in = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) { return in.readAllBytes(); }
        catch (ErrorResponseException e) {
            if (missing(e)) throw new NotFoundException("Archivo no encontrado");
            throw new StorageUnavailableException("No se pudo leer el archivo; el almacenamiento no está disponible", e);
        } catch (Exception e) { throw new StorageUnavailableException("No se pudo leer el archivo; el almacenamiento no está disponible", e); }
    }

    public boolean exists(String bucket, String key) {
        try { client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build()); return true; }
        catch (ErrorResponseException e) {
            if (missing(e)) return false;
            throw new StorageUnavailableException("No se pudo consultar el almacenamiento", e);
        } catch (Exception e) { throw new StorageUnavailableException("No se pudo consultar el almacenamiento", e); }
    }

    public void delete(String bucket, String key) {
        try { client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build()); }
        catch (Exception e) { throw new StorageUnavailableException("No se pudo eliminar el archivo", e); }
    }

    private static boolean missing(ErrorResponseException e) {
        String code = e.errorResponse().code();
        return "NoSuchKey".equals(code) || "NoSuchBucket".equals(code);
    }

    private void ensureBucket(String bucket) {
        if (ready.contains(bucket)) return;
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            ready.add(bucket);
        } catch (Exception e) { throw new StorageUnavailableException("El almacenamiento de archivos no está disponible", e); }
    }
}
