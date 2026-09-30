package com.cajamarca.sgi.comando.storage;

import com.cajamarca.sgi.comando.operator.OperatorFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class StandardImageStorageTest {
    @Inject EntityManager em;
    @Inject StorageService storage;
    @Inject StandardImageMigrator migrator;
    static final Path JPG = Path.of("src/test/resources/fixtures/sample.jpg");

    @SuppressWarnings("unchecked")
    List<Object[]> images(String checkpointId) {
        return em.createNativeQuery("select id, object_key, sha256, position from standard_reference_image where target_type='PATROL_CHECKPOINT' and target_id=:c order by position")
            .setParameter("c", UUID.fromString(checkpointId)).getResultList();
    }

    @Test void multipartUploadGoesToMinioAndReadsBack() throws Exception {
        String cp = (String) OperatorFixtures.createDraftPatrolWithCheckpoint(-2.17, -79.92).get("checkpointId");
        OperatorFixtures.uploadStandard(cp, JPG).statusCode(200).body("hasStandardImage", is(true)).body("standardImageVersion", is(1));
        Object[] r = images(cp).get(0);
        assertEquals(Digests.sha256Hex(Files.readAllBytes(JPG)), r[2].toString().trim());
        assertTrue(storage.exists(storage.standardBucket(), r[1].toString()));
        byte[] back = as("coord").get("/api/patrols/checkpoints/" + cp + "/standard-images/" + r[0]).then().statusCode(200).extract().asByteArray();
        assertArrayEquals(Files.readAllBytes(JPG), back);
    }

    @Test void rejectsDisguisedText() {
        String cp = (String) OperatorFixtures.createDraftPatrolWithCheckpoint(-2.17, -79.92).get("checkpointId");
        OperatorFixtures.uploadStandard(cp, Path.of("src/test/resources/fixtures/not-an-image.txt")).statusCode(400);
    }

    /** Fase 4: la foto estándar antigua de una evidencia de Consigna (bytea) pasa a ser su foto estándar n.º 1. */
    @Test void legacyConsignmentImageMigratesToFirstStandardImage() throws Exception {
        String evidence = (String) com.cajamarca.sgi.comando.operator.TaskFixtures.draftConsignmentWithPhoto().get("evidenceId");
        byte[] bytes = Files.readAllBytes(Path.of("src/test/resources/fixtures/sample.png"));
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("update consignment_evidence set standard_image_data=:d, standard_image_content_type='image/png', standard_image_object_key=null where id=:id")
            .setParameter("d", bytes).setParameter("id", UUID.fromString(evidence)).executeUpdate());
        assertTrue(migrator.migrateAll() >= 1);
        List<?> rows = em.createNativeQuery("select id from standard_reference_image where target_type='CONSIGNMENT_EVIDENCE' and target_id=:e")
            .setParameter("e", UUID.fromString(evidence)).getResultList();
        assertEquals(1, rows.size());
        assertArrayEquals(bytes, as("coord").get("/api/consignments/evidences/" + evidence + "/standard-images/" + rows.get(0)).then().statusCode(200).extract().asByteArray());
    }

    /** Una foto estándar antigua (bytea en patrol_checkpoint) se sube a MinIO y pasa a ser la foto estándar n.º 1 del Hito. */
    @Test void legacyByteaMigratesToFirstStandardImage() throws Exception {
        String cp = (String) OperatorFixtures.createDraftPatrolWithCheckpoint(-2.17, -79.92).get("checkpointId");
        byte[] bytes = Files.readAllBytes(Path.of("src/test/resources/fixtures/sample.png"));
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("update patrol_checkpoint set standard_image_data=:d, standard_image_content_type='image/png', standard_image_version=3, standard_image_object_key=null where id=:id")
            .setParameter("d", bytes).setParameter("id", UUID.fromString(cp)).executeUpdate());
        assertTrue(migrator.migrateAll() >= 1);
        List<Object[]> rows = images(cp);
        assertEquals(1, rows.size());
        assertEquals(1, ((Number) rows.get(0)[3]).intValue());
        assertArrayEquals(bytes, as("coord").get("/api/patrols/checkpoints/" + cp + "/standard-images/" + rows.get(0)[0]).then().statusCode(200).extract().asByteArray());
        Object[] legacy = (Object[]) em.createNativeQuery("select standard_image_object_key, standard_image_data is null, standard_image_version from patrol_checkpoint where id=:id")
            .setParameter("id", UUID.fromString(cp)).getSingleResult();
        assertNull(legacy[0]); assertEquals(true, legacy[1]); assertEquals(3, ((Number) legacy[2]).intValue());
    }
}
