package com.cajamarca.sgi.comando.secelec;

import com.cajamarca.sgi.comando.secelec.SecElecComponent.Family;
import com.cajamarca.sgi.comando.secelec.SecElecComponent.Status;
import com.cajamarca.sgi.comando.secelec.SecElecComponent.StreamingMode;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Pattern;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Seguridad Electrónica · Componentes de Seguridad del Punto (diapositivas 7, 8, 16 y 17 de "OBSINT Estructura").
 * Alta, edición y baja por familia. Las cámaras (VIDEO) guardan IP, puerto y credenciales; la contraseña nunca se devuelve.
 */
@Path("/api/secelec")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
public class SecElecResource {
    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$");
    private static final Map<Family, String> CODE_PREFIX = Map.of(Family.VIDEO, "CCTV", Family.PERIMETER, "PER", Family.ACCESS_CONTROL, "ACC",
        Family.INTRUSION, "INT", Family.LIGHTING, "ILU", Family.FIRE, "INC");

    @Inject SecElecPlatform platform;
    @Inject CameraSecretCipher cipher;
    @Inject EntityManager em;

    public record ComponentView(UUID id, UUID pointId, Family family, String componentType, String code, String name, String location,
                                String coverage, String brandModel, Status status, String observations, String ip, Integer port,
                                Integer channel, BigDecimal latitude, BigDecimal longitude, Boolean streamingEnabled, StreamingMode streamingMode,
                                String username, boolean hasPassword, String streamUrl, String updatedBy, java.time.Instant updatedAt) {
        static ComponentView of(SecElecComponent c) {
            return new ComponentView(c.id, c.pointId, c.family, c.componentType, c.code, c.name, c.location, c.coverage, c.brandModel, c.status,
                c.observations, c.ip, c.port, c.channel, c.latitude, c.longitude, c.streamingEnabled, c.streamingMode,
                c.username, c.passwordCipher != null, c.streamUrl, c.updatedBy, c.updatedAt);
        }
    }
    /**
     * password: obligatoria al crear una cámara; al editar, vacía o null conserva la actual.
     * channel (1 por defecto), streamingEnabled (sí por defecto) y streamingMode (COPY por defecto) solo aplican a cámaras.
     * latitude y longitude son opcionales, pero van juntas.
     */
    public record SaveRequest(Family family, String componentType, String name, String location, String coverage, String brandModel,
                              Status status, String observations, String ip, Integer port, Integer channel, BigDecimal latitude, BigDecimal longitude,
                              Boolean streamingEnabled, StreamingMode streamingMode, String username, String password, String streamUrl) {}
    public record Summary(int total, int active, int observed, int cameras, String status, String meta) {}
    /** Datos del formulario para "Probar conexión". password vacía + componentId usa la contraseña guardada de esa cámara. */
    public record CameraTestRequest(UUID componentId, String ip, Integer port, Integer channel, String username, String password) {}
    public record CameraTestResult(boolean ok, RtspProbe.Outcome outcome, String message, String codec, String streamPath, long elapsedMs) {}

    private static final RtspProbe PROBE = new RtspProbe(3000, 5000);

    @GET @Path("/points/{pointId}/components") @Transactional
    public List<ComponentView> list(@PathParam("pointId") UUID pointId) {
        platform.requirePoint(pointId);
        return SecElecComponent.<SecElecComponent>list("instanceCountryId=?1 and pointId=?2 order by family, code", platform.instanceCountryId(), pointId)
            .stream().map(ComponentView::of).toList();
    }

    @POST @Path("/points/{pointId}/components") @Transactional
    public ComponentView create(@PathParam("pointId") UUID pointId, SaveRequest body) {
        platform.requirePoint(pointId);
        if (body == null || body.family() == null) throw new BadRequestException("La familia del componente es obligatoria.");
        SecElecComponent c = new SecElecComponent();
        c.instanceCountryId = platform.instanceCountryId();
        c.pointId = pointId;
        c.family = body.family();
        c.code = nextCode(pointId, body.family());
        apply(c, body, true);
        c.persist();
        return ComponentView.of(c);
    }

    @PUT @Path("/components/{id}") @Transactional
    public ComponentView update(@PathParam("id") UUID id, SaveRequest body) {
        SecElecComponent c = component(id);
        if (body == null) throw new BadRequestException("Solicitud obligatoria.");
        if (body.family() != null && body.family() != c.family) throw new BadRequestException("La familia no se puede cambiar; cree otro componente.");
        apply(c, body, false);
        return ComponentView.of(c);
    }

    /** No se elimina un componente que otra configuración usa (p. ej. una cámara asociada a consignas OBSINT): se puede inactivar. */
    @DELETE @Path("/components/{id}") @Transactional
    public void delete(@PathParam("id") UUID id) {
        SecElecComponent c = component(id);
        try {
            c.delete();
            em.flush();
        } catch (PersistenceException e) {
            throw new ClientErrorException("El componente está en uso (por ejemplo, en consignas OBSINT). Cámbielo a Inactivo en lugar de eliminarlo.", 409);
        }
    }

    /**
     * Botón "Test" del formulario de cámara: prueba la conexión RTSP con los datos escritos, sin guardarlos.
     * Siempre responde 200 con el resultado (ok, motivo y códec); los 4xx quedan para datos inválidos o falta de acceso.
     */
    @POST @Path("/points/{pointId}/cameras/test")
    public CameraTestResult testCamera(@PathParam("pointId") UUID pointId, CameraTestRequest body) {
        String password = cameraTestPassword(pointId, body);
        String ip = required(body.ip(), "La dirección IP es obligatoria.", 255);
        if (!HOST.matcher(ip).matches()) throw new BadRequestException("Dirección IP o nombre de equipo inválido.");
        if (body.port() == null || body.port() < 1 || body.port() > 65535) throw new BadRequestException("El puerto debe estar entre 1 y 65535.");
        int channel = body.channel() == null ? 1 : body.channel();
        if (channel < 1 || channel > 256) throw new BadRequestException("El canal debe estar entre 1 y 256.");
        String username = required(body.username(), "El usuario de la cámara es obligatorio.", 120);
        RtspProbe.Result r = PROBE.describe(ip, body.port(), channel, username, password);
        return new CameraTestResult(r.ok(), r.outcome(), r.message(), r.codec(), RtspProbe.streamPath(channel), r.elapsedMs());
    }

    /** Valida el acceso al punto en una transacción corta, para no mantenerla abierta mientras se espera a la cámara. */
    @Transactional
    String cameraTestPassword(UUID pointId, CameraTestRequest body) {
        platform.requirePoint(pointId);
        if (body == null) throw new BadRequestException("Solicitud obligatoria.");
        if (body.password() != null && !body.password().isBlank()) return body.password();
        if (body.componentId() == null) throw new BadRequestException("Escriba la contraseña de la cámara para probar la conexión.");
        SecElecComponent c = component(body.componentId());
        if (!c.pointId.equals(pointId) || c.family != Family.VIDEO || c.passwordCipher == null)
            throw new BadRequestException("Escriba la contraseña de la cámara para probar la conexión.");
        return cipher.decrypt(c.passwordCipher);
    }

    /** Igual que el resto de Comando: el motivo del rechazo viaja como {"message": …} para que la pantalla lo muestre. */
    @ServerExceptionMapper
    public Response error(WebApplicationException e) {
        return Response.status(e.getResponse().getStatus()).entity(Map.of("message", e.getMessage())).type(MediaType.APPLICATION_JSON).build();
    }

    /** Estado para la fila "Componentes de Seguridad" de Configuración del Punto. */
    @GET @Path("/points/{pointId}/summary") @Transactional
    public Summary summary(@PathParam("pointId") UUID pointId) {
        platform.requirePoint(pointId);
        List<SecElecComponent> all = SecElecComponent.list("instanceCountryId=?1 and pointId=?2", platform.instanceCountryId(), pointId);
        int active = (int) all.stream().filter(c -> c.status == Status.ACTIVE).count();
        int observed = (int) all.stream().filter(c -> c.status == Status.OBSERVED).count();
        int cameras = (int) all.stream().filter(c -> c.family == Family.VIDEO && c.status != Status.INACTIVE).count();
        String status = active + observed == 0 ? "blocked" : observed > 0 ? "warning" : "complete";
        return new Summary(all.size(), active, observed, cameras, status, "%d componentes · %d activos".formatted(all.size(), active));
    }

    private void apply(SecElecComponent c, SaveRequest body, boolean creating) {
        c.componentType = required(body.componentType(), "El tipo de componente es obligatorio.", 80);
        c.name = required(body.name(), "El nombre del componente es obligatorio.", 160);
        c.location = optional(body.location(), 200);
        c.coverage = optional(body.coverage(), 300);
        c.brandModel = optional(body.brandModel(), 160);
        c.status = body.status() == null ? Status.ACTIVE : body.status();
        c.observations = optional(body.observations(), 1000);
        if (c.family == Family.VIDEO) {
            String ip = required(body.ip(), "La dirección IP es obligatoria.", 255);
            if (!HOST.matcher(ip).matches()) throw new BadRequestException("Dirección IP o nombre de equipo inválido.");
            if (body.port() == null || body.port() < 1 || body.port() > 65535) throw new BadRequestException("El puerto debe estar entre 1 y 65535.");
            if (body.channel() != null && (body.channel() < 1 || body.channel() > 256)) throw new BadRequestException("El canal debe estar entre 1 y 256.");
            if ((body.latitude() == null) != (body.longitude() == null)) throw new BadRequestException("Ingrese latitud y longitud, o deje ambas vacías.");
            c.ip = ip;
            c.port = body.port();
            c.channel = body.channel() == null ? 1 : body.channel();
            c.latitude = coordinate(body.latitude(), 90, "La latitud debe estar entre -90 y 90.");
            c.longitude = coordinate(body.longitude(), 180, "La longitud debe estar entre -180 y 180.");
            c.streamingEnabled = body.streamingEnabled() == null || body.streamingEnabled();
            c.streamingMode = body.streamingMode() == null ? StreamingMode.COPY : body.streamingMode();
            c.username = required(body.username(), "El usuario de la cámara es obligatorio.", 120);
            String password = body.password() == null ? "" : body.password();
            if (!password.isBlank()) c.passwordCipher = cipher.encrypt(password);
            else if (creating || c.passwordCipher == null) throw new BadRequestException("La contraseña de la cámara es obligatoria.");
            c.streamUrl = streamUrl(body.streamUrl());
        } else {
            c.ip = null; c.port = null; c.username = null; c.passwordCipher = null; c.streamUrl = null;
            c.channel = null; c.latitude = null; c.longitude = null; c.streamingEnabled = null; c.streamingMode = null;
        }
        c.updatedBy = platform.username();
    }

    private String nextCode(UUID pointId, Family family) {
        String prefix = CODE_PREFIX.get(family) + "-";
        int next = SecElecComponent.<SecElecComponent>list("instanceCountryId=?1 and pointId=?2 and family=?3", platform.instanceCountryId(), pointId, family)
            .stream().map(c -> c.code).filter(code -> code.startsWith(prefix))
            .mapToInt(code -> { try { return Integer.parseInt(code.substring(prefix.length())); } catch (NumberFormatException e) { return 0; } })
            .max().orElse(0) + 1;
        return prefix + "%02d".formatted(next);
    }

    /** Ruta relativa a la web (videos de prueba) o URL http(s); cualquier otro esquema se rechaza. */
    private static String streamUrl(String value) {
        String url = optional(value, 500);
        if (url == null) return null;
        if (url.contains(":") && !url.startsWith("http://") && !url.startsWith("https://")) throw new BadRequestException("La URL de video debe ser http(s) o una ruta relativa.");
        return url;
    }

    /** Grados decimales con 6 decimales (≈ 10 cm), que es lo que guarda la columna. */
    private static BigDecimal coordinate(BigDecimal value, int limit, String message) {
        if (value == null) return null;
        if (value.abs().compareTo(BigDecimal.valueOf(limit)) > 0) throw new BadRequestException(message);
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    private SecElecComponent component(UUID id) {
        SecElecComponent c = SecElecComponent.<SecElecComponent>find("id=?1 and instanceCountryId=?2", id, platform.instanceCountryId()).firstResultOptional()
            .orElseThrow(() -> new NotFoundException("Componente no encontrado"));
        platform.requirePoint(c.pointId);
        return c;
    }

    private static String required(String value, String message, int max) {
        String v = optional(value, max);
        if (v == null) throw new BadRequestException(message);
        return v;
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        if (v.length() > max) throw new BadRequestException("Texto demasiado largo (máximo " + max + " caracteres).");
        return v;
    }
}
