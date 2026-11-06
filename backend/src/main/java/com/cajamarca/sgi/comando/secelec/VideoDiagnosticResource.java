package com.cajamarca.sgi.comando.secelec;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Diagnóstico del servidor de video, para probar sin sesión mientras no llega la orden de servicio de SICCOM:
 * <ul>
 *   <li>GET: Comando entra a la API de MediaMTX y el video solo se entrega con la URL firmada (sin cámara).</li>
 *   <li>POST /camera: lo mismo con una cámara real, sin Punto ni orden de servicio y sin guardar nada.</li>
 * </ul>
 * Solo existe con SGI_VIDEO_DIAGNOSTIC_ENABLED=true (encenderlo mientras se prueba, apagado en producción); si no, 404.
 */
@Path("/api/internal/video/diagnostic")
@PermitAll
public class VideoDiagnosticResource {
    private static final Logger LOG = Logger.getLogger(VideoDiagnosticResource.class);

    @ConfigProperty(name = "sgi.video.diagnostic-enabled", defaultValue = "false") boolean enabled;
    @Inject VideoGateway video;

    /** Datos de la cámara, como en el formulario de Seguridad Electrónica. Por defecto: puerto 554, canal 1 y marca HIKVISION. */
    public record CameraRequest(String ip, Integer port, Integer channel, CameraBrand cameraBrand, String rtspPath, String username, String password) {}

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public VideoGateway.Diagnostic diagnose() {
        requireEnabled();
        return video.diagnose();
    }

    /** Prueba la cámara de punta a punta: RTSP, registro en MediaMTX y video con la URL firmada. Responde 200 con cada paso. */
    @POST @Path("/camera")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public VideoGateway.CameraDiagnostic camera(CameraRequest body) {
        requireEnabled();
        if (body == null) throw new BadRequestException("Envíe los datos de la cámara.");
        String ip = required(body.ip(), "La dirección IP es obligatoria.");
        if (ip.length() > 255 || !SecElecResource.HOST.matcher(ip).matches()) throw new BadRequestException("Dirección IP o nombre de equipo inválido.");
        int port = body.port() == null ? CameraBrand.DEFAULT_PORT : body.port();
        if (port < 1 || port > 65535) throw new BadRequestException("El puerto debe estar entre 1 y 65535.");
        int channel = body.channel() == null ? 1 : body.channel();
        if (channel < 1 || channel > 256) throw new BadRequestException("El canal debe estar entre 1 y 256.");
        CameraBrand brand = body.cameraBrand() == null ? CameraBrand.HIKVISION : body.cameraBrand();
        String path = brand.path(channel, SecElecResource.rtspPath(brand, body.rtspPath()));
        String username = required(body.username(), "El usuario de la cámara es obligatorio.");
        if (body.password() == null || body.password().isEmpty()) throw new BadRequestException("La contraseña de la cámara es obligatoria.");
        LOG.infof("video_diagnostic_camera host=%s port=%d path=%s", ip, port, path);
        return video.diagnoseCamera(ip, port, path, username, body.password());
    }

    /** El motivo del rechazo viaja como {"message": …}, igual que en el resto de Comando. */
    @ServerExceptionMapper
    public Response error(WebApplicationException e) {
        return Response.status(e.getResponse().getStatus()).entity(Map.of("message", e.getMessage())).type(MediaType.APPLICATION_JSON).build();
    }

    private void requireEnabled() { if (!enabled) throw new NotFoundException("No encontrado"); }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new BadRequestException(message);
        return value.trim();
    }
}
