package com.cajamarca.sgi.comando.storage;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import java.util.*;

/** Mueve a MinIO las fotos estándar que aún viven en bytea. Conserva la versión de cada foto. */
@ApplicationScoped
public class StandardImageMigrator {
    private static final Logger LOG = Logger.getLogger(StandardImageMigrator.class);
    private static final Map<String,String> TABLES = new LinkedHashMap<>();
    static {
        TABLES.put("patrol_checkpoint", "patrol");
        TABLES.put("consignment_evidence", "consignment");
        TABLES.put("logbook_protocol_field", "bitacora");
    }

    @Inject EntityManager em;
    @Inject StandardImageStore store;
    @ConfigProperty(name="sgi.storage.migrate-standard-images") boolean enabled;

    @Scheduled(delayed="20s", every="1h", concurrentExecution=Scheduled.ConcurrentExecution.SKIP)
    void scheduled() {
        if (!enabled) return;
        try {
            int n = migrateAll();
            if (n > 0) LOG.infof("Fotos estándar migradas a MinIO: %d", n);
        } catch (RuntimeException e) { LOG.warn("Migración de fotos estándar pendiente: " + e.getMessage()); }
    }

    public int migrateAll() {
        int total = 0;
        for (var t : TABLES.entrySet()) total += migrate(t.getKey(), t.getValue());
        promotePatrolStandardImages();
        return total;
    }

    /** Los Hitos de patrulla guardan sus fotos estándar (hasta 5) en patrol_checkpoint_standard_image: la foto antigua pasa a ser la n.º 1. */
    void promotePatrolStandardImages() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("""
                insert into patrol_checkpoint_standard_image(id, instance_country_id, checkpoint_id, position, original_name, content_type, object_key, sha256, size_bytes)
                select gen_random_uuid(), c.instance_country_id, c.id, 1, c.standard_image_original_name, coalesce(c.standard_image_content_type, 'image/jpeg'),
                       c.standard_image_object_key, c.standard_image_sha256, coalesce(c.standard_image_size, 0)
                from patrol_checkpoint c where c.standard_image_object_key is not null and c.standard_image_sha256 is not null
                  and not exists (select 1 from patrol_checkpoint_standard_image s where s.checkpoint_id = c.id)""").executeUpdate();
            em.createNativeQuery("""
                update patrol_checkpoint set standard_image_object_key=null, standard_image_sha256=null, standard_image_size=null,
                       standard_image_original_name=null, standard_image_content_type=null
                where standard_image_object_key is not null""").executeUpdate();
        });
    }

    @SuppressWarnings("unchecked")
    int migrate(String table, String module) {
        int moved = 0;
        for (int guard = 0; guard < 1000; guard++) {
            List<Object[]> rows = QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
                "select id, standard_image_data from " + table + " where standard_image_object_key is null and standard_image_data is not null")
                .setMaxResults(20).getResultList());
            if (rows.isEmpty()) return moved;
            for (Object[] r : rows) {
                UUID id = (UUID) r[0];
                StandardImageStore.Stored s = store.save(module, (byte[]) r[1]);
                QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("update " + table
                    + " set standard_image_object_key=:k, standard_image_sha256=:s, standard_image_size=:z, standard_image_content_type=:ct, standard_image_data=null"
                    + " where id=:id and standard_image_object_key is null")
                    .setParameter("k", s.objectKey()).setParameter("s", s.sha256()).setParameter("z", s.size())
                    .setParameter("ct", s.contentType()).setParameter("id", id).executeUpdate());
                moved++;
            }
        }
        return moved;
    }
}
