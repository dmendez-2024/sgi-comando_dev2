package com.cajamarca.sgi.comando.visint;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Diagnóstico de VISINT desde SGI: Comando. Pregunta a CORE (SGI_COM_VISINT_0001_v002, sin caché) la URL y el token
 * (credentialRef de la respuesta) y dice:
 *   - con qué ambiente e Instancia PE se preguntó y qué respondió CORE (o por qué falló);
 *   - si vino el token y, si no, la razón; del token solo largo y huella SHA-256 (8 caracteres), nunca el valor;
 *   - si se llega a VISINT y qué responde: GET, POST multipart vacío sin token y, si hay token, con token (sin fotos;
 *     VISINT no evalúa nada). Cualquier respuesta HTTP es comunicación; sin respuesta, "error" dice el tipo.
 * Abierto sin autenticación para diagnosticar; nunca expone el valor del token.
 */
@Path("/api/internal/visint")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class VisintConnectivityResource {
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5), REQUEST_TIMEOUT = Duration.ofSeconds(15);
    static final int MAX_BODY = 2000;

    @Inject VisintEndpoint endpoint;
    /** Mismo header y esquema con que HttpVisintAdapter manda el token. */
    @ConfigProperty(name="sgi.visint.auth-header", defaultValue="X-API-Key") String authHeader;
    @ConfigProperty(name="sgi.visint.auth-scheme") Optional<String> authScheme;

    @GET
    @Path("/connectivity")
    public Map<String, Object> connectivity() {
        VisintEndpoint.Resolution r = endpoint.resolve(true);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("checkedAt", Instant.now().toString());

        Map<String, Object> core = new LinkedHashMap<>();
        core.put("interconnectionCode", r.interconnectionCode());
        core.put("environment", r.environment());
        core.put("instanceCountryId", r.instanceCountryId());
        core.put("resolved", "CORE".equals(r.source()));
        core.put("authType", r.authType());
        core.put("errorCode", r.coreErrorCode());
        core.put("error", r.coreError());
        out.put("core", core);

        out.put("token", token(r, authHeader));
        out.put("url", r.url());
        out.put("urlSource", r.source());
        if (r.url() == null) {
            out.put("reachable", false);
            out.put("error", "Sin URL de VISINT: ni CORE ni SGI_VISINT_URL");
            return out;
        }
        Map<String, Object> network = network(r.url());
        Map<String, Object> get = probe(r.url(), false, null), post = probe(r.url(), true, null);
        boolean reachable = Boolean.TRUE.equals(get.get("reachable")) || Boolean.TRUE.equals(post.get("reachable"));
        out.put("reachable", reachable);
        if (!reachable) out.put("reason", network.get("reason") != null ? network.get("reason") : get.get("reason"));
        out.put("network", network);
        out.put("get", get);
        out.put("postSinToken", post);
        if (r.token() != null)
            out.put("postConToken", probe(r.url(), true, Map.of(authHeader, authScheme.filter(s -> !s.isBlank()).map(s -> s + " " + r.token()).orElse(r.token()))));
        return out;
    }

    /** Si CORE entregó token: largo y huella (nunca el valor). Si no: la razón. */
    static Map<String, Object> token(VisintEndpoint.Resolution r, String header) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("present", r.token() != null);
        if (r.token() != null) {
            out.put("source", "CORE (credentialRef)");
            out.put("length", r.token().length());
            out.put("sha256Prefix", sha256Prefix(r.token()));
            out.put("header", header);
            if ("NONE".equalsIgnoreCase(r.authType())) out.put("warning", "CORE entrega token pero authType=NONE");
            return out;
        }
        out.put("reason", switch (r.source()) {
            case "CORE" -> "CORE respondió " + r.interconnectionCode() + " sin token (credentialRef vacío) para el ambiente "
                + r.environment() + " y la Instancia " + r.instanceCountryId() + "; authType=" + r.authType();
            default -> "No se obtuvo token porque CORE no respondió: " + r.coreError();
        });
        return out;
    }

    static String sha256Prefix(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 8);
        } catch (Exception e) {
            return null;
        }
    }

    /** reachable = hubo respuesta HTTP; status, tipo y cuerpo (recortado) de lo que respondió, o el error si no respondió. */
    static Map<String, Object> probe(String url, boolean emptyPost, Map<String, String> headers) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("method", emptyPost ? "POST multipart vacío" : "GET");
        long start = System.nanoTime();
        try {
            HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(CONNECT_TIMEOUT).build();
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url)).timeout(REQUEST_TIMEOUT);
            if (headers != null) headers.forEach(req::header);
            if (emptyPost) {
                String boundary = "sgi-diagnostico";
                req.header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofString("--" + boundary + "--\r\n"));
            } else {
                req.GET();
            }
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            String body = res.body() == null ? "" : res.body();
            out.put("reachable", true);
            out.put("httpStatus", res.statusCode());
            out.put("contentType", res.headers().firstValue("Content-Type").orElse(null));
            out.put("body", body.length() > MAX_BODY ? body.substring(0, MAX_BODY) + "…" : body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            out.put("reachable", false);
            out.put("error", "Interrumpido");
        } catch (Exception e) {
            out.put("reachable", false);
            out.put("error", technical(e));
            out.put("reason", explain(e, URI.create(url)));
        }
        out.put("elapsedMs", (System.nanoTime() - start) / 1_000_000);
        return out;
    }

    /**
     * Revisa la conexión por etapas desde el servidor de Comando: DNS (nombre → IP), TCP (abrir conexión al puerto) y,
     * si es https, TLS (certificado). "reason" = primera etapa que falla, en palabras; null si las tres pasan.
     */
    static Map<String, Object> network(String url) {
        Map<String, Object> out = new LinkedHashMap<>();
        URI uri;
        try { uri = URI.create(url); } catch (IllegalArgumentException e) {
            out.put("reason", "La URL de VISINT no es válida: " + url);
            return out;
        }
        String host = uri.getHost();
        boolean https = "https".equalsIgnoreCase(uri.getScheme());
        int port = uri.getPort() > 0 ? uri.getPort() : https ? 443 : 80;
        out.put("host", host);
        out.put("port", port);

        Map<String, Object> dns = new LinkedHashMap<>();
        java.net.InetAddress[] addresses;
        try {
            addresses = java.net.InetAddress.getAllByName(host);
            dns.put("ok", true);
            dns.put("ips", java.util.Arrays.stream(addresses).map(java.net.InetAddress::getHostAddress).toList());
        } catch (Exception e) {
            dns.put("ok", false);
            dns.put("error", technical(e));
            out.put("dns", dns);
            out.put("reason", explain(e, uri));
            return out;
        }
        out.put("dns", dns);

        Map<String, Object> tcp = new LinkedHashMap<>();
        long start = System.nanoTime();
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(addresses[0], port), (int) CONNECT_TIMEOUT.toMillis());
            tcp.put("ok", true);
        } catch (Exception e) {
            tcp.put("ok", false);
            tcp.put("error", technical(e));
            out.put("reason", explain(e, uri));
        }
        tcp.put("elapsedMs", (System.nanoTime() - start) / 1_000_000);
        out.put("tcp", tcp);
        if (out.containsKey("reason") || !https) return out;

        Map<String, Object> tls = new LinkedHashMap<>();
        try (javax.net.ssl.SSLSocket socket = (javax.net.ssl.SSLSocket) javax.net.ssl.SSLSocketFactory.getDefault().createSocket()) {
            socket.connect(new java.net.InetSocketAddress(addresses[0], port), (int) CONNECT_TIMEOUT.toMillis());
            socket.setSoTimeout((int) CONNECT_TIMEOUT.toMillis());
            javax.net.ssl.SSLParameters params = socket.getSSLParameters();
            params.setServerNames(java.util.List.of(new javax.net.ssl.SNIHostName(host)));
            params.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(params);
            socket.startHandshake();
            tls.put("ok", true);
            tls.put("protocol", socket.getSession().getProtocol());
            if (socket.getSession().getPeerCertificates()[0] instanceof java.security.cert.X509Certificate cert) {
                tls.put("certificateSubject", cert.getSubjectX500Principal().getName());
                tls.put("certificateExpires", cert.getNotAfter().toInstant().toString());
            }
        } catch (Exception e) {
            tls.put("ok", false);
            tls.put("error", technical(e));
            out.put("reason", explain(e, uri));
        }
        out.put("tls", tls);
        return out;
    }

    static String technical(Throwable e) {
        return e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
    }

    /** La causa en palabras, según el tipo de error (y el de su causa). */
    static String explain(Throwable e, URI uri) {
        String host = uri.getHost(), where = host + ":" + (uri.getPort() > 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
        Throwable c = e;
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) {
            c = t;
            if (t instanceof java.net.UnknownHostException || t instanceof java.net.ConnectException || t instanceof java.net.NoRouteToHostException
                || t instanceof javax.net.ssl.SSLException || t instanceof java.net.http.HttpTimeoutException || t instanceof java.net.SocketTimeoutException) break;
        }
        // El cliente HTTP envuelve el error real (por ejemplo ConnectException sin mensaje con "Connection refused" adentro).
        StringBuilder all = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause() == t ? null : t.getCause()) all.append(' ').append(t.getMessage());
        String msg = all.toString().toLowerCase(java.util.Locale.ROOT);
        if (c instanceof java.net.UnknownHostException)
            return "DNS: el servidor de Comando no puede convertir '" + host + "' en una IP (nombre inexistente o DNS interno sin ese registro).";
        if (c instanceof java.net.http.HttpConnectTimeoutException || (c instanceof java.net.SocketTimeoutException && msg.contains("connect")))
            return "TCP: no hubo respuesta al conectar a " + where + " en " + CONNECT_TIMEOUT.toSeconds() + " s. Algo en el camino descarta la conexión (firewall, ruta o proxy) o el host está apagado.";
        if (c instanceof java.net.NoRouteToHostException)
            return "TCP: no hay ruta de red desde el servidor de Comando hasta " + where + ".";
        if (c instanceof java.net.ConnectException)
            return msg.contains("refused")
                ? "TCP: " + where + " rechaza la conexión. El host existe pero nada escucha en ese puerto (servicio caído o puerto equivocado)."
                : "TCP: no se pudo abrir conexión a " + where + (c.getMessage() == null ? ". El detalle de la causa está en 'network.reason'." : " (" + c.getMessage() + ").");
        if (c instanceof javax.net.ssl.SSLHandshakeException || c instanceof javax.net.ssl.SSLException)
            return msg.contains("certification path") || msg.contains("pkix")
                ? "TLS: el certificado de " + host + " no es de confianza para Comando (autofirmado o CA interna que no está en Java)."
                : msg.contains("no subject alternative") || msg.contains("no name matching")
                    ? "TLS: el certificado no corresponde al nombre '" + host + "'."
                    : "TLS: falló el cifrado con " + where + " (" + c.getMessage() + ").";
        if (c instanceof java.net.http.HttpTimeoutException || c instanceof java.net.SocketTimeoutException)
            return "HTTP: se conecta a " + where + " pero VISINT no contestó en " + REQUEST_TIMEOUT.toSeconds() + " s (servicio saturado o colgado).";
        if (msg.contains("no bytes") || msg.contains("eof") || msg.contains("connection reset") || msg.contains("closed"))
            return "HTTP: se conecta a " + where + " pero la conexión se cierra sin respuesta (respuesta vacía). Suele ser un proxy o filtro de red en el camino (por ejemplo FortiGuard o una VPN).";
        return "No se pudo comunicar con " + where + ": " + technical(c);
    }
}
