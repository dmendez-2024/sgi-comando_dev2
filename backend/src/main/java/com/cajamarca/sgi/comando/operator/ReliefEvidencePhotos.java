package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.storage.StorageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;
import java.time.*;
import java.util.UUID;

/**
 * Fotos del relevo (rostro, cuerpo y puesto) en MinIO. operator_relief_evidence guarda bucket + object_key;
 * las filas antiguas aún traen los bytes en content hasta que ReliefEvidenceMigrator las sube.
 */
@ApplicationScoped
public class ReliefEvidencePhotos {
    public static final String CONTENT_TYPE = "image/jpeg";
    private static final Logger LOG = Logger.getLogger(ReliefEvidencePhotos.class);

    public record Stored(String bucket, String objectKey, long size) {}

    @Inject StorageService storage;
    @Inject TransactionSynchronizationRegistry tx;

    /** Clave estable por foto: un reintento del migrador sobrescribe el mismo objeto en vez de duplicarlo. */
    public static String key(UUID eventId, String purpose, UUID photoId, Instant createdAt) {
        ZonedDateTime at = createdAt.atZone(ZoneOffset.UTC);
        return String.format("evidence/relief/%04d/%02d/%s/%s-%s.jpg", at.getYear(), at.getMonthValue(), eventId, purpose, photoId);
    }

    /** Sube la foto dentro de la transacción en curso; si la transacción no confirma, borra el objeto para no dejarlo huérfano. */
    public Stored store(UUID eventId, String purpose, UUID photoId, byte[] bytes) {
        String bucket = storage.evidenceBucket(), key = key(eventId, purpose, photoId, Instant.now());
        storage.put(bucket, key, bytes, CONTENT_TYPE);
        tx.registerInterposedSynchronization(new Synchronization() {
            @Override public void beforeCompletion() {}
            @Override public void afterCompletion(int status) {
                if (status == Status.STATUS_COMMITTED) return;
                try { storage.delete(bucket, key); }
                catch (RuntimeException e) { LOG.warnf("relief_evidence_orphan bucket=%s key=%s error=%s", bucket, key, e.getMessage()); }
            }
        });
        return new Stored(bucket, key, bytes.length);
    }

    /** Bytes de la foto: de MinIO si la fila tiene object_key; si no, el content antiguo. */
    public byte[] read(Object bucket, Object objectKey, Object legacyContent) {
        if (bucket != null && objectKey != null) return storage.read(bucket.toString(), objectKey.toString());
        if (legacyContent instanceof byte[] b && b.length > 0) return b;
        throw new NotFoundException("Fotografía no encontrada");
    }
}
