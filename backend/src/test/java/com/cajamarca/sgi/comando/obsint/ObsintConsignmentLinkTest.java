package com.cajamarca.sgi.comando.obsint;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.cajamarca.sgi.comando.obsint.ObsintResourceTest.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * OBSINT como "puesto" en Consignas (diapositiva 9): la consigna marcada OBSINT se vincula a una tarea OBSINT (cámaras y horario)
 * y la Consola la muestra con la instrucción de Consignas; si deja de estar marcada, la Consola ya no la muestra.
 */
@QuarkusTest
class ObsintConsignmentLinkTest {

    /** Protocolo de Consignas activo en el Punto con una consigna de solo OBSINT; devuelve código e instrucción. */
    static Map<String, String> activeObsintConsignment(String instruction) {
        String protocolId = as("coord").contentType(ContentType.JSON).body(Map.of("pointId", POINT, "name", "Consignas OBSINT " + UUID.randomUUID()))
            .post("/api/consignments/protocols").then().statusCode(200).extract().path("id");
        var item = as("coord").contentType(ContentType.JSON).body(Map.of("title", "Uso de casco"))
            .post("/api/consignments/protocols/" + protocolId + "/items").then().statusCode(200).extract();
        String itemId = item.path("id");
        // Puestos específicos sin ningún Puesto: sin OBSINT no es válido; con OBSINT la consigna es solo del monitorista.
        as("coord").contentType(ContentType.JSON).body(Map.of("title", "Uso de casco", "instruction", instruction, "scopeType", "POSTS", "postIds", List.of()))
            .put("/api/consignments/items/" + itemId).then().statusCode(400);
        as("coord").contentType(ContentType.JSON).body(Map.of("title", "Uso de casco", "instruction", instruction, "scopeType", "POSTS", "postIds", List.of(), "obsint", true))
            .put("/api/consignments/items/" + itemId).then().statusCode(200).body("obsint", is(true)).body("postIds", empty());
        as("coord").post("/api/consignments/protocols/" + protocolId + "/publish").then().statusCode(200);
        as("coord").post("/api/consignments/protocols/" + protocolId + "/activate").then().statusCode(200).body("status", is("ACTIVO"));
        return Map.of("protocolId", protocolId, "code", item.path("code"));
    }

    @Test void consignmentMarkedObsintReachesTheConsoleUntilUnmarked() {
        String instruction = "Todo el personal usa casco y chaleco " + UUID.randomUUID();
        Map<String, String> source = activeObsintConsignment(instruction);
        String code = source.get("code");
        as("coord").get("/api/obsint/config/points/" + POINT + "/consignments").then().statusCode(200)
            .body("code", hasItem(code)).body("find { it.code == '" + code + "' }.instruction", is(instruction));

        String cam = camera("Cámara vínculo Consignas");
        String draft = as("coord").post("/api/obsint/config/points/" + POINT + "/protocols/draft").then().statusCode(200).extract().path("id");
        Map<String, Object> link = new HashMap<>(consigna("Nombre ignorado", List.of(cam), ALL_DAY));
        link.put("consignmentCode", code);
        var created = as("coord").contentType(ContentType.JSON).body(link)
            .post("/api/obsint/config/protocols/" + draft + "/consignas").then().statusCode(200)
            .body("consignmentCode", is(code)).body("title", is("Uso de casco")).body("instruction", is(instruction)).extract();
        String taskCode = created.path("code");
        as("coord").contentType(ContentType.JSON).body(link)
            .post("/api/obsint/config/protocols/" + draft + "/consignas").then().statusCode(400).body("message", containsString("ya está vinculada"));
        Map<String, Object> unknown = new HashMap<>(link);
        unknown.put("consignmentCode", "CON-NO-EXISTE");
        as("coord").contentType(ContentType.JSON).body(unknown)
            .post("/api/obsint/config/protocols/" + draft + "/consignas").then().statusCode(400);
        as("coord").post("/api/obsint/config/protocols/" + draft + "/publish").then().statusCode(200);
        as("coord").post("/api/obsint/config/protocols/" + draft + "/activate").then().statusCode(200);

        finishRunningCase("coord");
        String caseId = as("coord").post("/api/obsint/cases").then().statusCode(200).extract().path("id");
        assertEquals(instruction, taskInstruction(caseId, taskCode), "la tarea lleva la instrucción de Consignas");
        finishRunningCase("coord");

        // Nueva versión de Consignas sin OBSINT: la tarea deja de llegar a la Consola y la configuración lo advierte.
        String next = as("coord").post("/api/consignments/protocols/" + source.get("protocolId") + "/fork").then().statusCode(200).extract().path("id");
        String nextItem = as("coord").get("/api/consignments/protocols/" + source.get("protocolId") + "/history").then().statusCode(200)
            .extract().path("find { it.id == '" + next + "' }.consignments[0].id");
        as("coord").contentType(ContentType.JSON).body(Map.of("title", "Uso de casco", "instruction", instruction, "scopeType", "POINT", "obsint", false))
            .put("/api/consignments/items/" + nextItem).then().statusCode(200).body("obsint", is(false));
        as("coord").post("/api/consignments/protocols/" + next + "/publish").then().statusCode(200);
        as("coord").post("/api/consignments/protocols/" + next + "/activate").then().statusCode(200);
        as("coord").get("/api/obsint/config/points/" + POINT + "/consignments").then().statusCode(200).body("code", not(hasItem(code)));
        as("coord").get("/api/obsint/config/points/" + POINT + "/summary").then().statusCode(200)
            .body("status", is("warning")).body("warnings", hasItem(containsString(code)));
        var reopened = as("coord").post("/api/obsint/cases").then().extract();
        if (reopened.statusCode() == 200) assertNull(taskInstruction(reopened.path("id"), taskCode), "la tarea ya no entra en el caso");
        else assertEquals(409, reopened.statusCode());
        finishRunningCase("coord");

        // Se vuelve a marcar OBSINT: el resto de pruebas copia esta tarea en sus borradores y espera el resumen sin avisos.
        String restored = as("coord").post("/api/consignments/protocols/" + next + "/fork").then().statusCode(200).extract().path("id");
        String restoredItem = as("coord").get("/api/consignments/protocols/" + next + "/history").then().statusCode(200)
            .extract().path("find { it.id == '" + restored + "' }.consignments[0].id");
        as("coord").contentType(ContentType.JSON).body(Map.of("title", "Uso de casco", "instruction", instruction, "scopeType", "POSTS", "postIds", List.of(), "obsint", true))
            .put("/api/consignments/items/" + restoredItem).then().statusCode(200);
        as("coord").post("/api/consignments/protocols/" + restored + "/publish").then().statusCode(200);
        as("coord").post("/api/consignments/protocols/" + restored + "/activate").then().statusCode(200);
        as("coord").get("/api/obsint/config/points/" + POINT + "/summary").then().statusCode(200).body("warnings", not(hasItem(containsString(code))));
    }

    /** Instrucción de la tarea del caso que viene de esa tarea OBSINT, o null si no está. */
    static String taskInstruction(String caseId, String taskCode) {
        List<String> ids = as("coord").get("/api/obsint/cases/" + caseId).then().statusCode(200).extract().path("tasks.id");
        for (String id : ids) {
            var task = as("coord").get("/api/obsint/tasks/" + id).then().statusCode(200).extract();
            if (taskCode.equals(task.path("consignaCode"))) return task.path("instruction");
        }
        return null;
    }
}
