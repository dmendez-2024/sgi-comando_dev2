package com.cajamarca.sgi.comando.patrols;

import com.cajamarca.sgi.comando.operator.OperatorFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class CheckpointEvidenceCountTest {
    Map<String,Object> body(Integer min, Integer max) {
        Map<String,Object> b = new HashMap<>(Map.of("name", "Portón prueba", "description", "", "originMode", "FIELD", "latitude", -2.17, "longitude", -79.92,
            "gpsAccuracyM", 5.0, "controlType", "FOTOGRAFIA", "requiresEvidence", true, "standardImageNotes", ""));
        b.put("evidenceMinCount", min); b.put("evidenceMaxCount", max);
        return b;
    }

    @Test void defaultsAndSave() {
        String cp = (String) OperatorFixtures.createDraftPatrolWithCheckpoint(-2.17, -79.92).get("checkpointId");
        as("coord").contentType(ContentType.JSON).body(body(null, null)).put("/api/patrols/checkpoints/" + cp)
            .then().statusCode(200).body("evidenceMinCount", is(1)).body("evidenceMaxCount", is(5));
        as("coord").contentType(ContentType.JSON).body(body(2, 3)).put("/api/patrols/checkpoints/" + cp)
            .then().statusCode(200).body("evidenceMinCount", is(2)).body("evidenceMaxCount", is(3));
    }

    @Test void rejectsInvalidRanges() {
        String cp = (String) OperatorFixtures.createDraftPatrolWithCheckpoint(-2.17, -79.92).get("checkpointId");
        as("coord").contentType(ContentType.JSON).body(body(3, 2)).put("/api/patrols/checkpoints/" + cp).then().statusCode(400);
        as("coord").contentType(ContentType.JSON).body(body(1, 6)).put("/api/patrols/checkpoints/" + cp).then().statusCode(400);
        as("coord").contentType(ContentType.JSON).body(body(0, 2)).put("/api/patrols/checkpoints/" + cp).then().statusCode(400);
    }
}
