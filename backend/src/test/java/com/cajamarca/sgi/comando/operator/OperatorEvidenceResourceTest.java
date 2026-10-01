package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.storage.Digests;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.*;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class OperatorEvidenceResourceTest {
    @Inject EntityManager em;
    final ObjectMapper m = new ObjectMapper();
    static final Path JPG = Path.of("src/test/resources/fixtures/sample.jpg"), JPG2 = Path.of("src/test/resources/fixtures/sample2.jpg"),
        PNG = Path.of("src/test/resources/fixtures/sample.png"), TXT = Path.of("src/test/resources/fixtures/not-an-image.txt");
    UUID assignment; String checkpoint;

    @BeforeEach void setUp() {
        assignment = ensureAgentAssignment(em);
        checkpoint = (String) publishPatrolWithCheckpoint(-2.17, -79.92).get("checkpointId");
    }

    ObjectNode meta(UUID eventId, List<UUID> ids, List<Path> files, double lat) throws Exception {
        ObjectNode o = m.createObjectNode().put("uploadBatchId", UUID.randomUUID().toString()).put("eventId", eventId.toString())
            .put("assignmentId", assignment.toString()).put("targetType", "PATROL_CHECKPOINT").put("targetId", checkpoint);
        ArrayNode items = o.putArray("items");
        for (int i = 0; i < ids.size(); i++) items.addObject().put("clientEvidenceId", ids.get(i).toString()).put("capturedAt", Instant.now().toString())
            .put("latitude", lat).put("longitude", -79.92).put("accuracyM", 8.5).put("source", "CAMERA")
            .put("sha256", Digests.sha256Hex(Files.readAllBytes(files.get(i))));
        return o;
    }

    RequestSpecification form(ObjectNode meta, List<UUID> ids, List<Path> files) throws Exception {
        RequestSpecification r = as("agente").multiPart("metadata", meta.toString());
        for (int i = 0; i < ids.size(); i++) r = r.multiPart("files", ids.get(i) + ".jpg", Files.readAllBytes(files.get(i)), "image/jpeg");
        return r;
    }

    @Test void storesOnePhotoAndIsIdempotent() throws Exception {
        UUID event = UUID.randomUUID();
        List<UUID> ids = List.of(UUID.randomUUID());
        List<Path> files = List.of(JPG);
        ObjectNode meta = meta(event, ids, files, -2.17);
        form(meta, ids, files).post("/api/v1/operator/evidences").then().statusCode(200)
            .body("results.size()", is(1)).body("results[0].status", is("STORED"));
        form(meta, ids, files).post("/api/v1/operator/evidences").then().statusCode(200)
            .body("results[0].status", is("ALREADY_STORED"));
        Number count = (Number) em.createNativeQuery("select count(*) from evidence_object where event_id=:e").setParameter("e", event).getSingleResult();
        Assertions.assertEquals(1, count.intValue());
    }

    @Test void onlyOnePhotoPerCheckpointExecution() throws Exception {
        UUID event = UUID.randomUUID();
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        List<Path> files = List.of(JPG, JPG2);
        form(meta(event, ids, files, -2.17), ids, files).post("/api/v1/operator/evidences").then().statusCode(200)
            .body("results[0].status", is("STORED"))
            .body("results[1].status", is("REJECTED")).body("results[1].reason", is("TOO_MANY_PHOTOS"));
    }

    @Test void acceptsMetadataLargerThanTwoKilobytes() throws Exception {
        UUID event = UUID.randomUUID();
        List<UUID> ids = new ArrayList<>(); List<Path> files = new ArrayList<>();
        for (int i = 0; i < 5; i++) { ids.add(UUID.randomUUID()); files.add(JPG); }
        ObjectNode meta = meta(event, ids, files, -2.17);
        // Un móvil real agrega datos del dispositivo por foto; campos extra se ignoran pero cuentan para el tamaño.
        meta.path("items").forEach(i -> ((ObjectNode) i).put("deviceModel", "Samsung Galaxy A54 5G · Android 14 · cámara trasera 50 MP f/1.8 OIS · app SGI Operador 0.14.0"));
        Assertions.assertTrue(meta.toString().length() > 2048, "los metadatos de la prueba deben superar 2 KB");
        form(meta, ids, files).post("/api/v1/operator/evidences").then().statusCode(200).body("results.size()", is(5));
    }

    @Test void rejectsDisguisedFileAndFlagsOutOfRange() throws Exception {
        UUID event = UUID.randomUUID();
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        List<Path> files = List.of(TXT, JPG);
        // La foto rechazada no cuenta: la segunda (válida) se guarda.
        form(meta(event, ids, files, -2.30), ids, files).post("/api/v1/operator/evidences").then().statusCode(200)
            .body("results.find{it.clientEvidenceId=='" + ids.get(0) + "'}.status", is("REJECTED"))
            .body("results.find{it.clientEvidenceId=='" + ids.get(0) + "'}.reason", is("UNSUPPORTED_FORMAT"))
            .body("results.find{it.clientEvidenceId=='" + ids.get(1) + "'}.status", is("STORED"))
            .body("results.find{it.clientEvidenceId=='" + ids.get(1) + "'}.flags", hasItem("OUT_OF_RANGE"));
    }

    @Test void conflictWhenSameIdDifferentContent() throws Exception {
        UUID event = UUID.randomUUID(); List<UUID> ids = List.of(UUID.randomUUID());
        form(meta(event, ids, List.of(JPG), -2.17), ids, List.of(JPG)).post("/api/v1/operator/evidences").then().statusCode(200);
        form(meta(event, ids, List.of(PNG), -2.17), ids, List.of(PNG)).post("/api/v1/operator/evidences").then().statusCode(409);
    }

    @Test void rejectsCheckpointOfDraftProtocol() throws Exception {
        checkpoint = (String) createDraftPatrolWithCheckpoint(-2.17, -79.92).get("checkpointId");
        List<UUID> ids = List.of(UUID.randomUUID());
        form(meta(UUID.randomUUID(), ids, List.of(JPG), -2.17), ids, List.of(JPG)).post("/api/v1/operator/evidences").then().statusCode(400);
    }

    @Test void agentCanSeeStandardImages() {
        String image = as("agente").queryParam("assignmentId", assignment).get("/api/v1/operator/runtime").then().statusCode(200)
            .extract().path("patrols.checkpoints.flatten().find{it.checkpointId=='" + checkpoint + "'}.standardImages[0].id");
        as("agente").queryParam("assignmentId", assignment).get("/api/v1/operator/checkpoints/" + checkpoint + "/standard-images/" + image)
            .then().statusCode(200).contentType("image/jpeg");
    }
}
