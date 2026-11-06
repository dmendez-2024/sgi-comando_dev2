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
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Lo llama MediaMTX (authMethod: http) antes de entregar cada pedido. Responde 200 si se permite y 401 si no:
 * - ver video (read) de live/…: solo con el token que firmó Comando para esa ruta (ver VideoToken);
 * - API de MediaMTX (api): solo con el usuario y clave de API que usa Comando para registrar las cámaras;
 * - todo lo demás (publicar, métricas…): no.
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

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response check(AuthRequest r) {
        boolean ok = r != null && allowed(r);
        if (!ok && r != null) LOG.debugf("video_auth_denied action=%s path=%s ip=%s", r.action(), r.path(), r.ip());
        return Response.status(ok ? 200 : 401).build();
    }

    private boolean allowed(AuthRequest r) {
        if ("api".equals(r.action())) return apiUser.filter(u -> !u.isBlank()).isPresent()
            && same(apiUser.get(), r.user()) && same(apiPassword.orElse(""), r.password());
        if ("read".equals(r.action()) && r.path() != null && r.path().startsWith("live/"))
            return tokens.valid(r.path(), Optional.ofNullable(blank(r.token())).orElseGet(() -> queryToken(r.query())));
        return false;
    }

    /** token=… de la URL del navegador (?token=vence.firma). */
    static String queryToken(String query) {
        if (query == null) return null;
        for (String part : query.split("&")) {
            if (part.startsWith("token=")) return URLDecoder.decode(part.substring(6), StandardCharsets.UTF_8);
        }
        return null;
    }

    private static String blank(String v) { return v == null || v.isBlank() ? null : v; }

    private static boolean same(String expected, String given) {
        return given != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }
}
