package com.cajamarca.sgi.comando.obsint;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.storage.Digests;
import com.cajamarca.sgi.comando.storage.ImageSniffer;
import com.cajamarca.sgi.comando.storage.StorageService;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Único punto de contacto de OBSINT con el resto de SGI: Comando (tenant, usuario, MinIO, validación de imágenes, banderas UAT).
 * El módulo no usa esas clases en ningún otro lugar; si Comando las cambia, solo se ajusta este archivo.
 */
@ApplicationScoped
public class ObsintPlatform {
    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject StorageService storage;
    @ConfigProperty(name = "sgi.uat.features-enabled") boolean uatEnabled;

    public UUID instanceCountryId() { return tenant.instanceCountryId(); }
    public String username() { return scope.username(); }
    /** El catálogo demo solo existe con las herramientas UAT habilitadas. */
    public boolean demoEnabled() { return uatEnabled; }

    /** image/jpeg, image/png o image/webp según los bytes; null si no es una imagen aceptada. */
    public String imageContentType(byte[] bytes) { return ImageSniffer.detect(bytes); }
    public String imageExtension(String contentType) { return ImageSniffer.extension(contentType); }
    public String sha256(byte[] bytes) { return Digests.sha256Hex(bytes); }

    public String evidenceBucket() { return storage.evidenceBucket(); }
    public void put(String bucket, String key, byte[] bytes, String contentType) { storage.put(bucket, key, bytes, contentType); }
    public byte[] read(String bucket, String key) { return storage.read(bucket, key); }
    public void delete(String bucket, String key) { storage.delete(bucket, key); }
}
