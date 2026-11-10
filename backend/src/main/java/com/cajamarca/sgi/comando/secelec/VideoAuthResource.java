package com.cajamarca.sgi.comando.secelec;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Lo llama MediaMTX (authMethod: http) antes de entregar cada pedido. Responde 200 si se permite y 401 si no:
 * - ver video (read) de live/…: solo con el token que firmó Comando para esa ruta (ver VideoToken);
 * - API de MediaMTX (api): solo con el usuario y clave de API que usa Comando para registrar las cámaras;
 * - publicar en live/…: solo desde 127.0.0.1 (FFmpeg de MediaMTX que convierte H.265 → H.264);
 * - todo lo demás (métricas…): no.
 * No necesita sesión porque quien llama es el servidor de video, no una persona.
 */
@Path("/api/internal/video/auth")
@PermitAll
public class VideoAuthResource {
    private static final Logger LOG = Logger.getLogger(VideoAuthResource.class);

    @ConfigProperty(name = "sgi.video.mediamtx.api-user") Optional<String> apiUser;
    @ConfigProperty(name = "sgi.video.mediamtx.api-password") Optional<String> apiPassword;
    @Inject VideoToken tokens;

    /** Lo que envía MediaMTX (los campos que no se usan se ignoran). */
    public record AuthRequest(String user, String password, String token, String ip, String action, String path, String protocol, String query) {}

    @Inject VideoAuthLog log;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response check(AuthRequest r) {
        if (r == null) return Response.status(401).build();
        String token = Optional.ofNullable(blank(r.token())).orElseGet(() -> queryToken(r.query()));
        String problem = problem(r, token);
        boolean ok = problem == null;
        if (!"api".equals(r.action())) {
            // Lo que llega de MediaMTX, sin el token (guía de Infra, 5.1): el diagnóstico lo usa para saber si MediaMTX pregunta.
            log.add(new VideoAuthLog.Entry(Instant.now(), r.action(), r.path(), r.protocol(), r.ip(), token != null, ok, problem));
            if (!ok) LOG.infof("[SGI-VIDEO] video_auth_denied action=%s path=%s protocol=%s ip=%s token=%s motivo=%s",
                r.action(), r.path(), r.protocol(), r.ip(), token != null ? "sí" : "no", problem);
        } else if (!ok) {
            LOG.infof("[SGI-VIDEO] video_auth_denied action=api user=%s ip=%s", r.user(), r.ip());
        }
        return Response.status(ok ? 200 : 401).build();
    }

    /** null si se permite; si no, el motivo. */
    private String problem(AuthRequest r, String token) {
        if ("api".equals(r.action())) return apiUser.filter(u -> !u.isBlank()).isPresent()
            && same(apiUser.get(), r.user()) && same(apiPassword.orElse(""), r.password()) ? null : "usuario o clave de API incorrectos";
        if (r.path() == null || !r.path().startsWith("live/")) return "ruta fuera de live/";
        // FFmpeg dentro del contenedor de MediaMTX publica el video convertido a H.264 (ver VideoGateway.transcodeJson):
        // solo se acepta desde la propia máquina; el RTSP de MediaMTX escucha solo en 127.0.0.1.
        if ("publish".equals(r.action())) return loopback(r.ip()) ? null : "publicar solo desde 127.0.0.1 (FFmpeg de MediaMTX)";
        if (!"read".equals(r.action())) return "acción no permitida (" + r.action() + ")";
        return tokens.problem(r.path(), token);
    }

    /** token=… de la URL del navegador (?token=vence.firma). */
    static String queryToken(String query) {
        if (query == null) return null;
        for (String part : query.split("&")) {
            if (part.startsWith("token=")) return URLDecoder.decode(part.substring(6), StandardCharsets.UTF_8);
        }
        return null;
    }

    static boolean loopback(String ip) {
        return ip != null && (ip.equals("127.0.0.1") || ip.equals("::1") || ip.equals("::ffff:127.0.0.1"));
    }

    private static String blank(String v) { return v == null || v.isBlank() ? null : v; }

    private static boolean same(String expected, String given) {
        return given != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }
}
