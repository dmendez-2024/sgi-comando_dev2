package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.storage.Digests;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
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
class PatrolExecutionTest {
    @Inject EntityManager em;
    final ObjectMapper m = new ObjectMapper();
    UUID assignment, employee;
    Map<String,Object> ids;

    @BeforeEach void setUp() {
        assignment = ensureAgentAssignment(em);
        employee = UUID.fromString(as("agente").queryParam("assignmentId", assignment).get("/api/v1/operator/runtime").then().statusCode(200).extract().path("employeeId"));
        ids = publishPatrolWithCheckpoint(-2.17, -79.92);
    }

    List<String> upload(UUID event, int n, Object checkpoint) throws Exception {
        byte[] jpg = Files.readAllBytes(Path.of("src/test/resources/fixtures/sample.jpg"));
        ObjectNode meta = m.createObjectNode().put("uploadBatchId", UUID.randomUUID().toString()).put("eventId", event.toString())
            .put("assignmentId", assignment.toString()).put("targetType", "PATROL_CHECKPOINT").put("targetId", checkpoint.toString());
        ArrayNode items = meta.putArray("items");
        List<UUID> cids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            UUID c = UUID.randomUUID(); cids.add(c);
            items.addObject().put("clientEvidenceId", c.toString()).put("capturedAt", Instant.now().toString())
                .put("latitude", -2.17).put("longitude", -79.92).put("source", "CAMERA").put("sha256", Digests.sha256Hex(jpg));
        }
        RequestSpecification r = as("agente").multiPart("metadata", meta.toString());
        for (UUID c : cids) r = r.multiPart("files", c + ".jpg", jpg, "image/jpeg");
        return r.post("/api/v1/operator/evidences").then().statusCode(200).extract().path("results.evidenceId");
    }

    ObjectNode batch(UUID event, UUID run, List<String> evidenceIds) {
        ObjectNode b = m.createObjectNode().put("batchId", UUID.randomUUID().toString()).put("correlationId", UUID.randomUUID().toString())
            .put("employeeId", employee.toString()).put("instanceCountryId", "11111111-1111-1111-1111-111111111111").put("deviceId", "test")
            .put("capturedAt", Instant.now().toString());
        ObjectNode e = b.putArray("events").addObject().put("type", "PATROL_CHECKPOINT_COMPLETED").put("eventId", event.toString())
            .put("assignmentId", assignment.toString()).put("patrolRunId", run.toString()).put("patrolId", ids.get("patrolId").toString())
            .put("checkpointId", ids.get("checkpointId").toString()).put("executedAt", Instant.now().toString()).put("latitude", -2.17).put("longitude", -79.92);
        ArrayNode arr = e.putArray("evidenceIds");
        evidenceIds.forEach(arr::add);
        return b;
    }

    @Test void registersExecutionWithOnePhotoAndIsIdempotent() throws Exception {
        UUID event = UUID.randomUUID(), run = UUID.randomUUID();
        ObjectNode b = batch(event, run, upload(event, 1, ids.get("checkpointId")));
        as("agente").contentType(ContentType.JSON).body(b.toString()).post("/api/v1/operator/executions").then().statusCode(200)
            .body("results[0].status", is("RECEIVED")).body("results[0].evidenceCount", is(1));
        as("agente").contentType(ContentType.JSON).body(b.toString()).post("/api/v1/operator/executions").then().statusCode(200)
            .body("results[0].evidenceCount", is(1));
        Assertions.assertEquals(1, ((Number) em.createNativeQuery("select count(*) from task_execution_evidence where task_execution_id=:e")
            .setParameter("e", event).getSingleResult()).intValue());
        Assertions.assertEquals("ATTACHED", em.createNativeQuery("select distinct status from evidence_object where event_id=:e").setParameter("e", event).getSingleResult());
    }

    @Test void refusesExecutionWithoutPhoto() throws Exception {
        UUID event = UUID.randomUUID();
        as("agente").contentType(ContentType.JSON).body(batch(event, UUID.randomUUID(), List.of()).toString())
            .post("/api/v1/operator/executions").then().statusCode(400).body(containsString("requiere 1 foto"));
    }

    @Test void refusesPhotosFromAnotherEvent() throws Exception {
        List<String> foreign = upload(UUID.randomUUID(), 1, ids.get("checkpointId"));
        as("agente").contentType(ContentType.JSON).body(batch(UUID.randomUUID(), UUID.randomUUID(), foreign).toString())
            .post("/api/v1/operator/executions").then().statusCode(400).body(containsString("Foto no autorizada"));
    }

    @Test void refusesSameCheckpointTwiceInOneRound() throws Exception {
        UUID run = UUID.randomUUID(), first = UUID.randomUUID(), second = UUID.randomUUID();
        as("agente").contentType(ContentType.JSON).body(batch(first, run, upload(first, 1, ids.get("checkpointId"))).toString())
            .post("/api/v1/operator/executions").then().statusCode(200);
        as("agente").contentType(ContentType.JSON).body(batch(second, run, upload(second, 1, ids.get("checkpointId"))).toString())
            .post("/api/v1/operator/executions").then().statusCode(409);
    }

    @Test void reliefStillRoutesToReliefContract() {
        ObjectNode b = batch(UUID.randomUUID(), UUID.randomUUID(), List.of());
        ((ObjectNode) b.path("events").get(0)).put("type", "RELIEF_SUBMITTED");
        as("agente").contentType(ContentType.JSON).body(b.toString()).post("/api/v1/operator/executions").then().statusCode(400)
            .body(containsString("Campo requerido"));
    }
}
