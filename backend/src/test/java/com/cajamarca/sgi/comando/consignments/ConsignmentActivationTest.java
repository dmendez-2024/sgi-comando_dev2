package com.cajamarca.sgi.comando.consignments;

import com.cajamarca.sgi.comando.operator.TaskFixtures;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static org.hamcrest.Matchers.is;

/** Activar un protocolo de Consignas cuando el Punto ya tiene otro activo debe desactivar el anterior, no fallar (índice único de ACTIVO por Punto). */
@QuarkusTest
class ConsignmentActivationTest {
    @Test void activatingASecondProtocolReplacesTheActiveOne() {
        Map<String,Object> first = TaskFixtures.activeConsignment(List.of(Path.of("src/test/resources/fixtures/sample.jpg")));
        Map<String,Object> second = TaskFixtures.draftConsignmentWithPhoto();
        TaskFixtures.uploadConsignmentStandard(second.get("evidenceId"), Path.of("src/test/resources/fixtures/sample.jpg")).statusCode(200);
        as("coord").post("/api/consignments/protocols/" + second.get("protocolId") + "/publish").then().statusCode(200);
        as("coord").post("/api/consignments/protocols/" + second.get("protocolId") + "/activate").then().statusCode(200).body("status", is("ACTIVO"));
        as("coord").get("/api/consignments/protocols/" + first.get("protocolId") + "/history").then().statusCode(200).body("[0].status", is("INACTIVO"));
    }
}
