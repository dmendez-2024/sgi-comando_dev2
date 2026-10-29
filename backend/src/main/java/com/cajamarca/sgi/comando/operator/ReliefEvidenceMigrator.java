package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.storage.Digests;
import com.cajamarca.sgi.comando.storage.StorageService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import java.time.*;
import java.util.*;

/** Mueve a MinIO las fotos del relevo que aún viven en operator_relief_evidence.content. Cada fila en su propia transacción. */
@ApplicationScoped
public class ReliefEvidenceMigrator {
    private static final Logger LOG = Logger.getLogger(ReliefEvidenceMigrator.class);
    /** 00000000-0000-0000-0000-000000000000: Postgres ordena uuid por bytes sin signo, así que es el menor posible. */
    private static final UUID FIRST = new UUID(0L, 0L);

    @Inject EntityManager em;
    @Inject StorageService storage;
    @ConfigProperty(name="sgi.storage.migrate-relief-evidence") boolean enabled;

    @Scheduled(delayed="30s", every="1h", concurrentExecution=Scheduled.ConcurrentExecution.SKIP)
    void scheduled() {
        if (!enabled) return;
        try {
            int n = migrateAll();
            if (n > 0) LOG.infof("Fotos del relevo migradas a MinIO: %d", n);
        } catch (RuntimeException e) { LOG.warn("Migración de fotos del relevo pendiente: " + e.getMessage()); }
    }

    /** Recorre las filas por id para no volver sobre las que se saltan (sha256 alterado). Devuelve cuántas movió. */
    @SuppressWarnings("unchecked")
    public int migrateAll() {
        int moved = 0;
        UUID last = FIRST;
        while (true) {
            UUID after = last;
            List<Object[]> rows = QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery("""
                select id,event_id,purpose,sha256,created_at,content from operator_relief_evidence
                where content is not null and object_key is null and id > :last order by id""")
                .setParameter("last", after).setMaxResults(20).getResultList());
            if (rows.isEmpty()) return moved;
            for (Object[] r : rows) {
                last = (UUID) r[0];
                if (migrate(last, (UUID) r[1], (String) r[2], ((String) r[3]).trim(), instant(r[4]), (byte[]) r[5])) moved++;
            }
        }
    }

    boolean migrate(UUID id, UUID eventId, String purpose, String sha256, Instant createdAt, byte[] bytes) {
        if (!sha256.equals(Digests.sha256Hex(bytes))) {
            LOG.warnf("relief_evidence_migration_skipped id=%s reason=SHA256_MISMATCH", id);
            return false;
        }
        String bucket = storage.evidenceBucket(), key = ReliefEvidencePhotos.key(eventId, purpose, id, createdAt);
        storage.put(bucket, key, bytes, ReliefEvidencePhotos.CONTENT_TYPE);
        // Solo se libera content si lo que quedó en MinIO es idéntico.
        if (!sha256.equals(Digests.sha256Hex(storage.read(bucket, key)))) {
            LOG.warnf("relief_evidence_migration_skipped id=%s reason=STORED_COPY_MISMATCH", id);
            return false;
        }
        int updated = QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery("""
            update operator_relief_evidence set bucket=:bucket, object_key=:key, size_bytes=:size, content=null
            where id=:id and object_key is null""")
            .setParameter("bucket", bucket).setParameter("key", key).setParameter("size", (long) bytes.length).setParameter("id", id).executeUpdate());
        return updated == 1;
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant i) return i;
        if (value instanceof OffsetDateTime o) return o.toInstant();
        if (value instanceof java.sql.Timestamp t) return t.toInstant();
        return Instant.parse(value.toString());
    }
}
