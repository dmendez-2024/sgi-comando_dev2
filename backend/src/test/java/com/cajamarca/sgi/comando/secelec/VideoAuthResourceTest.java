package com.cajamarca.sgi.comando.secelec;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Permiso de video: MediaMTX pregunta a Comando y solo se entrega el video con una URL firmada, de esa cámara y vigente. */
@QuarkusTest
class VideoAuthResourceTest {
    static final String CAM = "live/cam-11111111-1111-1111-1111-111111111111";
    @Inject VideoToken tokens;
    @Inject VideoGateway video;

    static int ask(Map<String, Object> body) {
        return RestAssured.given().contentType(ContentType.JSON).body(body).post("/api/internal/video/auth").statusCode();
    }

    static Map<String, Object> read(String path, String query) {
        Map<String, Object> b = new HashMap<>(Map.of("action", "read", "path", path, "protocol", "hls", "ip", "172.18.0.1", "user", "", "password", ""));
        if (query != null) b.put("query", query);
        return b;
    }

    @Test void signedUrlOpensOnlyItsCamera() {
        String token = tokens.sign(CAM);
        assertEquals(200, ask(read(CAM, "token=" + token)), "URL firmada por Comando");
        assertEquals(200, ask(read(CAM, "a=1&token=" + token)));
        assertEquals(401, ask(read(CAM, null)), "sin token");
        assertEquals(401, ask(read("live/cam-otra", "token=" + token)), "token de otra cámara");
        assertEquals(401, ask(read(CAM, "token=" + token.substring(0, token.length() - 2) + "xx")), "firma alterada");
        assertEquals(401, ask(read(CAM, "token=" + tokens.sign(CAM, Instant.now().minusSeconds(5)))), "vencido");

        Map<String, Object> bearer = read(CAM, null);
        bearer.put("token", token);
        assertEquals(200, ask(bearer), "MediaMTX también puede mandarlo en el campo token");
    }

    @Test void nothingElseIsAllowed() {
        String token = tokens.sign(CAM);
        Map<String, Object> publish = read(CAM, "token=" + token);
        publish.put("action", "publish");
        assertEquals(401, ask(publish), "nadie publica video");
        assertEquals(401, ask(read("Streaming/Channels/101", "token=" + tokens.sign("Streaming/Channels/101"))), "solo rutas live/");
        assertEquals(401, ask(new HashMap<>(Map.of("action", "api", "user", "x", "password", "y"))), "API sin la clave de Comando");
    }

    /** El diagnóstico sin sesión no existe mientras no se encienda (SGI_VIDEO_DIAGNOSTIC_ENABLED). */
    @Test void diagnosticIsOffByDefault() {
        RestAssured.given().get("/api/internal/video/diagnostic").then().statusCode(404);
    }

    /** Sin servidor de video lo dice sin fallar. */
    @Test void diagnoseWithoutServer() {
        VideoGateway.Diagnostic d = video.diagnose();
        assertFalse(d.configured());
        assertFalse(d.ok());
        assertTrue(d.summary().contains("SGI_VIDEO_MEDIAMTX"), d.summary());
    }

    /** La prueba de cámara sin sesión tampoco existe con el diagnóstico apagado. */
    @Test void cameraDiagnosticIsOffByDefault() {
        RestAssured.given().contentType(ContentType.JSON).body(Map.of("ip", "127.0.0.1", "username", "admin", "password", "x"))
            .post("/api/internal/video/diagnostic/camera").then().statusCode(404);
    }

    /** Cámara real sin Punto: dice qué paso falla (RTSP, códec o falta de servidor de video) sin guardar nada. */
    @Test void cameraDiagnoseStepByStep() throws Exception {
        String path = CameraBrand.HIKVISION.path(1, null);
        try (FakeRtspCamera cam = new FakeRtspCamera("admin", "Clave123", 1, "H264")) {
            VideoGateway.CameraDiagnostic d = video.diagnoseCamera("127.0.0.1", cam.port(), path, "admin", "Clave123");
            assertFalse(d.ok());
            assertEquals(2, d.steps().size());
            assertTrue(d.steps().stream().allMatch(VideoGateway.Step::ok), d.steps().toString());
            assertTrue(d.summary().contains("no hay servidor de video"), d.summary());
            assertNull(d.videoUrl());

            VideoGateway.CameraDiagnostic wrong = video.diagnoseCamera("127.0.0.1", cam.port(), path, "admin", "otra");
            assertFalse(wrong.ok());
            assertEquals(1, wrong.steps().size());
            assertFalse(wrong.steps().get(0).ok());
        }
        try (FakeRtspCamera cam = new FakeRtspCamera("admin", "Clave123", 1, "H265")) {
            VideoGateway.CameraDiagnostic h265 = video.diagnoseCamera("127.0.0.1", cam.port(), path, "admin", "Clave123");
            assertFalse(h265.steps().get(1).ok(), "H.265 no se ve en el navegador");
        }
    }

    @Test void queryToken() {
        assertEquals("1.abc", VideoAuthResource.queryToken("x=2&token=1.abc"));
        assertNull(VideoAuthResource.queryToken("x=2"));
        assertNull(VideoAuthResource.queryToken(null));
    }
}
