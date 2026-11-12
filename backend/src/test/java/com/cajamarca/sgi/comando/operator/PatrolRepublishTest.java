package com.cajamarca.sgi.comando.operator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.*;
import static org.hamcrest.Matchers.*;

/**
 * Patrulla programada en curso cuando se vuelve a publicar su protocolo: la versión anterior queda INACTIVA, START de la
 * nueva responde 409 y el agente debe poder ver la patrulla en curso y finalizarla con el patrolId de cualquier versión.
 */
@QuarkusTest
class PatrolRepublishTest {
    @Inject EntityManager em;
    final ObjectMapper m = new ObjectMapper();
    UUID assignment;
    String protocolV1, patrolV1, checkpointV1, patrolV2, checkpointV2;

    @BeforeEach void setUp() {
        assignment = ensureAgentAssignment(em);
        // Ventana de 40 minutos que contiene la hora actual de operación (máximo permitido: 60).
        LocalTime now = LocalTime.now(ZoneId.of("America/Guayaquil"));
        DateTimeFormatter hhmm = DateTimeFormatter.ofPattern("HH:mm");
        String windowStart = now.minusMinutes(10).format(hhmm), windowEnd = now.plusMinutes(30).format(hhmm);
        protocolV1 = as("coord").contentType(ContentType.JSON).body(Map.of("postId", POST_GGTT01, "name", "Republicación " + UUID.randomUUID()))
            .post("/api/patrols/protocols").then().statusCode(200).extract().path("id");
        patrolV1 = as("coord").contentType(ContentType.JSON).body(Map.of("name", "Ronda programada", "structureType", "CLOSED", "scheduleType", "PROGRAMMED"))
            .post("/api/patrols/protocols/" + protocolV1 + "/patrols").then().statusCode(200).extract().path("id");
        as("coord").contentType(ContentType.JSON).body(Map.of("name", "Ronda programada", "description", "", "structureType", "CLOSED",
                "scheduleType", "PROGRAMMED", "sequenceType", "FLEXIBLE", "windowStart", windowStart, "windowEnd", windowEnd, "repetitions", 1))
            .put("/api/patrols/patrols/" + patrolV1).then().statusCode(200);
        Map<String,Object> cp = new HashMap<>(Map.of("name", "Portón prueba", "description", "", "originMode", "FIELD", "latitude", -2.17, "longitude", -79.92,
            "gpsAccuracyM", 5.0, "controlType", "FOTOGRAFIA", "requiresEvidence", true));
        checkpointV1 = as("coord").contentType(ContentType.JSON).body(cp)
            .post("/api/patrols/patrols/" + patrolV1 + "/checkpoints").then().statusCode(200).extract().path("id");
        cp.put("standardImageNotes", ""); cp.put("visintEnabled", false);
        as("coord").contentType(ContentType.JSON).body(cp).put("/api/patrols/checkpoints/" + checkpointV1).then().statusCode(200);
        uploadStandard(checkpointV1, Path.of("src/test/resources/fixtures/sample.jpg")).statusCode(200);
        as("coord").post("/api/patrols/protocols/" + protocolV1 + "/publish").then().statusCode(200).body("status", is("ACTIVO"));
    }

    /** Nueva versión publicada (fork + publish): la anterior queda INACTIVA y sus patrullas cambian de id. */
    void republish() {
        JsonPath fork = as("coord").post("/api/patrols/protocols/" + protocolV1 + "/fork").then().statusCode(200).extract().jsonPath();
        patrolV2 = fork.getString("patrols[0].id");
        checkpointV2 = fork.getString("patrols[0].checkpoints[0].id");
        as("coord").post("/api/patrols/protocols/" + fork.getString("id") + "/publish").then().statusCode(200).body("status", is("ACTIVO"));
        Assertions.assertNotEquals(patrolV1, patrolV2);
    }

    ObjectNode event(String action, UUID execution, String patrolId, String... checkpoints) {
        ObjectNode e = m.createObjectNode().put("action", action).put("assignmentId", assignment.toString())
            .put("executionId", execution.toString()).put("patrolId", patrolId).put("executedAt", Instant.now().toString());
        if ("FINISH".equals(action)) {
            var results = e.putArray("checkpointResults");
            for (String id : checkpoints) results.addObject().put("checkpointId", id).put("result", "CUMPLIDO").put("validatedAt", Instant.now().toString());
        }
        return e;
    }

    ValidatableResponse send(ObjectNode e) {
        return as("agente").contentType(ContentType.JSON).body(e.toString()).post("/api/v1/operator/patrol-executions").then();
    }

    ValidatableResponse current(String patrolId) {
        return as("agente").queryParam("assignmentId", assignment.toString()).queryParam("patrolId", patrolId)
            .get("/api/v1/operator/patrol-executions/current").then();
    }

    @Test void newVersionShowsAndFinishesPatrolStartedOnPreviousVersion() {
        UUID execution = UUID.randomUUID();
        send(event("START", execution, patrolV1)).statusCode(200).body("status", is("IN_PROGRESS"));
        republish();

        current(patrolV2).statusCode(200).body("status", is("IN_PROGRESS")).body("executionId", is(execution.toString()));
        send(event("START", UUID.randomUUID(), patrolV2)).statusCode(409);

        send(event("FINISH", execution, patrolV2, checkpointV2)).statusCode(200).body("status", is("RECEIVED")).body("result", is("COMPLETA"));
        current(patrolV2).statusCode(200).body("status", is("COMPLETED")).body("executionId", is(execution.toString()));
    }

    @Test void queuedFinishWithPreviousVersionPatrolIdIsAccepted() {
        UUID execution = UUID.randomUUID();
        send(event("START", execution, patrolV1)).statusCode(200);
        republish();
        // La cola del teléfono (o el cierre automático) envía el FINISH con el patrolId con que se inició.
        send(event("FINISH", execution, patrolV1, checkpointV1)).statusCode(200).body("result", is("COMPLETA"));
    }

    @Test void finishRejectsPatrolOfAnotherSeries() {
        UUID execution = UUID.randomUUID();
        send(event("START", execution, patrolV1)).statusCode(200);
        Map<String,Object> other = publishPatrolWithCheckpoint(-2.17, -79.92);
        send(event("FINISH", execution, other.get("patrolId").toString(), other.get("checkpointId").toString()))
            .statusCode(400).body(containsString("no corresponde"));
    }
}
