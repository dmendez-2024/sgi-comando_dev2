package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.storage.Digests;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import io.quarkus.narayana.jta.QuarkusTransaction;
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

/** Foto de un reporte operativo (Hallazgo, Incidente, Vulnerabilidad) de SGI Operador: llega aparte del reporte y Comando la muestra en Consola. */
@QuarkusTest
class OperationalReportPhotoTest {
    @Inject EntityManager em;
    final ObjectMapper m = new ObjectMapper();
    static final Path JPG = Path.of("src/test/resources/fixtures/sample.jpg");
    UUID assignment;

    // Consola solo muestra registros de Compañías en el alcance del usuario; la Compañía del Puesto del agente la tiene asignada asistente.
    static final String CONSOLE = "asistente";

    @BeforeEach void setUp() { assignment = ensureAgentAssignment(em); }

    void submitReport(UUID reportId, int photoCount) {
        ObjectNode body = m.createObjectNode().put("reportId", reportId.toString()).put("assignmentId", assignment.toString())
            .put("type", "FINDING").put("category", "Limpieza").put("title", "Hallazgo de prueba").put("description", "Basura en el acceso")
            .put("severity", "MEDIUM").put("occurredAt", Instant.now().toString()).put("photoCount", photoCount);
        as("agente").header("Idempotency-Key", reportId.toString()).contentType(ContentType.JSON).body(body.toString())
            .post("/api/v1/operator/reports").then().statusCode(200).body("photoCount", is(photoCount));
    }

    io.restassured.response.ValidatableResponse uploadPhoto(UUID reportId, UUID clientEvidenceId) throws Exception {
        ObjectNode meta = m.createObjectNode().put("uploadBatchId", UUID.randomUUID().toString()).put("eventId", reportId.toString())
            .put("assignmentId", assignment.toString()).put("targetType", "OPERATIONAL_REPORT").put("targetId", reportId.toString());
        meta.putArray("items").addObject().put("clientEvidenceId", clientEvidenceId.toString()).put("capturedAt", Instant.now().toString())
            .put("source", "CAMERA").put("sha256", Digests.sha256Hex(Files.readAllBytes(JPG)));
        return as("agente").multiPart("metadata", meta.toString()).multiPart("files", clientEvidenceId + ".jpg", Files.readAllBytes(JPG), "image/jpeg")
            .post("/api/v1/operator/evidences").then();
    }

    @Test void photoUploadedByTheAppIsShownInTheConsole() throws Exception {
        UUID reportId = UUID.randomUUID();
        submitReport(reportId, 1);

        // Antes de que llegue el archivo, Consola muestra el lugar de la foto declarada.
        as(CONSOLE).get("/api/operation/events/" + reportId + "/evidences").then().statusCode(200)
            .body("size()", is(1)).body("[0].source", is("MISSING")).body("[0].label", is("Fotografía del reporte"));

        uploadPhoto(reportId, UUID.randomUUID()).statusCode(200).body("results[0].status", is("STORED"));

        String photoId = as(CONSOLE).get("/api/operation/events/" + reportId + "/evidences").then().statusCode(200)
            .body("size()", is(1)).body("[0].source", is("OBJECT")).body("[0].label", is("Fotografía del reporte"))
            .extract().path("[0].id");
        byte[] served = as(CONSOLE).get("/api/operation/events/" + reportId + "/evidences/" + photoId + "/content").then()
            .statusCode(200).contentType(startsWith("image/jpeg")).extract().asByteArray();
        Assertions.assertArrayEquals(Files.readAllBytes(JPG), served);
    }

    @Test void retryFromTheOutboxIsIdempotentAndOnlyOnePhotoIsKept() throws Exception {
        UUID reportId = UUID.randomUUID(), clientId = UUID.randomUUID();
        submitReport(reportId, 1);
        uploadPhoto(reportId, clientId).statusCode(200).body("results[0].status", is("STORED"));
        uploadPhoto(reportId, clientId).statusCode(200).body("results[0].status", is("ALREADY_STORED"));
        uploadPhoto(reportId, UUID.randomUUID()).statusCode(200).body("results[0].status", is("REJECTED")).body("results[0].reason", is("TOO_MANY_PHOTOS"));
        as(CONSOLE).get("/api/operation/events/" + reportId + "/evidences").then().statusCode(200).body("size()", is(1));
    }

    @Test void photoBeforeTheReportArrivesAsksTheAppToRetry() throws Exception {
        uploadPhoto(UUID.randomUUID(), UUID.randomUUID()).statusCode(409);
    }

    @Test void reportWithoutPhotoRejectsAPhotoAndShowsNothing() throws Exception {
        UUID reportId = UUID.randomUUID();
        submitReport(reportId, 0);
        uploadPhoto(reportId, UUID.randomUUID()).statusCode(400);
        as(CONSOLE).get("/api/operation/events/" + reportId + "/evidences").then().statusCode(200).body("size()", is(0));
    }

    @Test void reportOfAnotherAgentRejectsThePhoto() throws Exception {
        UUID reportId = UUID.randomUUID();
        submitReport(reportId, 1);
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
            update operator_operational_report r set employee_id=(select e.employee_id from employee_operational_snapshot e
              where e.instance_country_id=r.instance_country_id and e.employee_id<>r.employee_id order by e.employee_id limit 1)
            where r.id=:id""").setParameter("id", reportId).executeUpdate());
        uploadPhoto(reportId, UUID.randomUUID()).statusCode(403);
    }

    @Test void photoOfAnotherReportIsNotServed() throws Exception {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        submitReport(first, 1);
        submitReport(second, 1);
        uploadPhoto(first, UUID.randomUUID()).statusCode(200);
        String photoId = as(CONSOLE).get("/api/operation/events/" + first + "/evidences").then().extract().path("[0].id");
        as(CONSOLE).get("/api/operation/events/" + second + "/evidences/" + photoId + "/content").then().statusCode(404);
    }
}
