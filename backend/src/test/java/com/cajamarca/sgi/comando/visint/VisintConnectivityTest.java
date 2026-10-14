package com.cajamarca.sgi.comando.visint;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Diagnóstico de VISINT: de dónde sale la URL, si vino el token (o por qué no), si hay comunicación y qué responde. */
class VisintConnectivityTest {
    static VisintEndpoint.Resolution core(String token, String authType) {
        return new VisintEndpoint.Resolution("SGI_COM_VISINT_0001_v002", "https://visint/v1/evidence/validate", "CORE", null, null,
            "DEVELOPMENT", "398233d2-293a-4709-ac30-b74c4269e22d", authType, token);
    }

    @Test void cualquierRespuestaHttpEsComunicacionYSeMuestraLoQueResponde() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/evidence/validate", ex -> {
            boolean post = ex.getRequestMethod().equals("POST");
            boolean withToken = "secreto".equals(ex.getRequestHeaders().getFirst("X-API-Key"));
            byte[] body = (!post ? "{\"detail\":\"Method Not Allowed\"}" : withToken ? "{\"detail\":\"image is required\"}" : "{\"detail\":\"invalid api key\"}")
                .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(!post ? 405 : withToken ? 422 : 401, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/evidence/validate";
            Map<String, Object> get = VisintConnectivityResource.probe(url, false, null);
            assertEquals(true, get.get("reachable"));
            assertEquals(405, get.get("httpStatus"));
            Map<String, Object> sinToken = VisintConnectivityResource.probe(url, true, null);
            assertEquals(401, sinToken.get("httpStatus"));
            assertEquals("{\"detail\":\"invalid api key\"}", sinToken.get("body"));
            Map<String, Object> conToken = VisintConnectivityResource.probe(url, true, Map.of("X-API-Key", "secreto"));
            assertEquals(422, conToken.get("httpStatus"));
            assertEquals("application/json", conToken.get("contentType"));
        } finally {
            server.stop(0);
        }
    }

    @Test void sinServidorNoHayComunicacionYDiceElError() throws Exception {
        int freePort;
        try (ServerSocket s = new ServerSocket(0)) { freePort = s.getLocalPort(); }
        Map<String, Object> probe = VisintConnectivityResource.probe("http://127.0.0.1:" + freePort + "/v1/evidence/validate", false, null);
        assertEquals(false, probe.get("reachable"));
        assertNotNull(probe.get("error"));
        assertNull(probe.get("httpStatus"));
    }

    @Test void nombreQueNoExisteFallaEnDns() {
        Map<String, Object> net = VisintConnectivityResource.network("https://visint.no-existe.invalid/v1/evidence/validate");
        assertEquals(false, ((Map<?, ?>) net.get("dns")).get("ok"));
        assertTrue(((String) net.get("reason")).startsWith("DNS:"), (String) net.get("reason"));
    }

    @Test void puertoCerradoFallaEnTcpYDiceQueRechaza() throws Exception {
        int freePort;
        try (ServerSocket s = new ServerSocket(0)) { freePort = s.getLocalPort(); }
        String url = "http://127.0.0.1:" + freePort + "/v1/evidence/validate";
        Map<String, Object> net = VisintConnectivityResource.network(url);
        assertEquals(true, ((Map<?, ?>) net.get("dns")).get("ok"));
        assertEquals(false, ((Map<?, ?>) net.get("tcp")).get("ok"));
        assertTrue(((String) net.get("reason")).contains("rechaza"), (String) net.get("reason"));
        // El cliente HTTP no informa "refused": la prueba HTTP dice que falló en TCP y la causa exacta queda en network.
        assertTrue(((String) VisintConnectivityResource.probe(url, false, null).get("reason")).startsWith("TCP:"));
    }

    @Test void servidorQueCierraSinResponderEsRespuestaVacia() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Thread t = new Thread(() -> { try { while (true) server.accept().close(); } catch (Exception ignored) {} });
            t.setDaemon(true);
            t.start();
            String url = "http://127.0.0.1:" + server.getLocalPort() + "/v1/evidence/validate";
            assertNull(VisintConnectivityResource.network(url).get("reason"));   // DNS y TCP pasan
            Map<String, Object> probe = VisintConnectivityResource.probe(url, false, null);
            assertEquals(false, probe.get("reachable"));
            assertTrue(((String) probe.get("reason")).contains("respuesta vacía"), (String) probe.get("reason"));
        }
    }

    @Test void tokenDeCoreSeInformaSinMostrarElValor() {
        String secreto = "8398231db47f81a5c8d8f131171aeb6cf8cff6040aab1e7a674d49d16a967f94".replace('8', 'a');
        Map<String, Object> t = VisintConnectivityResource.token(core(secreto, "SERVICE_TOKEN"), "X-API-Key");
        assertEquals(true, t.get("present"));
        assertEquals(64, t.get("length"));
        assertEquals(8, ((String) t.get("sha256Prefix")).length());
        assertFalse(t.toString().contains(secreto));
        assertFalse(core(secreto, "SERVICE_TOKEN").toString().contains(secreto));
        assertNull(t.get("warning"));
        assertNotNull(VisintConnectivityResource.token(core(secreto, "NONE"), "X-API-Key").get("warning"));
    }

    @Test void sinTokenDiceLaRazon() {
        Map<String, Object> t = VisintConnectivityResource.token(core(null, "SERVICE_TOKEN"), "X-API-Key");
        assertEquals(false, t.get("present"));
        String reason = (String) t.get("reason");
        assertTrue(reason.contains("sin token") && reason.contains("DEVELOPMENT") && reason.contains("398233d2"), reason);

        VisintEndpoint.Resolution caido = new VisintEndpoint.Resolution("SGI_COM_VISINT_0001_v002", "http://respaldo", "SGI_VISINT_URL",
            "CORE_RESOLUTION_HTTP_404", "CORE no resolvió SGI_COM_VISINT_0001_v002: HTTP 404", "DEVELOPMENT", null, null, null);
        assertTrue(((String) VisintConnectivityResource.token(caido, "X-API-Key").get("reason")).contains("HTTP 404"));
    }

    @Test void sinCoreUsaSgiVisintUrlYDiceElMotivo() {
        VisintEndpoint.Resolution r = VisintEndpoint.fixed(Optional.of("http://visint-env:8010/v1/evidence/validate")).resolve(true);
        assertEquals("SGI_VISINT_URL", r.source());
        assertEquals("CORE_RESOLVER_NOT_CONFIGURED", r.coreErrorCode());
        assertEquals("http://visint-env:8010/v1/evidence/validate", r.url());
        assertNull(r.token());
    }

    @Test void sinCoreNiRespaldoNoHayUrl() {
        VisintEndpoint.Resolution r = VisintEndpoint.fixed(Optional.empty()).resolve(true);
        assertEquals("NONE", r.source());
        assertNull(r.url());
    }
}
