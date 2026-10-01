package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.visint.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Fase 3: el agente consulta el resultado de VISINT de su Hito y, si "no cumple", puede tomar una nueva foto en la misma ronda. */
@QuarkusTest
class ExecutionResultTest {
    @Inject EntityManager em;
    @Inject VisualReviewWorker worker;
    @Inject MockVisintAdapter mock;
    static final Path SAME = Path.of("src/test/resources/fixtures/sample.jpg"), OTHER = Path.of("src/test/resources/fixtures/sample2.jpg");

    @BeforeEach void drainQueue() { worker.processDue(); }

    io.restassured.response.ValidatableResponse result(UUID event) {
        return as("agente").get("/api/v1/operator/executions/" + event).then();
    }

    @Test void pendingThenValidated() throws Exception {
        UUID event = VisintFixtures.executeWithPhoto(em, VisintFixtures.publishVisintPatrol(), SAME);
        result(event).statusCode(200).body("eventId", is(event.toString())).body("captureNo", is(1))
            .body("outcome", is("PENDING")).body("validation.status", is("QUEUED_FOR_VISINT")).body("canRetake", is(false))
            .body("message", containsString("Validando"));
        mock.forceNextResult("PASS");
        worker.processDue();
        result(event).statusCode(200).body("outcome", is("VALIDATED")).body("validation.result", is("PASS")).body("canRetake", is(false))
            .body("message", containsString("validada"));
    }

    @Test void scoresFromVisintAreReturned() throws Exception {
        UUID event = VisintFixtures.executeWithPhoto(em, VisintFixtures.publishVisintPatrol(), SAME);
        result(event).statusCode(200).body("validation.quality", nullValue()).body("validation.match", nullValue());
        mock.forceNextResult("PASS");
        worker.processDue();
        // El simulado no trae puntajes; se cargan los que guardaría la respuesta real de VISINT.
        io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
            "update visual_review set quality_valid=true, quality_score=0.7473, match_compatible=true, match_score=0.9582 where task_execution_id=:e")
            .setParameter("e", event).executeUpdate());
        result(event).statusCode(200).body("outcome", is("VALIDATED"))
            .body("validation.quality.valid", is(true)).body("validation.quality.score", is(0.7473f))
            .body("validation.match.compatible", is(true)).body("validation.match.score", is(0.9582f));
    }

    @Test void notValidatedAllowsRetakeInTheSameRound() throws Exception {
        Map<String,Object> ids = VisintFixtures.publishVisintPatrol();
        UUID run = UUID.randomUUID();
        var first = VisintFixtures.submitWithPhoto(em, ids, OTHER, run);
        assertEquals(200, first.status());
        mock.forceNextResult("FAIL");
        worker.processDue();
        result(first.eventId()).statusCode(200).body("outcome", is("NOT_VALIDATED")).body("canRetake", is(true))
            .body("message", containsString("nueva foto"));

        var retake = VisintFixtures.submitWithPhoto(em, ids, SAME, run);
        assertEquals(200, retake.status(), retake.body());
        result(retake.eventId()).statusCode(200).body("captureNo", is(2)).body("outcome", is("PENDING"));
        result(first.eventId()).statusCode(200).body("canRetake", is(false));
        as("coord").get("/api/operation/executions/" + retake.eventId()).then().statusCode(200).body("row.captureNo", is(2));
    }

    @Test void noRetakeWhilePendingOrAfterValidated() throws Exception {
        Map<String,Object> ids = VisintFixtures.publishVisintPatrol();
        UUID run = UUID.randomUUID();
        assertEquals(200, VisintFixtures.submitWithPhoto(em, ids, SAME, run).status());
        var whilePending = VisintFixtures.submitWithPhoto(em, ids, SAME, run);
        assertEquals(409, whilePending.status());
        assertTrue(whilePending.body().contains("validando"), whilePending.body());
        mock.forceNextResult("PASS");
        worker.processDue();
        var afterPass = VisintFixtures.submitWithPhoto(em, ids, SAME, run);
        assertEquals(409, afterPass.status());
        assertTrue(afterPass.body().contains("ya fue registrado"), afterPass.body());
    }

    @Test void technicalErrorIsNotBlamedOnTheAgent() throws Exception {
        UUID event = VisintFixtures.executeWithPhoto(em, VisintFixtures.publishVisintPatrol(), SAME);
        mock.forceNextResult("ERROR");
        worker.processDue();
        result(event).statusCode(200).body("outcome", is("TECHNICAL_ERROR")).body("canRetake", is(false))
            .body("message", containsString("queda registrado"));
    }

    @Test void checkpointWithoutVisintNeedsNoValidation() throws Exception {
        UUID event = VisintFixtures.executeWithPhoto(em, publishPatrolWithCheckpoint(-2.17, -79.92), SAME);
        result(event).statusCode(200).body("outcome", is("NOT_REQUIRED")).body("validation.status", is("NOT_REQUESTED")).body("canRetake", is(false));
    }

    @Test void listByRoundShowsEveryCapture() throws Exception {
        Map<String,Object> ids = VisintFixtures.publishVisintPatrol();
        UUID run = UUID.randomUUID();
        UUID first = VisintFixtures.submitWithPhoto(em, ids, OTHER, run).eventId();
        mock.forceNextResult("FAIL");
        worker.processDue();
        UUID second = VisintFixtures.submitWithPhoto(em, ids, SAME, run).eventId();
        as("agente").queryParam("patrolRunId", run).get("/api/v1/operator/executions").then().statusCode(200)
            .body("size()", is(2)).body("eventId", contains(first.toString(), second.toString()))
            .body("outcome", contains("NOT_VALIDATED", "PENDING"));
    }

    @Test void onlyTheAgentCanReadIt() throws Exception {
        UUID event = VisintFixtures.executeWithPhoto(em, VisintFixtures.publishVisintPatrol(), SAME);
        as("coord").get("/api/v1/operator/executions/" + event).then().statusCode(anyOf(is(403), is(404)));
        result(UUID.randomUUID()).statusCode(404);
    }
}
