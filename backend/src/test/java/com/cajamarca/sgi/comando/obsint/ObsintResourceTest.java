package com.cajamarca.sgi.comando.obsint;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Configuración OBSINT del Punto (cámaras, protocolo versionado, consignas con horario) y Consola de punta a punta. */
@QuarkusTest
class ObsintResourceTest {
    static final String POINT = "40000000-0000-0000-0000-000000000001";
    static final Path JPG = Path.of("src/test/resources/fixtures/sample.jpg");
    static final Path NOT_IMAGE = Path.of("src/test/resources/fixtures/not-an-image.txt");
    static final List<Map<String, Object>> ALL_DAY = java.util.stream.IntStream.rangeClosed(1, 7)
        .mapToObj(d -> Map.<String, Object>of("dayOfWeek", d, "start", "00:00", "end", "23:59:59")).toList();

    static String camera(String name) {
        return as("coord").contentType(ContentType.JSON).body(Map.of("family", "VIDEO", "componentType", "Cámara Bala", "name", name,
                "ip", "192.168.1.10", "port", 554, "username", "comando_view", "password", "secreta", "streamUrl", "obsint/videos/1-1.mp4"))
            .post("/api/secelec/points/" + POINT + "/components").then().statusCode(200).extract().path("id");
    }

    static Map<String, Object> consigna(String title, List<String> cameraIds, List<Map<String, Object>> schedule) {
        return Map.of("title", title, "instruction", "No debe existir vehículo estacionado.", "category", "MOBILITY",
            "timeLimitSeconds", 60, "cameraIds", cameraIds, "schedule", schedule);
    }

    /** Deja un protocolo ACTIVO con una consigna vigente todo el día; devuelve el código de la consigna. */
    static String activeProtocolWithConsigna() {
        String cam = camera("Cámara prueba consola");
        String draft = as("coord").post("/api/obsint/config/points/" + POINT + "/protocols/draft").then().statusCode(200)
            .body("status", is("BORRADOR")).extract().path("id");
        String code = as("coord").contentType(ContentType.JSON).body(consigna("Consola " + UUID.randomUUID(), List.of(cam), ALL_DAY))
            .post("/api/obsint/config/protocols/" + draft + "/consignas").then().statusCode(200).extract().path("code");
        as("coord").post("/api/obsint/config/protocols/" + draft + "/publish").then().statusCode(200).body("status", is("INACTIVO"));
        as("coord").post("/api/obsint/config/protocols/" + draft + "/activate").then().statusCode(200).body("status", is("ACTIVO"));
        return code;
    }

    @Test void protocolLifecycleAndValidation() {
        String cam = camera("Cámara Domo Patio");
        String draft = as("coord").post("/api/obsint/config/points/" + POINT + "/protocols/draft").then().statusCode(200).extract().path("id");
        assertEquals(draft, as("coord").post("/api/obsint/config/points/" + POINT + "/protocols/draft").then().extract().path("id"), "un solo borrador");

        as("coord").contentType(ContentType.JSON).body(consigna("Cinco cámaras", List.of(cam, cam, UUID.randomUUID().toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString()), ALL_DAY))
            .post("/api/obsint/config/protocols/" + draft + "/consignas").then().statusCode(400);
        as("coord").contentType(ContentType.JSON).body(consigna("Cámara ajena", List.of(UUID.randomUUID().toString()), ALL_DAY))
            .post("/api/obsint/config/protocols/" + draft + "/consignas").then().statusCode(400).body("message", containsString("cámaras"));
        as("coord").contentType(ContentType.JSON).body(consigna("Horario al revés", List.of(cam), List.of(Map.of("dayOfWeek", 1, "start", "18:00", "end", "08:00"))))
            .post("/api/obsint/config/protocols/" + draft + "/consignas").then().statusCode(400);

        String consignaId = as("coord").contentType(ContentType.JSON).body(consigna("Sin horario", List.of(cam), List.of()))
            .post("/api/obsint/config/protocols/" + draft + "/consignas").then().statusCode(200)
            .body("code", startsWith("OBS-")).body("cameraIds", hasItem(cam)).extract().path("id");
        as("coord").post("/api/obsint/config/protocols/" + draft + "/publish").then().statusCode(409).body("message", containsString("horario"));

        as("coord").contentType(ContentType.JSON).body(consigna("Con horario", List.of(cam), List.of(Map.of("dayOfWeek", 1, "start", "08:00", "end", "18:00"))))
            .put("/api/obsint/config/consignas/" + consignaId).then().statusCode(200).body("schedule[0].start", is("08:00"));
        as("coord").post("/api/obsint/config/protocols/" + draft + "/publish").then().statusCode(200).body("status", is("INACTIVO"));
        as("coord").contentType(ContentType.JSON).body(consigna("Editar publicado", List.of(cam), ALL_DAY))
            .put("/api/obsint/config/consignas/" + consignaId).then().statusCode(409);

        as("coord").post("/api/obsint/config/protocols/" + draft + "/activate").then().statusCode(200).body("status", is("ACTIVO"));
        as("coord").get("/api/obsint/config/points/" + POINT + "/summary").then().statusCode(200)
            .body("status", is("complete")).body("activeVersion", notNullValue());

        var next = as("coord").post("/api/obsint/config/points/" + POINT + "/protocols/draft").then().statusCode(200).extract();
        assertNotEquals(draft, next.path("id"));
        assertTrue(next.<List<String>>path("consignas.code").contains(as("coord").get("/api/obsint/config/points/" + POINT + "/protocols")
            .then().extract().<String>path("find{it.id=='" + draft + "'}.consignas[0].code")), "la nueva versión copia las consignas");
        as("coord").delete("/api/obsint/config/protocols/" + next.path("id")).then().statusCode(204);

        as("coord").delete("/api/secelec/components/" + cam).then().statusCode(409);
    }

    @Test void consoleCaseWithConfiguredConsignas() throws Exception {
        String code = activeProtocolWithConsigna();
        String caseId = as("dnacional").post("/api/obsint/cases").then().statusCode(200).body("status", is("IN_PROGRESS")).extract().path("id");
        assertEquals(caseId, as("dnacional").post("/api/obsint/cases").then().statusCode(200).extract().path("id"), "un caso a la vez");

        List<String> taskIds = as("dnacional").get("/api/obsint/cases/" + caseId).then().statusCode(200).extract().path("tasks.id");
        if (taskIds.size() > 1) as("dnacional").post("/api/obsint/tasks/" + taskIds.get(1) + "/start").then().statusCode(409).body("message", containsString("orden"));

        List<String> codes = new ArrayList<>();
        for (String id : taskIds) {
            var task = as("dnacional").post("/api/obsint/tasks/" + id + "/start").then().statusCode(200)
                .body("status", is("IN_PROGRESS")).body("deadline", notNullValue()).extract();
            codes.add(task.path("consignaCode"));
            String cameraId = task.path("cameras[0].id");
            if (id.equals(taskIds.get(0))) {
                String evidenceId = as("dnacional").contentType(ContentType.MULTIPART).multiPart("file", JPG.toFile(), "image/jpeg")
                    .multiPart("cameraId", cameraId).multiPart("videoTimeSeconds", "12.5")
                    .post("/api/obsint/tasks/" + id + "/evidence").then().statusCode(200).extract().path("id");
                assertArrayEquals(Files.readAllBytes(JPG), as("dnacional").get("/api/obsint/tasks/" + id + "/evidence/" + evidenceId + "/content")
                    .then().statusCode(200).extract().asByteArray());
                as("dnacional").contentType(ContentType.MULTIPART).multiPart("file", NOT_IMAGE.toFile(), "image/jpeg").multiPart("cameraId", cameraId)
                    .post("/api/obsint/tasks/" + id + "/evidence").then().statusCode(400);
                as("dnacional").contentType(ContentType.JSON).body(Map.of("text", "Revise la garita norte"))
                    .post("/api/obsint/tasks/" + id + "/messages").then().statusCode(200).body("deliveryStatus", is("SENT"));
                as("dnacional").contentType(ContentType.JSON).body(Map.of("decision", "NO", "justification", " "))
                    .put("/api/obsint/tasks/" + id + "/answer").then().statusCode(409).body("message", containsString("justificación"));
            }
            as("dnacional").contentType(ContentType.JSON).body(Map.of("decision", "YES", "justification", "Sin novedad"))
                .put("/api/obsint/tasks/" + id + "/answer").then().statusCode(200);
        }
        assertTrue(codes.contains(code), "el caso incluye la consigna del protocolo activo");
        as("dnacional").get("/api/obsint/cases/" + caseId).then().statusCode(200).body("status", is("COMPLETED"));
        as("dnacional").get("/api/obsint/cases/current").then().statusCode(204);
    }

    @Test void anotherUserCannotSeeTheCase() {
        activeProtocolWithConsigna();
        String caseId = as("dzonal").post("/api/obsint/cases").then().statusCode(200).extract().path("id");
        String taskId = as("dzonal").get("/api/obsint/cases/" + caseId).then().extract().path("tasks[0].id");
        as("jregional").get("/api/obsint/cases/" + caseId).then().statusCode(404);
        as("jregional").post("/api/obsint/tasks/" + taskId + "/start").then().statusCode(404);
    }

    @Test void clientHasNoAccess() {
        as("cliente").post("/api/obsint/cases").then().statusCode(403);
        as("cliente").get("/api/obsint/config/points/" + POINT + "/protocols").then().statusCode(403);
    }
}
