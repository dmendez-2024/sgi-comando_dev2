package com.cajamarca.sgi.comando.patrols;

import com.cajamarca.sgi.comando.operator.OperatorFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Un Hito tiene de 0 a 5 fotos estándar; ya no hay mínimo/máximo de fotos del agente. */
@QuarkusTest
class CheckpointStandardImagesTest {
    @Inject EntityManager em;
    static final Path JPG = Path.of("src/test/resources/fixtures/sample.jpg"), JPG2 = Path.of("src/test/resources/fixtures/sample2.jpg"),
        PNG = Path.of("src/test/resources/fixtures/sample.png");

    String cp() { return (String) OperatorFixtures.createDraftPatrolWithCheckpoint(-2.17, -79.92).get("checkpointId"); }
    Map<String,Object> body(Boolean visint) {
        Map<String,Object> b = new HashMap<>(Map.of("name", "Portón prueba", "description", "", "originMode", "FIELD", "latitude", -2.17, "longitude", -79.92,
            "gpsAccuracyM", 5.0, "controlType", "FOTOGRAFIA", "requiresEvidence", true, "standardImageNotes", ""));
        b.put("visintEnabled", visint);
        return b;
    }

    @Test void upToFiveStandardImagesInOrder() {
        String cp = cp();
        List<Path> files = List.of(JPG, JPG2, PNG, JPG2, JPG);
        for (int i = 0; i < files.size(); i++)
            uploadStandard(cp, files.get(i)).statusCode(200).body("standardImages.size()", is(i + 1)).body("standardImages[" + i + "].position", is(i + 1))
                .body("hasStandardImage", is(true));
        uploadStandard(cp, JPG).statusCode(400);
    }

    @Test void eachImageIsReadable() throws Exception {
        String cp = cp();
        uploadStandard(cp, JPG).statusCode(200);
        String second = uploadStandard(cp, PNG).statusCode(200).extract().path("standardImages[1].id");
        byte[] back = as("coord").get("/api/patrols/checkpoints/" + cp + "/standard-images/" + second).then().statusCode(200).contentType("image/png").extract().asByteArray();
        assertArrayEquals(Files.readAllBytes(PNG), back);
    }

    @Test void deleteRenumbersAndRemovingTheLastDisablesVisint() {
        String cp = cp();
        String first = uploadStandard(cp, JPG).statusCode(200).extract().path("standardImages[0].id");
        String second = uploadStandard(cp, JPG2).statusCode(200).extract().path("standardImages[1].id");
        as("coord").contentType(ContentType.JSON).body(body(true)).put("/api/patrols/checkpoints/" + cp).then().statusCode(200).body("visintEnabled", is(true));
        as("coord").delete("/api/patrols/checkpoints/" + cp + "/standard-images/" + first).then().statusCode(200)
            .body("standardImages.size()", is(1)).body("standardImages[0].id", is(second)).body("standardImages[0].position", is(1)).body("visintEnabled", is(true));
        as("coord").delete("/api/patrols/checkpoints/" + cp + "/standard-images/" + second).then().statusCode(200)
            .body("standardImages.size()", is(0)).body("hasStandardImage", is(false)).body("visintEnabled", is(false));
    }

    @Test void minAndMaxPhotoCountsAreGone() {
        as("coord").contentType(ContentType.JSON).body(body(null)).put("/api/patrols/checkpoints/" + cp()).then().statusCode(200)
            .body("$", not(hasKey("evidenceMinCount"))).body("$", not(hasKey("evidenceMaxCount")));
    }

    @Test void newVersionKeepsTheStandardImages() {
        Map<String,Object> ids = OperatorFixtures.createDraftPatrolWithCheckpoint(-2.17, -79.92);
        uploadStandard(ids.get("checkpointId"), JPG).statusCode(200);
        uploadStandard(ids.get("checkpointId"), JPG2).statusCode(200);
        as("coord").post("/api/patrols/protocols/" + ids.get("protocolId") + "/publish").then().statusCode(200);
        String fork = as("coord").post("/api/patrols/protocols/" + ids.get("protocolId") + "/fork").then().statusCode(200).extract().path("id");
        Number n = (Number) em.createNativeQuery("""
            select count(*) from patrol_checkpoint_standard_image s join patrol_checkpoint c on c.id=s.checkpoint_id
            join patrol_definition d on d.id=c.patrol_definition_id where d.protocol_id=:p""").setParameter("p", UUID.fromString(fork)).getSingleResult();
        assertEquals(2, n.intValue());
    }
}
