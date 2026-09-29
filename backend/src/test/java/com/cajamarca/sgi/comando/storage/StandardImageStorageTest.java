package com.cajamarca.sgi.comando.storage;

import com.cajamarca.sgi.comando.operator.OperatorFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.UUID;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class StandardImageStorageTest {
    @Inject EntityManager em;
    @Inject StorageService storage;
    @Inject StandardImageMigrator migrator;
    static final Path JPG = Path.of("src/test/resources/fixtures/sample.jpg");

    Object[] row(String table, String id) {
        return (Object[]) em.createNativeQuery("select standard_image_object_key, standard_image_data is null, standard_image_sha256, standard_image_version from " + table + " where id=:id")
            .setParameter("id", UUID.fromString(id)).getSingleResult();
    }

    @Test void multipartUploadGoesToMinioAndReadsBack() throws Exception {
        String cp = (String) OperatorFixtures.createDraftPatrolWithCheckpoint(-2.17, -79.92).get("checkpointId");
        as("coord").multiPart("file", JPG.toFile(), "image/jpeg").post("/api/patrols/checkpoints/" + cp + "/standard-image")
            .then().statusCode(200).body("hasStandardImage", is(true)).body("standardImageVersion", is(1));
        Object[] r = row("patrol_checkpoint", cp);
        assertNotNull(r[0]); assertEquals(true, r[1]); assertEquals(Digests.sha256Hex(Files.readAllBytes(JPG)), r[2].toString());
        assertTrue(storage.exists(storage.standardBucket(), r[0].toString()));
        byte[] back = as("coord").get("/api/patrols/checkpoints/" + cp + "/standard-image").then().statusCode(200).extract().asByteArray();
        assertArrayEquals(Files.readAllBytes(JPG), back);
    }

    @Test void octetStreamUploadStillWorksAndGoesToMinio() throws Exception {
        String cp = (String) OperatorFixtures.createDraftPatrolWithCheckpoint(-2.17, -79.92).get("checkpointId");
        as("coord").contentType("application/octet-stream").body(Files.readAllBytes(JPG))
            .post("/api/patrols/checkpoints/" + cp + "/standard-image?filename=x.jpg&contentType=image/jpeg").then().statusCode(200);
        assertNotNull(row("patrol_checkpoint", cp)[0]);
    }

    @Test void rejectsDisguisedText() {
        String cp = (String) OperatorFixtures.createDraftPatrolWithCheckpoint(-2.17, -79.92).get("checkpointId");
        as("coord").multiPart("file", Path.of("src/test/resources/fixtures/not-an-image.txt").toFile(), "image/jpeg")
            .post("/api/patrols/checkpoints/" + cp + "/standard-image").then().statusCode(400);
    }

    @Test void legacyByteaStillReadableAndMigrates() throws Exception {
        String cp = (String) OperatorFixtures.createDraftPatrolWithCheckpoint(-2.17, -79.92).get("checkpointId");
        byte[] bytes = Files.readAllBytes(Path.of("src/test/resources/fixtures/sample.png"));
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("update patrol_checkpoint set standard_image_data=:d, standard_image_content_type='image/png', standard_image_version=3, standard_image_object_key=null where id=:id")
            .setParameter("d", bytes).setParameter("id", UUID.fromString(cp)).executeUpdate());
        assertArrayEquals(bytes, as("coord").get("/api/patrols/checkpoints/" + cp + "/standard-image").then().statusCode(200).extract().asByteArray());
        assertTrue(migrator.migrateAll() >= 1);
        Object[] r = row("patrol_checkpoint", cp);
        assertNotNull(r[0]); assertEquals(true, r[1]); assertEquals(3, ((Number) r[3]).intValue());
        assertArrayEquals(bytes, as("coord").get("/api/patrols/checkpoints/" + cp + "/standard-image").then().statusCode(200).extract().asByteArray());
    }
}
