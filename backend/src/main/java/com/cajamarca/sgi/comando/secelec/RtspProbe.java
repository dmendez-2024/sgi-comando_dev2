package com.cajamarca.sgi.comando.secelec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Probar conexión" de una cámara: pide DESCRIBE del flujo RTSP del canal (ruta Hikvision /Streaming/Channels/{canal}01),
 * con autenticación Digest o Basic. Valida red, credenciales y canal en una sola llamada y devuelve el códec del video.
 * Sin dependencias: un socket, dos peticiones como máximo y respuestas acotadas.
 */
public final class RtspProbe {
    public enum Outcome { OK, AUTH_FAILED, NOT_FOUND, UNREACHABLE, TIMEOUT, PROTOCOL_ERROR }
    public record Result(Outcome outcome, String message, String codec, long elapsedMs) {
        public boolean ok() { return outcome == Outcome.OK; }
    }

    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final Pattern PARAM = Pattern.compile("(\\w+)=(?:\"([^\"]*)\"|([^,\\s]+))");
    private static final Pattern RTPMAP = Pattern.compile("a=rtpmap:\\d+\\s+([A-Za-z0-9-]+)/", Pattern.CASE_INSENSITIVE);

    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public RtspProbe(int connectTimeoutMs, int readTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    public static String streamPath(int channel) {
        return "/Streaming/Channels/" + channel + "01";
    }

    public Result describe(String host, int port, int channel, String username, String password) {
        long start = System.nanoTime();
        String uri = "rtsp://" + (host.contains(":") ? "[" + host + "]" : host) + ":" + port + streamPath(channel);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            socket.setSoTimeout(readTimeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            Response first = exchange(out, in, uri, 1, null);
            Response last = first;
            if (first.status == 401) {
                String challenge = first.headers.get("www-authenticate");
                if (challenge == null) return result(Outcome.PROTOCOL_ERROR, "La cámara pidió autenticación sin indicar el método.", null, start);
                last = exchange(out, in, uri, 2, authorization(challenge, username, password, uri));
            }
            return switch (last.status) {
                case 200 -> {
                    String codec = codec(last.body);
                    yield result(Outcome.OK, "Conexión correcta" + (codec == null ? "." : " · video " + codec + "."), codec, start);
                }
                case 401, 403 -> result(Outcome.AUTH_FAILED, "Usuario o contraseña incorrectos.", null, start);
                // Según el equipo, una ruta inexistente vuelve como 404, 454 (Session Not Found) o 400 (MediaMTX, algunos NVR).
                case 400, 404, 454 -> result(Outcome.NOT_FOUND, "La cámara respondió, pero no tiene el canal " + channel + ".", null, start);
                default -> result(Outcome.PROTOCOL_ERROR, "La cámara respondió RTSP " + last.status + ".", null, start);
            };
        } catch (UnknownHostException e) {
            return result(Outcome.UNREACHABLE, "No se encontró el equipo " + host + ".", null, start);
        } catch (SocketTimeoutException e) {
            return result(Outcome.TIMEOUT, "La cámara no respondió a tiempo. Revise la IP, el puerto y la red.", null, start);
        } catch (ConnectException e) {
            return result(Outcome.UNREACHABLE, "No se pudo conectar a " + host + ":" + port + ". Revise la IP, el puerto y la red.", null, start);
        } catch (IOException | IllegalArgumentException e) {
            return result(Outcome.PROTOCOL_ERROR, "El equipo no respondió como una cámara RTSP.", null, start);
        }
    }

    private record Response(int status, Map<String, String> headers, String body) {}

    private Response exchange(OutputStream out, InputStream in, String uri, int cseq, String authorization) throws IOException {
        StringBuilder req = new StringBuilder()
            .append("DESCRIBE ").append(uri).append(" RTSP/1.0\r\n")
            .append("CSeq: ").append(cseq).append("\r\n")
            .append("Accept: application/sdp\r\n")
            .append("User-Agent: SGI-Comando\r\n");
        if (authorization != null) req.append("Authorization: ").append(authorization).append("\r\n");
        out.write(req.append("\r\n").toString().getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
        return read(in);
    }

    private static Response read(InputStream in) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int b, matched = 0;
        while (matched < 4) {
            if ((b = in.read()) < 0) throw new IOException("conexión cerrada");
            head.write(b);
            if (head.size() > MAX_RESPONSE_BYTES) throw new IOException("cabecera demasiado grande");
            matched = (b == '\r' && (matched == 0 || matched == 2)) || (b == '\n' && (matched == 1 || matched == 3)) ? matched + 1 : 0;
        }
        String[] lines = head.toString(StandardCharsets.ISO_8859_1).split("\r\n");
        String[] statusLine = lines[0].split(" ", 3);
        if (statusLine.length < 2 || !statusLine[0].startsWith("RTSP/")) throw new IOException("no es RTSP");
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) headers.putIfAbsent(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim());
        }
        int length = headers.containsKey("content-length") ? Integer.parseInt(headers.get("content-length")) : 0;
        if (length < 0 || length > MAX_RESPONSE_BYTES) throw new IOException("cuerpo demasiado grande");
        byte[] body = in.readNBytes(length);
        return new Response(Integer.parseInt(statusLine[1]), headers, new String(body, StandardCharsets.UTF_8));
    }

    /** Digest (RFC 2617, con o sin qop=auth) o Basic, según lo que pida la cámara. */
    static String authorization(String challenge, String username, String password, String uri) {
        if (challenge.regionMatches(true, 0, "Basic", 0, 5)) {
            return "Basic " + java.util.Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        }
        if (!challenge.regionMatches(true, 0, "Digest", 0, 6)) throw new IllegalArgumentException("método de autenticación no soportado");
        Map<String, String> p = new LinkedHashMap<>();
        Matcher m = PARAM.matcher(challenge.substring(6));
        while (m.find()) p.put(m.group(1).toLowerCase(Locale.ROOT), m.group(2) != null ? m.group(2) : m.group(3));
        String realm = p.getOrDefault("realm", ""), nonce = p.getOrDefault("nonce", "");
        String ha1 = md5(username + ":" + realm + ":" + password);
        String ha2 = md5("DESCRIBE:" + uri);
        boolean qopAuth = p.containsKey("qop") && p.get("qop").toLowerCase(Locale.ROOT).contains("auth");
        StringBuilder h = new StringBuilder("Digest username=\"").append(username).append("\", realm=\"").append(realm)
            .append("\", nonce=\"").append(nonce).append("\", uri=\"").append(uri).append("\"");
        if (qopAuth) {
            String nc = "00000001", cnonce = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            h.append(", qop=auth, nc=").append(nc).append(", cnonce=\"").append(cnonce).append("\", response=\"")
                .append(md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":auth:" + ha2)).append("\"");
        } else {
            h.append(", response=\"").append(md5(ha1 + ":" + nonce + ":" + ha2)).append("\"");
        }
        if (p.containsKey("opaque")) h.append(", opaque=\"").append(p.get("opaque")).append("\"");
        return h.append(", algorithm=MD5").toString();
    }

    /** Primer códec de video del SDP (H264 → "H.264", H265 → "H.265"); null si no se reconoce. */
    static String codec(String sdp) {
        boolean inVideo = false;
        for (String line : sdp.split("\r?\n")) {
            if (line.startsWith("m=")) inVideo = line.startsWith("m=video");
            if (!inVideo) continue;
            Matcher m = RTPMAP.matcher(line);
            if (m.find()) {
                String name = m.group(1).toUpperCase(Locale.ROOT);
                return switch (name) { case "H264" -> "H.264"; case "H265", "HEVC" -> "H.265"; default -> name; };
            }
        }
        return null;
    }

    private static String md5(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Result result(Outcome outcome, String message, String codec, long startNanos) {
        return new Result(outcome, message, codec, (System.nanoTime() - startNanos) / 1_000_000);
    }
}
