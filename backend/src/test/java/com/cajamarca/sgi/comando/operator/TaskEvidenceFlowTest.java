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

/** Fase 4: el agente envía fotos de Consignas y Bitácora; VISINT valida y el resultado vuelve al agente y a Operación. */
@QuarkusTest
class TaskEvidenceFlowTest {
    @Inject EntityManager em;
    @Inject VisualReviewWorker worker;
    @Inject MockVisintAdapter mock;
    static final Path SAME = Path.of("src/test/resources/fixtures/sample.jpg"), OTHER = Path.of("src/test/resources/fixtures/sample2.jpg");

    @BeforeEach void drainQueue() { worker.processDue(); }

    io.restassured.response.ValidatableResponse result(UUID event) { return as("agente").get("/api/v1/operator/executions/" + event).then(); }
    int reviewStandards(UUID event) {
        return ((Number) em.createNativeQuery("select count(*) from visual_review_standard s join visual_review r on r.id=s.review_id where r.task_execution_id=:e")
            .setParameter("e", event).getSingleResult()).intValue();
    }

    // ───────── Consignas ─────────

    @Test void runtimeListsVigenteConsignmentPhotoEvidence() {
        UUID assignment = ensureAgentAssignment(em);
        Map<String,Object> ids = TaskFixtures.activeConsignment(List.of(SAME, OTHER));
        String task = "consignmentTasks.find{it.consignmentId=='" + ids.get("consignmentId") + "'}";
        String image = as("agente").queryParam("assignmentId", assignment).get("/api/v1/operator/runtime").then().statusCode(200)
            .body(task + ".title", is("Cortina cerrada"))
            .body(task + ".evidences[0].evidenceId", is(ids.get("evidenceId")))
            .body(task + ".evidences[0].visintEnabled", is(true))
            .body(task + ".evidences[0].standardImages.size()", is(2))
            .extract().path(task + ".evidences[0].standardImages[0].id");
        as("agente").queryParam("assignmentId", assignment).get("/api/v1/operator/standard-images/" + image).then().statusCode(200).contentType("image/jpeg");
    }

    @Test void consignmentPhotoIsValidatedOncePerShift() throws Exception {
        Map<String,Object> ids = TaskFixtures.activeConsignment(List.of(OTHER, SAME));
        var first = TaskFixtures.submitTask(em, "CONSIGNMENT_EVIDENCE", ids.get("evidenceId"), SAME, null);
        assertEquals(200, first.status(), first.body());
        assertTrue(first.body().contains("QUEUED_FOR_VISINT"), first.body());
        assertEquals(2, reviewStandards(first.eventId()));
        worker.processDue();
        result(first.eventId()).statusCode(200).body("outcome", is("VALIDATED")).body("captureNo", is(1)).body("message", is("Foto validada."));
        var again = TaskFixtures.submitTask(em, "CONSIGNMENT_EVIDENCE", ids.get("evidenceId"), SAME, null);
        assertEquals(409, again.status());
        assertTrue(again.body().contains("ya fue registrada"), again.body());
        as("coord").get("/api/operation/executions/" + first.eventId()).then().statusCode(200)
            .body("row.module", is("CONSIGNA")).body("row.taskName", is("Foto de la cortina")).body("row.groupName", is("Cortina cerrada"))
            .body("standards.size()", is(2));
    }

    @Test void consignmentRetakeAfterNotValidated() throws Exception {
        Map<String,Object> ids = TaskFixtures.activeConsignment(List.of(SAME));
        var first = TaskFixtures.submitTask(em, "CONSIGNMENT_EVIDENCE", ids.get("evidenceId"), OTHER, null);
        mock.forceNextResult("FAIL");
        worker.processDue();
        result(first.eventId()).statusCode(200).body("outcome", is("NOT_VALIDATED")).body("canRetake", is(true));
        var retake = TaskFixtures.submitTask(em, "CONSIGNMENT_EVIDENCE", ids.get("evidenceId"), SAME, null);
        assertEquals(200, retake.status(), retake.body());
        result(retake.eventId()).statusCode(200).body("captureNo", is(2));
    }

    @Test void draftConsignmentIsNotAvailableToTheAgent() throws Exception {
        Map<String,Object> ids = TaskFixtures.draftConsignmentWithPhoto();
        UUID assignment = ensureAgentAssignment(em);
        var meta = "{\"uploadBatchId\":\"" + UUID.randomUUID() + "\",\"eventId\":\"" + UUID.randomUUID() + "\",\"assignmentId\":\"" + assignment
            + "\",\"targetType\":\"CONSIGNMENT_EVIDENCE\",\"targetId\":\"" + ids.get("evidenceId") + "\",\"items\":[{\"clientEvidenceId\":\"" + UUID.randomUUID()
            + "\",\"capturedAt\":\"" + java.time.Instant.now() + "\",\"source\":\"CAMERA\",\"sha256\":\"" + "a".repeat(64) + "\"}]}";
        as("agente").multiPart("metadata", meta).multiPart("files", "x.jpg", java.nio.file.Files.readAllBytes(SAME), "image/jpeg")
            .post("/api/v1/operator/evidences").then().statusCode(400);
    }

    // ───────── Bitácora ─────────

    @Test void runtimeListsLogbookFieldsWithEvidence() {
        UUID assignment = ensureAgentAssignment(em);
        Map<String,Object> ids = TaskFixtures.activeLogbook(List.of(SAME));
        String task = "logbookTasks.find{it.protocolId=='" + ids.get("protocolId") + "'}";
        as("agente").queryParam("assignmentId", assignment).get("/api/v1/operator/runtime").then().statusCode(200)
            .body(task + ".fields.find{it.name=='Cédula'}.visintEnabled", is(true))
            .body(task + ".fields.find{it.name=='Cédula'}.standardImages.size()", is(1))
            .body(task + ".fields.find{it.name=='Rostro'}.visintEnabled", is(false))
            .body(task + ".fields.name", not(hasItem("Empresa")));
    }

    @Test void logbookEntriesValidateDocumentsAndStoreTheFace() throws Exception {
        Map<String,Object> ids = TaskFixtures.activeLogbook(List.of(SAME));
        UUID entryA = UUID.randomUUID(), entryB = UUID.randomUUID();
        var cedulaA = TaskFixtures.submitTask(em, "LOGBOOK_FIELD", ids.get("Cédula"), SAME, entryA);
        assertEquals(200, cedulaA.status(), cedulaA.body());
        var rostroA = TaskFixtures.submitTask(em, "LOGBOOK_FIELD", ids.get("Rostro"), OTHER, entryA);
        assertEquals(200, rostroA.status(), rostroA.body());
        result(rostroA.eventId()).statusCode(200).body("outcome", is("NOT_REQUIRED")).body("message", is("Foto registrada."));
        var whilePending = TaskFixtures.submitTask(em, "LOGBOOK_FIELD", ids.get("Cédula"), SAME, entryA);
        assertEquals(409, whilePending.status());
        var cedulaB = TaskFixtures.submitTask(em, "LOGBOOK_FIELD", ids.get("Cédula"), SAME, entryB);
        assertEquals(200, cedulaB.status(), "otro visitante (otro registro) puede enviar su cédula: " + cedulaB.body());
        worker.processDue();
        result(cedulaA.eventId()).statusCode(200).body("outcome", is("VALIDATED"));
        as("agente").queryParam("groupId", entryA).get("/api/v1/operator/executions").then().statusCode(200).body("size()", is(2));
        as("coord").get("/api/operation/executions/" + cedulaA.eventId()).then().statusCode(200)
            .body("row.module", is("BITACORA")).body("row.taskName", is("Cédula"));
    }

    @Test void logbookRequiresAnEntryId() throws Exception {
        Map<String,Object> ids = TaskFixtures.activeLogbook(List.of(SAME));
        var noEntry = TaskFixtures.submitTask(em, "LOGBOOK_FIELD", ids.get("Cédula"), SAME, null);
        assertEquals(400, noEntry.status());
        assertTrue(noEntry.body().contains("groupId"), noEntry.body());
    }
}
