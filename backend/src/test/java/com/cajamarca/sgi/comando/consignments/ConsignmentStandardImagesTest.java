package com.cajamarca.sgi.comando.consignments;

import com.cajamarca.sgi.comando.operator.TaskFixtures;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static com.cajamarca.sgi.comando.operator.TaskFixtures.uploadConsignmentStandard;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Fase 4: cada evidencia de Consigna tiene de 0 a 5 fotos estándar; las de tipo Foto se validan con VISINT. */
@QuarkusTest
class ConsignmentStandardImagesTest {
    static final Path JPG = Path.of("src/test/resources/fixtures/sample.jpg"), JPG2 = Path.of("src/test/resources/fixtures/sample2.jpg"),
        PNG = Path.of("src/test/resources/fixtures/sample.png");

    @Test void photoEvidenceHasVisintAndUpToFiveStandards() {
        Map<String,Object> ids = TaskFixtures.draftConsignmentWithPhoto();
        List<Path> files = List.of(JPG, JPG2, PNG, JPG2, JPG);
        for (int i = 0; i < files.size(); i++)
            uploadConsignmentStandard(ids.get("evidenceId"), files.get(i)).statusCode(200)
                .body("standardImages.size()", is(i + 1)).body("standardImages[" + i + "].position", is(i + 1))
                .body("hasStandardImage", is(true)).body("visintEnabled", is(true));
        uploadConsignmentStandard(ids.get("evidenceId"), JPG).statusCode(400);
    }

    @Test void readAndDeleteRenumbers() throws Exception {
        Map<String,Object> ids = TaskFixtures.draftConsignmentWithPhoto();
        String first = uploadConsignmentStandard(ids.get("evidenceId"), JPG).statusCode(200).extract().path("standardImages[0].id");
        String second = uploadConsignmentStandard(ids.get("evidenceId"), PNG).statusCode(200).extract().path("standardImages[1].id");
        assertArrayEquals(Files.readAllBytes(PNG), as("coord").get("/api/consignments/evidences/" + ids.get("evidenceId") + "/standard-images/" + second)
            .then().statusCode(200).contentType("image/png").extract().asByteArray());
        as("coord").delete("/api/consignments/evidences/" + ids.get("evidenceId") + "/standard-images/" + first).then().statusCode(200)
            .body("standardImages.size()", is(1)).body("standardImages[0].id", is(second)).body("standardImages[0].position", is(1));
    }

    @Test void nonPhotoEvidenceHasNoVisint() {
        Map<String,Object> ids = TaskFixtures.draftConsignmentWithPhoto();
        as("coord").contentType("application/json").body(Map.of("name", "Confirmar", "description", "", "evidenceType", "CONFIRMATION", "required", true, "standardImageNotes", ""))
            .put("/api/consignments/evidences/" + ids.get("evidenceId")).then().statusCode(200).body("visintEnabled", is(false));
    }

    @Test void publishRequiresAStandardForPhotoEvidence() {
        Map<String,Object> ids = TaskFixtures.draftConsignmentWithPhoto();
        as("coord").post("/api/consignments/protocols/" + ids.get("protocolId") + "/publish").then().statusCode(400);
        uploadConsignmentStandard(ids.get("evidenceId"), JPG).statusCode(200);
        as("coord").post("/api/consignments/protocols/" + ids.get("protocolId") + "/publish").then().statusCode(200);
    }

    @Test void newVersionKeepsTheStandards() {
        Map<String,Object> ids = TaskFixtures.draftConsignmentWithPhoto();
        uploadConsignmentStandard(ids.get("evidenceId"), JPG).statusCode(200);
        uploadConsignmentStandard(ids.get("evidenceId"), JPG2).statusCode(200);
        as("coord").post("/api/consignments/protocols/" + ids.get("protocolId") + "/publish").then().statusCode(200);
        as("coord").post("/api/consignments/protocols/" + ids.get("protocolId") + "/fork").then().statusCode(200)
            .body("consignments[0].evidences[0].standardImages.size()", is(2)).body("consignments[0].evidences[0].visintEnabled", is(true));
    }
}
