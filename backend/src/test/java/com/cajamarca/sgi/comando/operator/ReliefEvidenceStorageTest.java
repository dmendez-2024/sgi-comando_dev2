package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.storage.Digests;
import com.cajamarca.sgi.comando.storage.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.*;

/** Fotos del relevo en MinIO: la fila guarda bucket + object_key; las filas antiguas con content se siguen sirviendo y se migran. */
@QuarkusTest
class ReliefEvidenceStorageTest {
    @Inject EntityManager em;
    @Inject StorageService storage;
    @Inject ReliefEvidenceMigrator migrator;
    @Inject ReliefStationReviews stationReviews;
    static final Path JPG = Path.of("src/test/resources/fixtures/sample.jpg");
    static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID assignment;
    byte[] photo;

    @BeforeEach void setUp() throws Exception {
        assignment = ensureAgentAssignment(em);
        photo = Files.readAllBytes(JPG);
    }

    String upload(UUID eventId, String purpose, byte[] bytes, int status) {
        var response = as("agente").contentType("image/jpeg").body(bytes)
            .put("/api/v1/operator/relief-evidence/" + eventId + "/" + purpose + "?assignmentId=" + assignment).then().statusCode(status);
        return status == 200 ? response.extract().path("evidenceId") : null;
    }

    byte[] download(UUID eventId, String purpose) {
        return as("agente").get("/api/v1/operator/relief-evidence/" + eventId + "/" + purpose + "?assignmentId=" + assignment)
            .then().statusCode(200).extract().asByteArray();
    }

    /** bucket, object_key, size_bytes, content, username de la foto. */
    Object[] row(Object id) {
        return QuarkusTransaction.requiringNew().call(() -> (Object[]) em.createNativeQuery(
            "select bucket,object_key,size_bytes,content,username from operator_relief_evidence where id=cast(:id as uuid)")
            .setParameter("id", id.toString()).getSingleResult());
    }

    /** Misma foto JPEG con otro contenido (conserva las marcas de inicio y fin que valida el PUT). */
    byte[] altered(byte[] bytes) {
        byte[] copy = bytes.clone();
        copy[copy.length / 2] ^= 0x5A;
        return copy;
    }

    /** id con el primer bit en 0 (00…–7f…): Postgres lo ordena antes que los ids 80…–ff…, el caso que el migrador no debe saltarse. */
    UUID insertLegacy(UUID eventId, String purpose, String username, byte[] bytes, String sha256) {
        UUID random = UUID.randomUUID();
        UUID id = new UUID(random.getMostSignificantBits() & Long.MAX_VALUE, random.getLeastSignificantBits());
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
            insert into operator_relief_evidence(id,instance_country_id,event_id,assignment_id,username,purpose,sha256,content_type,content,created_at)
            values(:id,:t,:e,:a,:u,:p,:h,'image/jpeg',:b,current_timestamp)""")
            .setParameter("id", id).setParameter("t", TENANT).setParameter("e", eventId).setParameter("a", assignment).setParameter("u", username)
            .setParameter("p", purpose).setParameter("h", sha256).setParameter("b", bytes).executeUpdate());
        return id;
    }

    @Test void uploadStoresThePhotoInMinioAndServesIt() {
        UUID eventId = UUID.randomUUID();
        String evidenceId = upload(eventId, "entrant_face", photo, 200);

        Object[] stored = row(evidenceId);
        Assertions.assertEquals(storage.evidenceBucket(), stored[0]);
        Assertions.assertTrue(stored[1].toString().startsWith("evidence/relief/") && stored[1].toString().contains(eventId + "/entrant_face-"));
        Assertions.assertEquals((long) photo.length, ((Number) stored[2]).longValue());
        Assertions.assertNull(stored[3], "Los bytes ya no se guardan en la base");
        Assertions.assertArrayEquals(photo, storage.read(stored[0].toString(), stored[1].toString()));
        Assertions.assertArrayEquals(photo, download(eventId, "entrant_face"));

        // Reintento offline con la misma foto: mismo evidenceId. Otra foto para el mismo propósito: 409.
        Assertions.assertEquals(evidenceId, upload(eventId, "entrant_face", photo, 200));
        upload(eventId, "entrant_face", altered(photo), 409);
    }

    @Test void legacyPhotoIsServedAndThenMigrated() {
        UUID eventId = UUID.randomUUID();
        String username = row(upload(eventId, "entrant_face", photo, 200))[4].toString();
        UUID legacy = insertLegacy(eventId, "entrant_full", username, photo, Digests.sha256Hex(photo));
        UUID tampered = insertLegacy(eventId, "station_0", username, photo, Digests.sha256Hex(altered(photo)));
        try {
            Assertions.assertArrayEquals(photo, download(eventId, "entrant_full"), "La foto antigua se sirve desde content");

            migrator.migrateAll();

            Object[] moved = row(legacy);
            Assertions.assertEquals(storage.evidenceBucket(), moved[0]);
            Assertions.assertTrue(moved[1].toString().contains(eventId + "/entrant_full-" + legacy));
            Assertions.assertNull(moved[3], "content se libera al migrar");
            Assertions.assertArrayEquals(photo, download(eventId, "entrant_full"), "La misma foto, ahora desde MinIO");

            Object[] skipped = row(tampered);
            Assertions.assertNull(skipped[1], "Con sha256 que no coincide no se migra");
            Assertions.assertNotNull(skipped[3], "y conserva su content");
        } finally {
            QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("delete from operator_relief_evidence where id=:id").setParameter("id", tampered).executeUpdate());
        }
    }

    @Test void stationPhotoForVisintReusesTheStoredObject() {
        UUID eventId = UUID.randomUUID();
        String evidenceId = upload(eventId, "station_0", photo, 200);
        Object[] stored = row(evidenceId);
        Object[] ctx = QuarkusTransaction.requiringNew().call(() -> (Object[]) em.createNativeQuery("""
            select s.id, p.point_id, a.employee_id, s.post_id from operational_assignment a
            join shift_occurrence s on s.id=a.shift_occurrence_id join post p on p.id=s.post_id where a.id=:a""")
            .setParameter("a", assignment).getSingleResult());
        ObjectNode batch = new ObjectMapper().createObjectNode().put("batchId", UUID.randomUUID().toString())
            .put("correlationId", UUID.randomUUID().toString()).put("deviceId", "relief-storage-test");
        batch.putArray("events").addObject().put("latitude", -2.9).put("longitude", -79.0);

        // Todo se revierte al final: la ubicación temporal del Puesto y lo que registra el relevo.
        QuarkusTransaction.begin();
        try {
            int located = em.createNativeQuery("update post_operational_config set latitude=-2.9, longitude=-79.0 where post_id=:p and instance_country_id=:t")
                .setParameter("p", ctx[3]).setParameter("t", TENANT).executeUpdate();
            Assumptions.assumeTrue(located > 0, "El Puesto de pruebas no tiene configuración operacional");
            stationReviews.enqueue(TENANT, eventId, assignment, (UUID) ctx[0], (UUID) ctx[1], (UUID) ctx[3], (UUID) ctx[2], stored[4].toString(), Instant.now(), batch);
            Object[] evidence = (Object[]) em.createNativeQuery("select bucket,object_key,size_bytes from evidence_object where instance_country_id=:t and client_evidence_id=cast(:c as uuid)")
                .setParameter("t", TENANT).setParameter("c", evidenceId).getSingleResult();
            Assertions.assertEquals(stored[0], evidence[0]);
            Assertions.assertEquals(stored[1], evidence[1], "VISINT usa el mismo objeto de MinIO, sin copiarlo");
            Assertions.assertEquals(((Number) stored[2]).longValue(), ((Number) evidence[2]).longValue());
        } finally {
            QuarkusTransaction.rollback();
        }
    }
}
