package com.cajamarca.sgi.comando.obsint;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Consola OBSINT de punta a punta con el catálogo demo (sgi.uat.features-enabled=true en test). */
@QuarkusTest
class ObsintResourceTest {
    static final Path JPG = Path.of("src/test/resources/fixtures/sample.jpg");
    static final Path NOT_IMAGE = Path.of("src/test/resources/fixtures/not-an-image.txt");

    /** Cada prueba usa su propio usuario para no heredar el caso en curso de otra. */
    String openCase(String user) {
        return as(user).post("/api/obsint/cases").then().statusCode(200)
            .body("status", is("IN_PROGRESS")).body("tasks.size()", is(3)).body("code", startsWith("CAJ-OBS-"))
            .extract().path("id");
    }

    @Test void fullCaseInOrderWithEvidenceAndMessages() throws Exception {
        String caseId = openCase("dnacional");
        assertEquals(caseId, as("dnacional").post("/api/obsint/cases").then().statusCode(200).extract().path("id"), "un caso a la vez");
        as("dnacional").get("/api/obsint/cases/current").then().statusCode(200).body("id", is(caseId));

        List<String> taskIds = as("dnacional").get("/api/obsint/cases/" + caseId).then().statusCode(200).extract().path("tasks.id");
        String first = taskIds.get(0);
        as("dnacional").post("/api/obsint/tasks/" + taskIds.get(1) + "/start").then().statusCode(409).body(containsString("en orden"));

        var task = as("dnacional").post("/api/obsint/tasks/" + first + "/start").then().statusCode(200)
            .body("status", is("IN_PROGRESS")).body("position", is(1)).body("totalTasks", is(3)).body("consignaCode", is("OBS-001"))
            .body("cameras.size()", is(4)).body("cameras[0].streamUrl", is("obsint/videos/1-1.mp4")).body("deadline", notNullValue()).extract();
        String deadline = task.path("deadline");
        String cameraId = task.path("cameras[0].id");
        as("dnacional").post("/api/obsint/tasks/" + first + "/start").then().statusCode(200).body("deadline", is(deadline));

        String evidenceId = as("dnacional").contentType(ContentType.MULTIPART).multiPart("file", JPG.toFile(), "image/jpeg")
            .multiPart("cameraId", cameraId).multiPart("videoTimeSeconds", "12.5")
            .post("/api/obsint/tasks/" + first + "/evidence").then().statusCode(200).body("contentType", is("image/jpeg")).extract().path("id");
        assertArrayEquals(Files.readAllBytes(JPG), as("dnacional").get("/api/obsint/tasks/" + first + "/evidence/" + evidenceId + "/content")
            .then().statusCode(200).extract().asByteArray());
        as("dnacional").contentType(ContentType.MULTIPART).multiPart("file", NOT_IMAGE.toFile(), "image/jpeg").multiPart("cameraId", cameraId)
            .post("/api/obsint/tasks/" + first + "/evidence").then().statusCode(400);

        as("dnacional").contentType(ContentType.JSON).body(Map.of("text", "Revise la garita norte"))
            .post("/api/obsint/tasks/" + first + "/messages").then().statusCode(200).body("deliveryStatus", is("SENT")).body("operator", is("dnacional"));
        as("dnacional").get("/api/obsint/tasks/" + first + "/messages").then().statusCode(200).body("size()", is(1));

        as("dnacional").contentType(ContentType.JSON).body(Map.of("decision", "NO", "justification", " "))
            .put("/api/obsint/tasks/" + first + "/answer").then().statusCode(409);
        as("dnacional").contentType(ContentType.JSON).body(Map.of("decision", "NO", "justification", "Ingresa con mochila"))
            .put("/api/obsint/tasks/" + first + "/answer").then().statusCode(200)
            .body("currentTaskId", is(taskIds.get(1))).body("tasks[0].decision", is("NO")).body("tasks[0].timedOut", is(false));
        as("dnacional").get("/api/obsint/tasks/" + first + "/evidence").then().statusCode(200).body("size()", is(1));

        for (String id : taskIds.subList(1, 3)) {
            as("dnacional").post("/api/obsint/tasks/" + id + "/start").then().statusCode(200);
            as("dnacional").contentType(ContentType.JSON).body(Map.of("decision", "YES", "justification", "Sin novedad"))
                .put("/api/obsint/tasks/" + id + "/answer").then().statusCode(200);
        }
        as("dnacional").get("/api/obsint/cases/" + caseId).then().statusCode(200).body("status", is("COMPLETED")).body("currentTaskId", nullValue());
        as("dnacional").get("/api/obsint/cases/current").then().statusCode(204);
    }

    @Test void anotherUserCannotSeeTheCase() {
        String caseId = openCase("dzonal");
        String taskId = as("dzonal").get("/api/obsint/cases/" + caseId).then().extract().path("tasks[0].id");
        as("jregional").get("/api/obsint/cases/" + caseId).then().statusCode(404);
        as("jregional").post("/api/obsint/tasks/" + taskId + "/start").then().statusCode(404);
    }

    @Test void clientAndAgentHaveNoAccess() {
        as("cliente").post("/api/obsint/cases").then().statusCode(403);
    }
}
