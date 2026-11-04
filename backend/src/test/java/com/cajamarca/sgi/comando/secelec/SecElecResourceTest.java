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

    @Test void cameraChannelCoordinatesAndStreaming() {
        String id = as("coord").contentType(ContentType.JSON).body(camera()).post("/api/secelec/points/" + POINT + "/components")
            .then().statusCode(200).body("channel", is(1)).body("streamingEnabled", is(true)).body("streamingMode", is("COPY"))
            .body("latitude", nullValue()).extract().path("id");

        Map<String, Object> edit = camera();
        edit.put("password", "");
        edit.put("channel", 3);
        edit.put("latitude", -0.113972);
        edit.put("longitude", -78.4725);
        edit.put("streamingEnabled", false);
        edit.put("streamingMode", "TRANSCODE");
        as("coord").contentType(ContentType.JSON).body(edit).put("/api/secelec/components/" + id)
            .then().statusCode(200).body("channel", is(3)).body("latitude", is(-0.113972f)).body("longitude", is(-78.4725f))
            .body("streamingEnabled", is(false)).body("streamingMode", is("TRANSCODE"));

        Map<String, Object> badChannel = camera();
        badChannel.put("channel", 0);
        as("coord").contentType(ContentType.JSON).body(badChannel).post("/api/secelec/points/" + POINT + "/components")
            .then().statusCode(400).body("message", containsString("canal"));
        Map<String, Object> onlyLatitude = camera();
        onlyLatitude.put("latitude", -0.11);
        as("coord").contentType(ContentType.JSON).body(onlyLatitude).post("/api/secelec/points/" + POINT + "/components")
            .then().statusCode(400).body("message", containsString("latitud y longitud"));
        Map<String, Object> badLatitude = camera();
        badLatitude.put("latitude", 91);
        badLatitude.put("longitude", 0);
        as("coord").contentType(ContentType.JSON).body(badLatitude).post("/api/secelec/points/" + POINT + "/components")
            .then().statusCode(400).body("message", containsString("latitud"));
    }

    @Test void otherFamiliesHaveNoConnectionData() {
        Map<String, Object> sensor = camera();
        sensor.put("family", "INTRUSION");
        sensor.put("componentType", "Sensor de movimiento");
        as("coord").contentType(ContentType.JSON).body(sensor).post("/api/secelec/points/" + POINT + "/components").then().statusCode(200)
            .body("code", startsWith("INT-")).body("ip", nullValue()).body("hasPassword", is(false))
            .body("channel", nullValue()).body("streamingMode", nullValue());
        as("coord").get("/api/secelec/points/" + POINT + "/summary").then().statusCode(200)
            .body("total", greaterThan(0)).body("status", anyOf(is("complete"), is("warning")));
    }

    @Test void testCameraConnection() throws Exception {
        try (FakeRtspCamera cam = new FakeRtspCamera("comando_view", "secreta", 4, "H264")) {
            Map<String, Object> form = new HashMap<>(Map.of("ip", "127.0.0.1", "port", cam.port(), "channel", 2, "username", "comando_view", "password", "secreta"));
            as("coord").contentType(ContentType.JSON).body(form).post("/api/secelec/points/" + POINT + "/cameras/test")
                .then().statusCode(200).body("ok", is(true)).body("outcome", is("OK")).body("codec", is("H.264"))
                .body("streamPath", is("/Streaming/Channels/201"));

            form.put("password", "otra");
            as("coord").contentType(ContentType.JSON).body(form).post("/api/secelec/points/" + POINT + "/cameras/test")
                .then().statusCode(200).body("ok", is(false)).body("outcome", is("AUTH_FAILED"));

            // Al editar, contraseña vacía + componentId usa la contraseña guardada (cifrada) de la cámara.
            Map<String, Object> saved = camera();
            saved.put("ip", "127.0.0.1");
            saved.put("port", cam.port());
            String id = as("coord").contentType(ContentType.JSON).body(saved).post("/api/secelec/points/" + POINT + "/components")
                .then().statusCode(200).extract().path("id");
            form.put("password", "");
            form.put("componentId", id);
            as("coord").contentType(ContentType.JSON).body(form).post("/api/secelec/points/" + POINT + "/cameras/test")
                .then().statusCode(200).body("ok", is(true));

            form.remove("componentId");
            as("coord").contentType(ContentType.JSON).body(form).post("/api/secelec/points/" + POINT + "/cameras/test")
                .then().statusCode(400).body("message", containsString("contraseña"));
            as("cliente").contentType(ContentType.JSON).body(form).post("/api/secelec/points/" + POINT + "/cameras/test").then().statusCode(403);
        }
    }

    @Test void clientHasNoAccess() {
        as("cliente").get("/api/secelec/points/" + POINT + "/components").then().statusCode(403);
    }
}
