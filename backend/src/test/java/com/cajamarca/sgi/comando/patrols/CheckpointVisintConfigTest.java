package com.cajamarca.sgi.comando.patrols;

import com.cajamarca.sgi.comando.operator.OperatorFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class CheckpointVisintConfigTest {
    Map<String,Object> body(Boolean visint, boolean requiresEvidence) {
        Map<String,Object> b = new HashMap<>(Map.of("name", "Portón prueba", "description", "", "originMode", "FIELD", "latitude", -2.17, "longitude", -79.92,
            "gpsAccuracyM", 5.0, "controlType", "FOTOGRAFIA", "requiresEvidence", requiresEvidence, "standardImageNotes", ""));
        b.put("visintEnabled", visint);
        return b;
    }
    String cp() { return (String) OperatorFixtures.createDraftPatrolWithCheckpoint(-2.17, -79.92).get("checkpointId"); }

    /** VISINT viene activado por defecto en los Hitos que requieren evidencia; se puede desactivar. */
    @Test void onByDefaultAndCanBeToggled() {
        String cp = cp();
        as("coord").contentType(ContentType.JSON).body(body(null, true)).put("/api/patrols/checkpoints/" + cp).then().statusCode(200).body("visintEnabled", is(true));
        as("coord").contentType(ContentType.JSON).body(body(false, true)).put("/api/patrols/checkpoints/" + cp).then().statusCode(200).body("visintEnabled", is(false));
        as("coord").contentType(ContentType.JSON).body(body(null, true)).put("/api/patrols/checkpoints/" + cp).then().statusCode(200).body("visintEnabled", is(false));
        as("coord").contentType(ContentType.JSON).body(body(true, true)).put("/api/patrols/checkpoints/" + cp).then().statusCode(200).body("visintEnabled", is(true));
    }

    @Test void offByDefaultWhenCheckpointDoesNotRequireEvidence() {
        Map<String,Object> ids = OperatorFixtures.createDraftPatrolWithCheckpoint(-2.17, -79.92);
        String cp = as("coord").contentType(ContentType.JSON).body(Map.of("name", "Sin foto", "originMode", "FIELD", "latitude", -2.17, "longitude", -79.92,
            "controlType", "CONFIRMACION", "requiresEvidence", false)).post("/api/patrols/patrols/" + ids.get("patrolId") + "/checkpoints")
            .then().statusCode(200).body("visintEnabled", is(false)).extract().path("id");
        org.junit.jupiter.api.Assertions.assertNotNull(cp);
    }

    @Test void requiresPhotosToEnable() {
        as("coord").contentType(ContentType.JSON).body(body(true, false)).put("/api/patrols/checkpoints/" + cp()).then().statusCode(400);
    }

    @Test void uploadingStandardImageKeepsVisintEnabled() {
        String cp = cp();
        as("coord").contentType(ContentType.JSON).body(body(true, true)).put("/api/patrols/checkpoints/" + cp).then().statusCode(200);
        OperatorFixtures.uploadStandard(cp, Path.of("src/test/resources/fixtures/sample.jpg")).statusCode(200).body("visintEnabled", is(true));
    }

    @Test void publishRejectsVisintWithoutStandardImage() {
        Map<String,Object> ids = OperatorFixtures.createDraftPatrolWithCheckpoint(-2.17, -79.92);
        as("coord").contentType(ContentType.JSON).body(body(true, true)).put("/api/patrols/checkpoints/" + ids.get("checkpointId")).then().statusCode(200);
        as("coord").post("/api/patrols/protocols/" + ids.get("protocolId") + "/publish").then().statusCode(400);
    }
}
