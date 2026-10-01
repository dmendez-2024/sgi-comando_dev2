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

/** Datos de prueba de VISINT: Hito con VISINT activado, hasta 5 fotos estándar y ejecuciones con la foto del agente. */
public final class VisintFixtures {
    private VisintFixtures() {}
    static final ObjectMapper M = new ObjectMapper();
    public static final Path SAMPLE = Path.of("src/test/resources/fixtures/sample.jpg");

    /** Protocolo publicado en GGTT01 con VISINT activado y una foto estándar (sample.jpg). */
    public static Map<String,Object> publishVisintPatrol() { return publishVisintPatrol(List.of(SAMPLE)); }

    /** Protocolo publicado en GGTT01 con VISINT activado y estas fotos estándar, en orden. */
    public static Map<String,Object> publishVisintPatrol(List<Path> standards) {
        Map<String,Object> ids = createDraftPatrolWithCheckpoint(-2.17, -79.92);
        for (Path p : standards) uploadStandard(ids.get("checkpointId"), p).statusCode(200);
        Map<String,Object> save = new HashMap<>(Map.of("name", "Portón prueba", "description", "", "originMode", "FIELD", "latitude", -2.17, "longitude", -79.92,
            "gpsAccuracyM", 5.0, "controlType", "FOTOGRAFIA", "requiresEvidence", true, "standardImageNotes", "", "visintEnabled", true));
        as("coord").contentType(ContentType.JSON).body(save).put("/api/patrols/checkpoints/" + ids.get("checkpointId")).then().statusCode(200);
        as("coord").post("/api/patrols/protocols/" + ids.get("protocolId") + "/publish").then().statusCode(200).body("status", is("ACTIVO"));
        return ids;
    }

    /** Sube la foto del agente por FormData y confirma el Hito; devuelve el eventId (= id de la ejecución). */
    public static UUID executeWithPhoto(EntityManager em, Map<String,Object> ids, Path photo) throws Exception {
        Submitted s = submitWithPhoto(em, ids, photo, UUID.randomUUID());
        if (s.status() != 200) throw new AssertionError("Confirmación del Hito: HTTP " + s.status() + " " + s.body());
        return s.eventId();
    }

    /** Resultado de confirmar un Hito: eventId, código HTTP y cuerpo. */
    public record Submitted(UUID eventId, int status, String body) {}

    /** Sube la foto y confirma el Hito dentro de la ronda indicada (para probar nuevas capturas en la misma ronda). */
    public static Submitted submitWithPhoto(EntityManager em, Map<String,Object> ids, Path photo, UUID runId) throws Exception {
        UUID assignment = ensureAgentAssignment(em);
        String employee = as("agente").queryParam("assignmentId", assignment).get("/api/v1/operator/runtime").then().statusCode(200).extract().path("employeeId");
        UUID event = UUID.randomUUID(), cid = UUID.randomUUID();
        ObjectNode meta = M.createObjectNode().put("uploadBatchId", UUID.randomUUID().toString()).put("eventId", event.toString())
            .put("assignmentId", assignment.toString()).put("targetType", "PATROL_CHECKPOINT").put("targetId", ids.get("checkpointId").toString());
        meta.putArray("items").addObject().put("clientEvidenceId", cid.toString()).put("capturedAt", Instant.now().toString()).put("latitude", -2.17).put("longitude", -79.92)
            .put("source", "CAMERA").put("sha256", Digests.sha256Hex(Files.readAllBytes(photo)));
        String evidenceId = as("agente").multiPart("metadata", meta.toString()).multiPart("files", cid + ".jpg", Files.readAllBytes(photo), "image/jpeg")
            .post("/api/v1/operator/evidences").then().statusCode(200).extract().path("results[0].evidenceId");
        ObjectNode b = M.createObjectNode().put("batchId", UUID.randomUUID().toString()).put("correlationId", UUID.randomUUID().toString())
            .put("employeeId", employee).put("instanceCountryId", "11111111-1111-1111-1111-111111111111").put("deviceId", "test").put("capturedAt", Instant.now().toString());
        ObjectNode e = b.putArray("events").addObject().put("type", "PATROL_CHECKPOINT_COMPLETED").put("eventId", event.toString()).put("assignmentId", assignment.toString())
            .put("patrolRunId", runId.toString()).put("patrolId", ids.get("patrolId").toString()).put("checkpointId", ids.get("checkpointId").toString())
            .put("executedAt", Instant.now().toString());
        e.putArray("evidenceIds").add(evidenceId);
        var res = as("agente").contentType(ContentType.JSON).body(b.toString()).post("/api/v1/operator/executions").then().extract();
        return new Submitted(event, res.statusCode(), res.asString());
    }
}
