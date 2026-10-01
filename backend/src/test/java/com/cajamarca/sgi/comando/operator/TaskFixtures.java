package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.storage.Digests;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import io.restassured.http.ContentType;
import jakarta.persistence.EntityManager;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.*;
import static org.hamcrest.Matchers.is;

/** Fase 4: Consignas y Bitácora creadas por la API y fotos del agente enviadas con TASK_EVIDENCE_SUBMITTED. */
public final class TaskFixtures {
    private TaskFixtures() {}
    static final ObjectMapper M = new ObjectMapper();
    public static final String POINT = "40000000-0000-0000-0000-000000000001";
    public static final Path SAMPLE = Path.of("src/test/resources/fixtures/sample.jpg");

    public static io.restassured.response.ValidatableResponse uploadConsignmentStandard(Object evidenceId, Path file) {
        return as("coord").multiPart("file", file.toFile(), "image/jpeg").post("/api/consignments/evidences/" + evidenceId + "/standard-images").then();
    }
    public static io.restassured.response.ValidatableResponse uploadLogbookStandard(Object fieldId, Path file) {
        return as("coord").multiPart("file", file.toFile(), "image/jpeg").post("/api/bitacora/fields/" + fieldId + "/standard-images").then();
    }

    /** Protocolo de Consignas en borrador con una consigna (alcance: todo el Punto) y una evidencia tipo Foto. */
    public static Map<String,Object> draftConsignmentWithPhoto() {
        String protocolId = as("coord").contentType(ContentType.JSON).body(Map.of("pointId", POINT, "name", "Consignas prueba " + UUID.randomUUID()))
            .post("/api/consignments/protocols").then().statusCode(200).extract().path("id");
        String itemId = as("coord").contentType(ContentType.JSON).body(Map.of("title", "Cortina cerrada"))
            .post("/api/consignments/protocols/" + protocolId + "/items").then().statusCode(200).extract().path("id");
        String evidenceId = as("coord").contentType(ContentType.JSON).body(Map.of("name", "Foto de la cortina", "evidenceType", "PHOTO", "required", true))
            .post("/api/consignments/items/" + itemId + "/evidences").then().statusCode(200).extract().path("id");
        as("coord").contentType(ContentType.JSON).body(Map.of("title", "Cortina cerrada", "instruction", "Verificar la cortina del local cerrada al iniciar el turno.",
            "scopeType", "POINT", "evidenceRequired", true)).put("/api/consignments/items/" + itemId).then().statusCode(200);
        return Map.of("protocolId", protocolId, "consignmentId", itemId, "evidenceId", evidenceId);
    }

    /** Consigna vigente (protocolo publicado y activo) con estas fotos estándar en su evidencia tipo Foto. */
    public static Map<String,Object> activeConsignment(List<Path> standards) {
        Map<String,Object> ids = draftConsignmentWithPhoto();
        for (Path p : standards) uploadConsignmentStandard(ids.get("evidenceId"), p).statusCode(200);
        as("coord").post("/api/consignments/protocols/" + ids.get("protocolId") + "/publish").then().statusCode(200);
        as("coord").post("/api/consignments/protocols/" + ids.get("protocolId") + "/activate").then().statusCode(200).body("status", is("ACTIVO"));
        return ids;
    }

    /** Protocolo de Bitácora PAX en borrador para GGTT01 (trae Cédula, Pasaporte, Credencial y Rostro). Devuelve protocolo y campos por nombre. */
    public static Map<String,Object> draftLogbook() {
        var dto = as("coord").contentType(ContentType.JSON).body(Map.of("postId", POST_GGTT01, "name", "Bitácora prueba " + UUID.randomUUID(), "objectType", "PAX", "applicationType", "INGRESO"))
            .post("/api/bitacora/protocols").then().statusCode(200).extract();
        Map<String,Object> ids = new HashMap<>(Map.of("protocolId", dto.path("id")));
        List<Map<String,Object>> fields = dto.path("accreditations[0].fields");
        for (Map<String,Object> f : fields) ids.put((String) f.get("name"), f.get("id"));
        return ids;
    }

    /** Bitácora activa en GGTT01 con estas fotos estándar en Cédula, Pasaporte y Credencial (los campos con VISINT). */
    public static Map<String,Object> activeLogbook(List<Path> standards) {
        Map<String,Object> ids = draftLogbook();
        for (String field : List.of("Cédula", "Pasaporte", "Credencial")) for (Path p : standards) uploadLogbookStandard(ids.get(field), p).statusCode(200);
        as("coord").post("/api/bitacora/protocols/" + ids.get("protocolId") + "/publish").then().statusCode(200).body("status", is("ACTIVO"));
        return ids;
    }

    /** Sube la foto del agente y envía TASK_EVIDENCE_SUBMITTED; groupId = entryId de Bitácora (null en Consignas). */
    public static VisintFixtures.Submitted submitTask(EntityManager em, String targetType, Object targetId, Path photo, UUID groupId) throws Exception {
        UUID assignment = ensureAgentAssignment(em);
        String employee = as("agente").queryParam("assignmentId", assignment).get("/api/v1/operator/runtime").then().statusCode(200).extract().path("employeeId");
        UUID event = UUID.randomUUID(), cid = UUID.randomUUID();
        ObjectNode meta = M.createObjectNode().put("uploadBatchId", UUID.randomUUID().toString()).put("eventId", event.toString())
            .put("assignmentId", assignment.toString()).put("targetType", targetType).put("targetId", targetId.toString());
        meta.putArray("items").addObject().put("clientEvidenceId", cid.toString()).put("capturedAt", Instant.now().toString())
            .put("source", "CAMERA").put("sha256", Digests.sha256Hex(Files.readAllBytes(photo)));
        String evidenceId = as("agente").multiPart("metadata", meta.toString()).multiPart("files", cid + ".jpg", Files.readAllBytes(photo), "image/jpeg")
            .post("/api/v1/operator/evidences").then().statusCode(200).extract().path("results[0].evidenceId");
        ObjectNode b = M.createObjectNode().put("batchId", UUID.randomUUID().toString()).put("correlationId", UUID.randomUUID().toString())
            .put("employeeId", employee).put("instanceCountryId", "11111111-1111-1111-1111-111111111111").put("deviceId", "test").put("capturedAt", Instant.now().toString());
        ObjectNode e = b.putArray("events").addObject().put("type", "TASK_EVIDENCE_SUBMITTED").put("eventId", event.toString()).put("assignmentId", assignment.toString())
            .put("targetType", targetType).put("targetId", targetId.toString()).put("executedAt", Instant.now().toString());
        if (groupId != null) e.put("groupId", groupId.toString());
        e.putArray("evidenceIds").add(evidenceId);
        var res = as("agente").contentType(ContentType.JSON).body(b.toString()).post("/api/v1/operator/executions").then().extract();
        return new VisintFixtures.Submitted(event, res.statusCode(), res.asString());
    }
}
