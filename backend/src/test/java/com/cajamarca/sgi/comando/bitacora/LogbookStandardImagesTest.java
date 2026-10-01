package com.cajamarca.sgi.comando.bitacora;

import com.cajamarca.sgi.comando.operator.TaskFixtures;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static com.cajamarca.sgi.comando.operator.TaskFixtures.uploadLogbookStandard;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Fase 4: los campos de Bitácora con evidencia tienen de 0 a 5 fotos estándar. VISINT valida los de tipo DOCUMENTO
 * (Cédula, Pasaporte, Credencial); Rostro solo se guarda (reconocimiento facial fuera de alcance).
 */
@QuarkusTest
class LogbookStandardImagesTest {
    static final Path JPG = Path.of("src/test/resources/fixtures/sample.jpg"), PNG = Path.of("src/test/resources/fixtures/sample.png");

    String field(Map<String,Object> ids, String name) { return (String) ids.get(name); }
    String fieldPath(String name) { return "accreditations[0].fields.find{it.name=='" + name + "'}"; }

    @Test void documentFieldsUseVisintButFaceDoesNot() {
        Map<String,Object> ids = TaskFixtures.draftLogbook();
        var p = as("coord").get("/api/bitacora/protocols/" + ids.get("protocolId") + "/history").then().statusCode(200);
        for (String doc : List.of("Cédula", "Pasaporte", "Credencial")) p.body("[0]." + fieldPath(doc) + ".visintEnabled", is(true));
        p.body("[0]." + fieldPath("Rostro") + ".visintEnabled", is(false));
    }

    @Test void upToFiveStandardsReadAndDelete() throws Exception {
        Map<String,Object> ids = TaskFixtures.draftLogbook();
        String cedula = field(ids, "Cédula");
        for (int i = 0; i < 5; i++) uploadLogbookStandard(cedula, i % 2 == 0 ? JPG : PNG).statusCode(200).body("standardImages.size()", is(i + 1));
        uploadLogbookStandard(cedula, JPG).statusCode(400);
        String second = as("coord").get("/api/bitacora/protocols/" + ids.get("protocolId") + "/history").then().statusCode(200)
            .extract().path("[0]." + fieldPath("Cédula") + ".standardImages[1].id");
        assertArrayEquals(Files.readAllBytes(PNG), as("coord").get("/api/bitacora/fields/" + cedula + "/standard-images/" + second).then().statusCode(200).extract().asByteArray());
        as("coord").delete("/api/bitacora/fields/" + cedula + "/standard-images/" + second).then().statusCode(200)
            .body("standardImages.size()", is(4)).body("standardImages.position", contains(1, 2, 3, 4));
    }

    @Test void publishRequiresStandardsOnlyForVisintFields() {
        Map<String,Object> ids = TaskFixtures.draftLogbook();
        as("coord").post("/api/bitacora/protocols/" + ids.get("protocolId") + "/publish").then().statusCode(400);
        for (String doc : List.of("Cédula", "Pasaporte", "Credencial")) uploadLogbookStandard(ids.get(doc), JPG).statusCode(200);
        // Rostro no necesita foto estándar.
        as("coord").post("/api/bitacora/protocols/" + ids.get("protocolId") + "/publish").then().statusCode(200).body("status", is("ACTIVO"));
    }

    @Test void newVersionKeepsTheStandards() {
        Map<String,Object> ids = TaskFixtures.activeLogbook(List.of(JPG, PNG));
        as("coord").post("/api/bitacora/protocols/" + ids.get("protocolId") + "/fork").then().statusCode(200)
            .body(fieldPath("Cédula") + ".standardImages.size()", is(2));
    }
}
