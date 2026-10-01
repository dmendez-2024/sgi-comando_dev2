package com.cajamarca.sgi.comando.visint;

import com.cajamarca.sgi.comando.storage.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class HttpVisintAdapterTest {
    final ObjectMapper m = new ObjectMapper();
    static final UUID EVIDENCE = UUID.fromString("99999999-9999-9999-9999-999999999999");
    static final UUID STD1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID STD2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    static final String PASS = """
        {"requestId":"prueba-007","status":"PASS","quality":{"valid":true,"score":0.8838},"match":{"compatible":true,"score":0.7635},
         "matchedReferenceId":"22222222-2222-2222-2222-222222222222","reasonCode":"OK","processedAt":"2026-09-30T14:49:47.724+00:00",
         "modelVersion":"visint-faces-1/buffalo_l-w600k_r50-v1"}""";
    static final String TIMEOUT = """
        {"requestId":"prueba-006","status":"TIMEOUT","quality":{"valid":false,"score":0.0},"match":{"compatible":false,"score":null},
         "matchedReferenceId":null,"reasonCode":"QUEUE_TIMEOUT","processedAt":"2026-09-29T21:36:48.843+00:00","modelVersion":"visint-faces-1/buffalo_l-w600k_r50-v1"}""";

    static String status(String status) { return "{\"requestId\":\"x\",\"status\":\"" + status + "\",\"matchedReferenceId\":null,\"reasonCode\":null}"; }

    @Test void passPointsToTheMatchedStandardImage() {
        var r = HttpVisintAdapter.parseResult(PASS, m);
        assertEquals("PASS", r.result());
        assertEquals("prueba-007", r.externalId());
        assertEquals(STD2, r.matchedStandardImageId());
        assertEquals("OK", r.reasonCode());
        assertEquals("visint-faces-1/buffalo_l-w600k_r50-v1", r.modelVersion());
    }

    @Test void timeoutAndProcessingAreRetried() {
        var e = assertThrows(VisintUnavailableException.class, () -> HttpVisintAdapter.parseResult(TIMEOUT, m));
        assertTrue(e.getMessage().contains("QUEUE_TIMEOUT"));
        assertThrows(VisintUnavailableException.class, () -> HttpVisintAdapter.parseResult(status("PROCESSING"), m));
    }

    /** Estados del plan de integración VISINT (lámina 9): FAIL_* es "no cumple"; ERROR_VISINT es un error técnico. */
    @Test void failStatusesAreFailWithTheirReason() {
        for (String s : List.of("FAIL_QUALITY", "FAIL_NO_MATCH", "FAIL_INVALID_IMAGE")) {
            var r = HttpVisintAdapter.parseResult(status(s), m);
            assertEquals("FAIL", r.result(), s);
            assertEquals(s, r.reasonCode(), "sin reasonCode se usa el status");
            assertNull(r.matchedStandardImageId());
        }
        var q = HttpVisintAdapter.parseResult("{\"requestId\":\"x\",\"status\":\"FAIL\",\"match\":{\"compatible\":false},\"reasonCode\":\"NO_MATCH\"}", m);
        assertEquals("FAIL", q.result());
        assertEquals("NO_MATCH", q.reasonCode());
    }

    @Test void visintErrorIsError() {
        var r = HttpVisintAdapter.parseResult(status("ERROR_VISINT"), m);
        assertEquals("ERROR", r.result());
        assertEquals("ERROR_VISINT", r.reasonCode());
    }

    @Test void invalidResponseIsRetried() {
        assertThrows(VisintUnavailableException.class, () -> HttpVisintAdapter.parseResult("{\"requestId\":\"x\"}", m));
        assertThrows(VisintUnavailableException.class, () -> HttpVisintAdapter.parseResult("no json", m));
    }

    @Test void notConfiguredIsUnavailable() {
        HttpVisintAdapter a = adapter("", new byte[0]);
        a.url = Optional.empty();
        var e = assertThrows(VisintUnavailableException.class, () -> a.review(request()));
        assertTrue(e.getMessage().contains("no configurado"));
    }

    @Test void storageFailureIsRetriedLikeAnOutage() {
        HttpVisintAdapter a = adapter("http://127.0.0.1:1/v1/evidence/validate", new byte[0]);
        a.storage = new StorageService() { @Override public byte[] read(String bucket, String key) { throw new jakarta.ws.rs.NotFoundException("Archivo no encontrado"); } };
        var e = assertThrows(VisintUnavailableException.class, () -> a.review(request()));
        assertTrue(e.getMessage().contains("foto"));
    }

    /** Llamada real por HTTP contra un VISINT falso local: en image la foto del agente; en referenceImages las fotos estándar. */
    @Test void sendsAgentPhotoAsImageAndStandardsAsReferences() throws Exception {
        AtomicReference<String> auth = new AtomicReference<>(), type = new AtomicReference<>(), body = new AtomicReference<>(), upgrade = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/evidence/validate", ex -> {
            auth.set(ex.getRequestHeaders().getFirst("X-API-Key"));
            upgrade.set(ex.getRequestHeaders().getFirst("Upgrade"));
            type.set(ex.getRequestHeaders().getFirst("Content-Type"));
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            byte[] out = PASS.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, out.length); ex.getResponseBody().write(out); ex.close();
        });
        server.start();
        try {
            HttpVisintAdapter a = adapter("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/evidence/validate", new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2});
            var r = a.review(request());
            assertEquals("PASS", r.result());
            assertEquals(STD2, r.matchedStandardImageId());
        } finally { server.stop(0); }
        assertEquals("secreto", auth.get(), "token en X-API-Key, sin esquema");
        assertNull(upgrade.get(), "HTTP/1.1 sin Upgrade: h2c (VISINT real pierde el cuerpo multipart con esa cabecera)");
        assertTrue(type.get().startsWith("multipart/form-data; boundary="));
        String b = body.get();
        for (String f : List.of("requestId", "source", "companyId", "pointId", "postId", "serviceType", "serviceId", "activityId", "evidenceId", "capturedAt", "latitude", "longitude"))
            assertTrue(b.contains("name=\"" + f + "\""), "falta el campo " + f);
        assertTrue(b.contains("\r\n\r\nSGI_COM\r\n"));
        assertTrue(b.contains("\r\n\r\nPATRULLA\r\n"));
        assertTrue(b.contains("name=\"evidenceId\"\r\n\r\n" + EVIDENCE + "\r\n"), "evidenceId = foto del agente");
        assertEquals(1, count(b, "name=\"image\""));
        assertTrue(b.contains("name=\"image\"; filename=\"" + EVIDENCE + ".jpg\""), "image = foto del agente");
        assertEquals(2, count(b, "name=\"referenceImages\""));
        assertEquals(2, count(b, "name=\"referenceIds\""));
        assertTrue(b.indexOf("\r\n\r\n" + STD1 + "\r\n") < b.indexOf("\r\n\r\n" + STD2 + "\r\n"), "referenceIds = fotos estándar en orden");
    }

    /** El motivo que da VISINT al rechazar la llamada (p. ej. 422) queda en el error para poder diagnosticarlo. */
    @Test void rejectionKeepsVisintReason() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/evidence/validate", ex -> {
            ex.getRequestBody().readAllBytes();
            byte[] out = "{\"detail\":[{\"loc\":[\"body\",\"image\"],\"msg\":\"Field required\"}]}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(422, out.length); ex.getResponseBody().write(out); ex.close();
        });
        server.start();
        try {
            HttpVisintAdapter a = adapter("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/evidence/validate", new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2});
            var e = assertThrows(VisintUnavailableException.class, () -> a.review(request()));
            assertTrue(e.getMessage().contains("HTTP 422"), e.getMessage());
            assertTrue(e.getMessage().contains("Field required"), e.getMessage());
        } finally { server.stop(0); }
    }

    /** VISINT real: HTTP 500 con {"status":"ERROR_VISINT","reasonCode":"INTERNAL_ERROR"} es un veredicto de falla técnica, no "sin respuesta". */
    @Test void structuredErrorWithHttp500IsVisintError() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/evidence/validate", ex -> {
            ex.getRequestBody().readAllBytes();
            byte[] out = ("{\"requestId\":\"x\",\"status\":\"ERROR_VISINT\",\"quality\":{\"valid\":false,\"score\":0.0},\"match\":{\"compatible\":false,\"score\":null},"
                + "\"matchedReferenceId\":null,\"reasonCode\":\"INTERNAL_ERROR\",\"modelVersion\":\"visint-evidence-1/dinov2-large-v1/quality-1\"}").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(500, out.length); ex.getResponseBody().write(out); ex.close();
        });
        server.start();
        try {
            HttpVisintAdapter a = adapter("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/evidence/validate", new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2});
            var r = a.review(request());
            assertEquals("ERROR", r.result());
            assertEquals("INTERNAL_ERROR", r.reasonCode());
            assertEquals("visint-evidence-1/dinov2-large-v1/quality-1", r.modelVersion());
        } finally { server.stop(0); }
    }

    @Test void serviceTypePerModule() {
        assertEquals("PATRULLA", HttpVisintAdapter.serviceType("PATROL_CHECKPOINT"));
        assertEquals("CONSIGNA", HttpVisintAdapter.serviceType("CONSIGNMENT_EVIDENCE"));
        assertEquals("BITACORA", HttpVisintAdapter.serviceType("LOGBOOK_FIELD"));
    }

    static int count(String s, String part) { int n = 0, i = 0; while ((i = s.indexOf(part, i)) >= 0) { n++; i += part.length(); } return n; }

    HttpVisintAdapter adapter(String url, byte[] photo) {
        HttpVisintAdapter a = new HttpVisintAdapter();
        a.mapper = m; a.url = Optional.of(url); a.token = Optional.of("secreto"); a.authHeader = "X-API-Key"; a.authScheme = Optional.empty(); a.timeoutSeconds = 5;
        a.storage = new StorageService() { @Override public byte[] read(String bucket, String key) { return photo; } };
        return a;
    }

    static VisintPort.ReviewRequest request() {
        return new VisintPort.ReviewRequest(UUID.randomUUID(), 1, UUID.randomUUID(), "PATROL_CHECKPOINT", UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            new VisintPort.EvidenceRef(EVIDENCE, "b", "k", "a".repeat(64), "image/jpeg", Instant.parse("2026-09-30T15:00:00Z"), -2.19, -79.88),
            List.of(new VisintPort.StandardRef(STD1, 1, "s", "std1", "b".repeat(64), "image/jpeg"), new VisintPort.StandardRef(STD2, 2, "s", "std2", "c".repeat(64), "image/png")),
            UUID.randomUUID());
    }
}
