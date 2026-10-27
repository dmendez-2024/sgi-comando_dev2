package com.cajamarca.sgi.comando.secelec;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static org.hamcrest.Matchers.*;

/** Componentes de Seguridad del Punto: alta por familia, credenciales de cámara que nunca se devuelven y resumen. */
@QuarkusTest
class SecElecResourceTest {
    static final String POINT = "40000000-0000-0000-0000-000000000001";

    static Map<String, Object> camera() {
        Map<String, Object> body = new HashMap<>(Map.of("family", "VIDEO", "componentType", "Cámara Bala", "name", "Cámara Bala Acceso Norte",
            "location", "Acceso Norte", "brandModel", "Hikvision DS-2CD2043G2-I", "ip", "192.168.1.10", "port", 554, "username", "comando_view"));
        body.put("password", "secreta");
        return body;
    }

    @Test void cameraKeepsCredentialsPrivate() {
        Map<String, Object> noPassword = camera();
        noPassword.remove("password");
        as("coord").contentType(ContentType.JSON).body(noPassword).post("/api/secelec/points/" + POINT + "/components")
            .then().statusCode(400).body("message", containsString("contraseña"));

        String id = as("coord").contentType(ContentType.JSON).body(camera()).post("/api/secelec/points/" + POINT + "/components")
            .then().statusCode(200).body("code", startsWith("CCTV-")).body("hasPassword", is(true)).body("status", is("ACTIVE"))
            .body("$", not(hasKey("password"))).body("$", not(hasKey("passwordCipher"))).extract().path("id");

        Map<String, Object> edit = camera();
        edit.put("password", "");
        edit.put("name", "Cámara renombrada");
        as("coord").contentType(ContentType.JSON).body(edit).put("/api/secelec/components/" + id)
            .then().statusCode(200).body("name", is("Cámara renombrada")).body("hasPassword", is(true));
        String list = as("coord").get("/api/secelec/points/" + POINT + "/components").then().statusCode(200)
            .body("find{it.id=='" + id + "'}.ip", is("192.168.1.10")).extract().asString();
        org.junit.jupiter.api.Assertions.assertFalse(list.contains("secreta"), "la contraseña nunca viaja en la respuesta");
    }

    @Test void validatesConnectionFields() {
        Map<String, Object> badPort = camera();
        badPort.put("port", 70000);
        as("coord").contentType(ContentType.JSON).body(badPort).post("/api/secelec/points/" + POINT + "/components").then().statusCode(400);
        Map<String, Object> badUrl = camera();
        badUrl.put("streamUrl", "javascript:alert(1)");
        as("coord").contentType(ContentType.JSON).body(badUrl).post("/api/secelec/points/" + POINT + "/components").then().statusCode(400);
    }

    @Test void otherFamiliesHaveNoConnectionData() {
        Map<String, Object> sensor = camera();
        sensor.put("family", "INTRUSION");
        sensor.put("componentType", "Sensor de movimiento");
        as("coord").contentType(ContentType.JSON).body(sensor).post("/api/secelec/points/" + POINT + "/components").then().statusCode(200)
            .body("code", startsWith("INT-")).body("ip", nullValue()).body("hasPassword", is(false));
        as("coord").get("/api/secelec/points/" + POINT + "/summary").then().statusCode(200)
            .body("total", greaterThan(0)).body("status", anyOf(is("complete"), is("warning")));
    }

    @Test void clientHasNoAccess() {
        as("cliente").get("/api/secelec/points/" + POINT + "/components").then().statusCode(403);
    }
}
