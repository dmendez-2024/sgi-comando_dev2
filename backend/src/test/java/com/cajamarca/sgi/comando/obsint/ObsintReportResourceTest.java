package com.cajamarca.sgi.comando.obsint;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.cajamarca.sgi.comando.obsint.ObsintResourceTest.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reportes OBSINT: solo Presidente y Director Nacional; el PDF trae la justificación completa y el resultado en español. */
@QuarkusTest
class ObsintReportResourceTest {

    @Test void wrapKeepsEveryWordAndRespectsTheWidth() {
        String text = "Justificación: " + "palabra ".repeat(80) + "x".repeat(ObsintReportResource.LINE_WIDTH + 7) + " fin";
        List<String> lines = ObsintReportResource.wrap(text);
        assertTrue(lines.size() > 1);
        lines.forEach(l -> assertTrue(l.length() <= ObsintReportResource.LINE_WIDTH, l));
        assertEquals(text.replaceAll("\\s+", ""), String.join("", lines).replaceAll("\\s+", ""), "no se pierde texto");
    }

    @Test void decisionInSpanishAndAccentsForTheWinAnsiFont() {
        assertEquals("Sí", ObsintReportResource.decisionLabel(ObsintCaseTask.Decision.YES));
        assertEquals("Incierto", ObsintReportResource.decisionLabel(ObsintCaseTask.Decision.UNCERTAIN));
        assertEquals("Sin respuesta", ObsintReportResource.decisionLabel(null));
        assertEquals("S\\355 \\(a\\) Compa\\361\\355a", ObsintReportResource.pdfString("Sí (a) Compañía"));
        assertEquals("?", ObsintReportResource.pdfString("✓"));
    }

    @Test void onlyPresidentAndNationalDirectorAndThePdfHasTheWholeJustification() {
        as("coord").get("/api/obsint/reports").then().statusCode(403);
        as("dnacional").get("/api/obsint/reports").then().statusCode(200);
        as("don").get("/api/obsint/reports").then().statusCode(200);  // DIRECTOR_OPERACIONES_NACIONAL: el cargo real en IDENT

        activeProtocolWithConsigna();
        finishRunningCase("coord");
        String caseId = as("coord").post("/api/obsint/cases").then().statusCode(200).extract().path("id");
        String ending = "final-" + UUID.randomUUID();
        String justification = "Se observa el patio despejado y sin vehículos. ".repeat(6) + ending;
        List<String> taskIds = as("coord").get("/api/obsint/cases/" + caseId).then().statusCode(200).extract().path("tasks.id");
        for (String id : taskIds) {
            as("coord").post("/api/obsint/tasks/" + id + "/start").then().statusCode(200);
            as("coord").contentType(ContentType.JSON).body(Map.of("decision", "YES", "justification", justification))
                .put("/api/obsint/tasks/" + id + "/answer").then().statusCode(200);
        }
        String pointId = as("presidente").get("/api/obsint/reports").then().statusCode(200)
            .body("caseId", hasItem(caseId)).extract().path("find { it.caseId == '" + caseId + "' }.pointId");
        byte[] pdf = as("presidente").get("/api/obsint/reports/" + caseId + "/points/" + pointId + "/pdf").then().statusCode(200)
            .contentType("application/pdf").extract().asByteArray();
        String content = new String(pdf, StandardCharsets.ISO_8859_1);
        assertTrue(content.startsWith("%PDF-1.4"));
        assertTrue(content.contains("Resultado: S\\355"), "resultado en español con tilde");
        assertTrue(content.contains(ending), "la justificación no se corta");
    }
}
