package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.storage.Digests;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.*;
import static org.hamcrest.Matchers.*;

/** Foto de referencia de una consigna nueva de SGI Operador: llega aparte de la solicitud y Comando la muestra en su detalle. */
@QuarkusTest
class ConsignmentRequestPhotoTest {
    @Inject EntityManager em;
    final ObjectMapper m = new ObjectMapper();
    static final Path JPG = Path.of("src/test/resources/fixtures/sample.jpg");
    UUID assignment;

    @BeforeEach void setUp() { assignment = ensureAgentAssignment(em); }

    void submitRequest(UUID requestId, boolean hasPhoto) {
        ObjectNode body = m.createObjectNode().put("requestId", requestId.toString()).put("assignmentId", assignment.toString())
            .put("title", "Revisar portón lateral").put("instruction", "Verificar candado cada ronda").put("scope", "Puesto")
            .put("character", "Permanente").put("schedule", "Todo el tiempo").put("priority", "Normal")
            .put("hasCoordinates", false).put("hasPhoto", hasPhoto);
        as("agente").header("Idempotency-Key", requestId.toString()).contentType(ContentType.JSON).body(body.toString())
            .post("/api/v1/operator/consignment-review-requests").then().statusCode(200).body("status", is("PENDING"));
    }

    io.restassured.response.ValidatableResponse uploadPhoto(UUID requestId, UUID clientEvidenceId) throws Exception {
        ObjectNode meta = m.createObjectNode().put("uploadBatchId", UUID.randomUUID().toString()).put("eventId", requestId.toString())
            .put("assignmentId", assignment.toString()).put("targetType", "CONSIGNMENT_REQUEST").put("targetId", requestId.toString());
        meta.putArray("items").addObject().put("clientEvidenceId", clientEvidenceId.toString()).put("capturedAt", Instant.now().toString())
            .put("source", "CAMERA").put("sha256", Digests.sha256Hex(Files.readAllBytes(JPG)));
        return as("agente").multiPart("metadata", meta.toString()).multiPart("files", clientEvidenceId + ".jpg", Files.readAllBytes(JPG), "image/jpeg")
            .post("/api/v1/operator/evidences").then();
    }

    @Test void photoUploadedByTheAppIsShownInTheConsignmentDetail() throws Exception {
        UUID requestId = UUID.randomUUID();
        submitRequest(requestId, true);

        // Antes de que llegue el archivo, el detalle muestra el lugar de la foto declarada.
        as("coord").get("/api/operation/events/" + requestId + "/evidences").then().statusCode(200)
            .body("size()", is(1)).body("[0].source", is("MISSING")).body("[0].label", is("Imagen de referencia"));

        uploadPhoto(requestId, UUID.randomUUID()).statusCode(200).body("results[0].status", is("STORED"));

        String photoId = as("coord").get("/api/operation/events/" + requestId + "/evidences").then().statusCode(200)
            .body("size()", is(1)).body("[0].source", is("OBJECT")).body("[0].label", is("Imagen de referencia"))
            .extract().path("[0].id");
        byte[] served = as("coord").get("/api/operation/events/" + requestId + "/evidences/" + photoId + "/content").then()
            .statusCode(200).contentType(startsWith("image/jpeg")).extract().asByteArray();
        Assertions.assertArrayEquals(Files.readAllBytes(JPG), served);
    }

    @Test void retryFromTheOutboxIsIdempotent() throws Exception {
        UUID requestId = UUID.randomUUID(), clientId = UUID.randomUUID();
        submitRequest(requestId, true);
        uploadPhoto(requestId, clientId).statusCode(200).body("results[0].status", is("STORED"));
        uploadPhoto(requestId, clientId).statusCode(200).body("results[0].status", is("ALREADY_STORED"));
        as("coord").get("/api/operation/events/" + requestId + "/evidences").then().statusCode(200).body("size()", is(1));
    }

    @Test void photoBeforeTheRequestArrivesAsksTheAppToRetry() throws Exception {
        uploadPhoto(UUID.randomUUID(), UUID.randomUUID()).statusCode(409);
    }

    @Test void requestWithoutPhotoRejectsAPhotoAndShowsNothing() throws Exception {
        UUID requestId = UUID.randomUUID();
        submitRequest(requestId, false);
        uploadPhoto(requestId, UUID.randomUUID()).statusCode(400);
        as("coord").get("/api/operation/events/" + requestId + "/evidences").then().statusCode(200).body("size()", is(0));
    }

    @Test void photoOfAnotherRequestIsNotServed() throws Exception {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        submitRequest(first, true);
        submitRequest(second, true);
        uploadPhoto(first, UUID.randomUUID()).statusCode(200);
        String photoId = as("coord").get("/api/operation/events/" + first + "/evidences").then().extract().path("[0].id");
        as("coord").get("/api/operation/events/" + second + "/evidences/" + photoId + "/content").then().statusCode(404);
    }
}
