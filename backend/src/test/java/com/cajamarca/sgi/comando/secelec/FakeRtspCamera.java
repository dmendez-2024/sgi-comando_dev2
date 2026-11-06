package com.cajamarca.sgi.comando.secelec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cámara RTSP simulada: pide Digest y, con las credenciales correctas, entrega un SDP si la ruta pedida coincide con pathRegex.
 * Por defecto responde como Hikvision (/Streaming/Channels/{canal}01) con esa cantidad de canales.
 */
final class FakeRtspCamera implements AutoCloseable {
    static final String REALM = "IP Camera(C1234)";
    static final String NONCE = "a1b2c3d4e5";

    private final ServerSocket server;
    private final String username;
    private final String password;
    private final String pathRegex;
    private final String codec;
    private final Thread thread;

    FakeRtspCamera(String username, String password, int channels, String codec) throws IOException {
        this(username, password, codec, ".*/Streaming/Channels/([1-" + channels + "])01$");
    }

    /** Cámara de otra marca: responde solo en las rutas que coinciden con pathRegex (sobre la URI completa). */
    FakeRtspCamera(String username, String password, String codec, String pathRegex) throws IOException {
        this.server = new ServerSocket(0);
        this.username = username;
        this.password = password;
        this.pathRegex = pathRegex;
        this.codec = codec;
        this.thread = Thread.ofVirtual().start(this::serve);
    }

    int port() { return server.getLocalPort(); }

    private void serve() {
        while (!server.isClosed()) {
            try (Socket s = server.accept()) {
                InputStream in = s.getInputStream();
                String request;
                while ((request = readRequest(in)) != null) {
                    String cseq = header(request, "CSeq");
                    String uri = request.substring(request.indexOf(' ') + 1, request.indexOf(" RTSP/1.0"));
                    String auth = header(request, "Authorization");
                    String reply;
                    if (auth == null) {
                        reply = "RTSP/1.0 401 Unauthorized\r\nCSeq: " + cseq + "\r\nWWW-Authenticate: Digest realm=\"" + REALM + "\", nonce=\"" + NONCE + "\", stale=\"FALSE\"\r\n\r\n";
                    } else if (!validDigest(auth, uri)) {
                        reply = "RTSP/1.0 401 Unauthorized\r\nCSeq: " + cseq + "\r\nWWW-Authenticate: Digest realm=\"" + REALM + "\", nonce=\"" + NONCE + "\"\r\n\r\n";
                    } else if (!uri.matches(pathRegex)) {
                        reply = "RTSP/1.0 404 Not Found\r\nCSeq: " + cseq + "\r\n\r\n";
                    } else {
                        String sdp = "v=0\r\no=- 1 1 IN IP4 0.0.0.0\r\ns=Media Presentation\r\nm=video 0 RTP/AVP 96\r\na=rtpmap:96 " + codec + "/90000\r\n"
                            + "m=audio 0 RTP/AVP 8\r\na=rtpmap:8 PCMA/8000\r\n";
                        reply = "RTSP/1.0 200 OK\r\nCSeq: " + cseq + "\r\nContent-Type: application/sdp\r\nContent-Length: " + sdp.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + sdp;
                    }
                    s.getOutputStream().write(reply.getBytes(StandardCharsets.UTF_8));
                    s.getOutputStream().flush();
                }
            } catch (IOException e) {
                // conexión cerrada o servidor detenido
            }
        }
    }

    private boolean validDigest(String auth, String uri) {
        String response = param(auth, "response");
        String expected = md5(md5(username + ":" + REALM + ":" + password) + ":" + NONCE + ":" + md5("DESCRIBE:" + uri));
        return username.equals(param(auth, "username")) && expected.equals(response);
    }

    private static String readRequest(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) >= 0) {
            buf.write(b);
            String s = buf.toString(StandardCharsets.ISO_8859_1);
            if (s.endsWith("\r\n\r\n")) return s;
        }
        return null;
    }

    private static String header(String request, String name) {
        Matcher m = Pattern.compile("(?im)^" + name + ":\\s*([^\\r\\n]+)").matcher(request);
        return m.find() ? m.group(1) : null;
    }

    private static String param(String header, String name) {
        Matcher m = Pattern.compile(name + "=\"([^\"]*)\"").matcher(header);
        return m.find() ? m.group(1) : null;
    }

    private static String md5(String v) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(v.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    @Override public void close() throws IOException {
        server.close();
        thread.interrupt();
    }
}
