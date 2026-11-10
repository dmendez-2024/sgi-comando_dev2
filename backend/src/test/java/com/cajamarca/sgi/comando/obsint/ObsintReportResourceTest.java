package com.cajamarca.sgi.comando.obsint;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import static com.cajamarca.sgi.comando.obsint.ObsintResourceTest.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Reportes OBSINT (diapositivas 12 y 13): un Informe Diario por Punto, solo para Presidente y Director Nacional,
 * y la frecuencia de cada consigna en la Consola.
 */
@QuarkusTest
class ObsintReportResourceTest {
    @Inject EntityManager em;

    @Test void frequencyDecidesWhenAConsignaComesBack() {
        Instant now = Instant.parse("2026-10-28T15:00:00Z");
        assertTrue(ObsintService.due("EVERY_2_HOURS", null, now), "nunca respondida");
        assertFalse(ObsintService.due("EVERY_2_HOURS", now.minus(Duration.ofMinutes(119)), now));
        assertTrue(ObsintService.due("EVERY_2_HOURS", now.minus(Duration.ofHours(2)), now));
        assertFalse(ObsintService.due("EVERY_4_HOURS", now.minus(Duration.ofHours(3)), now));
        assertFalse(ObsintService.due("EVERY_SHIFT", now.minus(Duration.ofHours(11)), now));
        assertTrue(ObsintService.due("EVERY_SHIFT", now.minus(Duration.ofHours(12)), now));
        // 10:00 en Ecuador: lo respondido ayer 23:00 (hora de Ecuador) ya no cuenta; lo de hoy 06:00 sí.
        assertTrue(ObsintService.due("DAILY", Instant.parse("2026-10-28T04:00:00Z"), now));
        assertFalse(ObsintService.due("DAILY", Instant.parse("2026-10-28T11:00:00Z"), now));
    }

    @Test void complianceLeavesUncertainOutAndTheTextUsesTheWinAnsiFont() {
        var summary = new ObsintDailyReport.Summary(3, 6, 3, 1, 2, 0.75, 4 / 6.0, 52);
        var lines = List.of(new ObsintDailyReport.ConsignaLine("OBS-001", "Uso de casco", "SSO", "Cada turno", 6, 3, 1, 2, 0.75));
        String text = ObsintDailyReport.observations(summary, lines);
        assertTrue(text.contains("75.0 %") && text.contains("Uso de casco (1)") && text.contains("2 verificaciones quedaron como Incierto")
            && text.contains("verificabilidad de 66.7 %") && text.contains("por debajo del 90 %"), text);
        PDType1Font helvetica = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        assertEquals("Sí Compañía ?", ObsintReportPdf.safe("Sí Compañía ✓", helvetica));
    }

    @Test void dailyReportForPresidentAndNationalDirector() throws Exception {
        as("coord").get("/api/obsint/reports").then().statusCode(403);
        as("dnacional").get("/api/obsint/reports").then().statusCode(200);
        as("don").get("/api/obsint/reports").then().statusCode(200);  // DIRECTOR_OPERACIONES_NACIONAL: el cargo real en IDENT
        as("presidente").get("/api/obsint/reports?from=2026-10-28&to=2026-10-01").then().statusCode(400);

        String code = activeProtocolWithConsigna();
        finishRunningCase("monitor");
        String caseId = as("monitor").post("/api/obsint/cases").then().statusCode(200).extract().path("id");
        String ending = "final-" + UUID.randomUUID();
        String justification = "Se observa un vehículo estacionado en la zona prohibida durante la ronda. ".repeat(3) + ending;
        String failedTask = null;
        List<String> taskIds = as("monitor").get("/api/obsint/cases/" + caseId).then().statusCode(200).extract().path("tasks.id");
        for (String id : taskIds) {
            var task = as("monitor").post("/api/obsint/tasks/" + id + "/start").then().statusCode(200).extract();
            boolean mine = code.equals(task.path("consignaCode"));
            if (mine) {
                failedTask = id;
                String cameraId = task.path("cameras[0].id");
                as("monitor").contentType(ContentType.MULTIPART).multiPart("file", JPG.toFile(), "image/jpeg")
                    .multiPart("cameraId", cameraId).multiPart("videoTimeSeconds", "3.5")
                    .post("/api/obsint/tasks/" + id + "/evidence").then().statusCode(200);
            }
            as("monitor").contentType(ContentType.JSON).body(Map.of("decision", mine ? "NO" : "YES", "justification", mine ? justification : "Revisada."))
                .put("/api/obsint/tasks/" + id + "/answer").then().statusCode(200);
        }
        assertNotNull(failedTask, "el caso incluye la consigna nueva");

        // Frecuencia: recién respondida ("cada turno"), no vuelve en el siguiente caso.
        var next = as("monitor").post("/api/obsint/cases").then().extract();
        if (next.statusCode() == 200) {
            List<String> ids = as("monitor").get("/api/obsint/cases/" + next.path("id")).then().statusCode(200).extract().path("tasks.id");
            for (String id : ids) assertNotEquals(code, as("monitor").get("/api/obsint/tasks/" + id).then().extract().path("consignaCode"));
            finishRunningCase("monitor");
        } else assertEquals(409, next.statusCode());

        String today = LocalDate.now(ObsintPlatform.DEFAULT_ZONE).toString();
        as("presidente").get("/api/obsint/reports?from=" + today + "&to=" + today).then().statusCode(200)
            .body("findAll { it.pointId == '" + POINT + "' && it.date == '" + today + "' }.size()", is(1));
        byte[] pdf = as("presidente").get("/api/obsint/reports/points/" + POINT + "/days/" + today + "/pdf").then().statusCode(200)
            .contentType("application/pdf").extract().asByteArray();
        // El producto sale del dispositivo OBSINT de SIC:COM del Punto (si no tiene, el nombre por defecto).
        String product = io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> {
            List<?> rows = em.createNativeQuery("""
                select coalesce(nullif(trim(d.product), ''), nullif(trim(d.name), '')) from electronic_monitoring_post d
                where d.point_id=cast(:point as uuid) and upper(d.post_type)='OBSINT'
                order by case when upper(d.commercial_status)='ACTIVE' then 0 else 1 end, d.code limit 1""")
                .setParameter("point", POINT).getResultList();
            return rows.isEmpty() || rows.get(0) == null ? ObsintPlatform.DEFAULT_PRODUCT : rows.get(0).toString();
        });
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(doc).replaceAll("\\s+", " ");
            for (String expected : List.of("Informe Diario de Consignas", "Resumen General", "Desempeño por Consigna", "Incumplimientos (",
                    "Observaciones Generales", "VERIFICABILIDAD", "INCIERTAS", code, "Se observa un veh", product.replaceAll("\\s+", " ")))
                assertTrue(text.contains(expected), "falta en el PDF: " + expected);
            long images = 0;
            for (var page : doc.getPages())
                for (var name : page.getResources().getXObjectNames()) if (page.getResources().getXObject(name) instanceof PDImageXObject) images++;
            assertTrue(images >= 2, "logo y foto de la evidencia del incumplimiento");
        }
        as("presidente").get("/api/obsint/reports/points/" + POINT + "/days/2001-01-01/pdf").then().statusCode(404);
    }
}
