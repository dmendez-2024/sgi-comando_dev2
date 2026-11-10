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
    static final Pattern HOST = Pattern.compile("^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$");
    private static final Map<Family, String> CODE_PREFIX = Map.of(Family.VIDEO, "CCTV", Family.PERIMETER, "PER", Family.ACCESS_CONTROL, "ACC",
        Family.INTRUSION, "INT", Family.LIGHTING, "ILU", Family.FIRE, "INC");

    @Inject SecElecPlatform platform;
    @Inject CameraSecretCipher cipher;
    @Inject VideoGateway video;
    @Inject EntityManager em;

    public record ComponentView(UUID id, UUID pointId, Family family, String componentType, String code, String name, String location,
                                String coverage, String brandModel, Status status, String observations, String ip, Integer port,
                                Integer channel, CameraBrand cameraBrand, CameraBrand.StreamQuality streamQuality, String rtspPath, String streamPath, String detectedCodec,
                                BigDecimal latitude, BigDecimal longitude, Boolean streamingEnabled, StreamingMode streamingMode,
                                String username, boolean hasPassword, String streamUrl, UUID electronicDeviceId, String updatedBy, java.time.Instant updatedAt) {
        static ComponentView of(SecElecComponent c) {
            boolean video = c.family == Family.VIDEO;
            return new ComponentView(c.id, c.pointId, c.family, c.componentType, c.code, c.name, c.location, c.coverage, c.brandModel, c.status,
                c.observations, c.ip, c.port, c.channel, video ? (c.cameraBrand == null ? CameraBrand.HIKVISION : c.cameraBrand) : null,
                video ? (c.streamQuality == null ? CameraBrand.StreamQuality.MAIN : c.streamQuality) : null,
                c.rtspPath, video ? CameraBrand.pathOf(c) : null, c.detectedCodec, c.latitude, c.longitude, c.streamingEnabled, c.streamingMode,
                c.username, c.passwordCipher != null, c.streamUrl, c.electronicDeviceId, c.updatedBy, c.updatedAt);
        }
    }
    /**
     * password: obligatoria al crear una cámara; al editar, vacía o null conserva la actual.
     * channel (1 por defecto), cameraBrand (HIKVISION por defecto), streamQuality (MAIN por defecto; no aplica a OTHER),
     * rtspPath (obligatoria y solo con cameraBrand OTHER),
     * streamingEnabled (sí por defecto) y streamingMode (COPY por defecto) solo aplican a cámaras.
     * latitude y longitude son opcionales, pero van juntas.
     * electronicDeviceId: dispositivo OBSINT de SIC:COM del mismo Punto al que pertenece la cámara (opcional).
     */
    public record SaveRequest(Family family, String componentType, String name, String location, String coverage, String brandModel,
                              Status status, String observations, String ip, Integer port, Integer channel, CameraBrand cameraBrand,
                              CameraBrand.StreamQuality streamQuality, String rtspPath,
                              BigDecimal latitude, BigDecimal longitude,
                              Boolean streamingEnabled, StreamingMode streamingMode, String username, String password, String streamUrl,
                              UUID electronicDeviceId) {}
    public record Summary(int total, int active, int observed, int cameras, String status, String meta) {}
    /** Dispositivo OBSINT que SIC:COM envió para el Punto (la línea contratada) y su horario, para elegirlo en la cámara. */
    public record DeviceView(UUID id, String code, String name, String product, String commercialStatus, String schedule, int cameras) {}
    /** Datos del formulario para "Probar conexión". password vacía + componentId usa la contraseña guardada de esa cámara. */
    public record CameraTestRequest(UUID componentId, String ip, Integer port, Integer channel, CameraBrand cameraBrand,
                                    CameraBrand.StreamQuality streamQuality, String rtspPath, String username, String password) {}
    /**
     * Opción de la lista "Marca" del formulario. pathTemplate null = "Otra": la ruta se escribe a mano (requiresPath).
     * subPathTemplate: ruta del stream secundario ({canal} se reemplaza por el canal), para mostrar la ruta que se usará.
     */
    public record BrandView(CameraBrand code, String label, int defaultPort, String pathTemplate, String subPathTemplate, boolean requiresPath) {}
    /** previewUrl: video en vivo para ver en el formulario (solo si conectó y hay servidor de video); vive unos minutos. */
    public record CameraTestResult(boolean ok, RtspProbe.Outcome outcome, String message, String codec, String streamPath, long elapsedMs,
                                   String previewUrl) {}

    private static final RtspProbe PROBE = new RtspProbe(3000, 5000);

    /** Lista "Marca" del formulario de cámara. */
    @GET @Path("/camera-brands")
    public List<BrandView> brands() {
        return Arrays.stream(CameraBrand.values())
            .map(b -> new BrandView(b, b.label, CameraBrand.DEFAULT_PORT, b.pathTemplate, b.subPathTemplate, b.pathTemplate == null)).toList();
    }

    /** Dispositivos OBSINT del Punto enviados por SIC:COM, con su horario y cuántas cámaras tienen asignadas. */
    @GET @Path("/points/{pointId}/devices") @Transactional
    public List<DeviceView> devices(@PathParam("pointId") UUID pointId) {
        platform.requirePoint(pointId);
        UUID tenant = platform.instanceCountryId();
        return ElectronicMonitoringDevice.<ElectronicMonitoringDevice>list("instanceCountryId=?1 and pointId=?2 and upper(postType)='OBSINT' order by code", tenant, pointId)
            .stream().map(d -> new DeviceView(d.id, d.code, d.name, d.product, d.commercialStatus, scheduleText(tenant, d.id),
                (int) SecElecComponent.count("instanceCountryId=?1 and electronicDeviceId=?2 and family=?3 and status<>?4", tenant, d.id, Family.VIDEO, Status.INACTIVE)))
            .toList();
    }

    private static final String[] DAY = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};

    /** Horario contratado en texto: "Lun–Vie 08:00–18:00 · Sáb 08:00–13:00". */
    static String scheduleText(UUID tenant, UUID deviceId) {
        List<String> parts = new ArrayList<>();
        for (ElectronicMonitoringShift s : ElectronicMonitoringShift.<ElectronicMonitoringShift>list(
                "instanceCountryId=?1 and deviceId=?2 and active=true order by shiftCode", tenant, deviceId))
            parts.add(daysText(s.dayMask == null ? 0 : s.dayMask) + " " + s.startTime + "–" + s.endTime);
        return parts.isEmpty() ? "Sin horario" : String.join(" · ", parts);
    }

    /** dayMask (lunes 1 … domingo 64) a texto: rangos seguidos se juntan ("Lun–Vie", "Sáb, Dom"). */
    static String daysText(int mask) {
        if (mask == 127) return "Todos los días";
        List<String> out = new ArrayList<>();
        int d = 0;
        while (d < 7) {
            if ((mask & (1 << d)) == 0) { d++; continue; }
            int end = d;
            while (end + 1 < 7 && (mask & (1 << (end + 1))) != 0) end++;
            out.add(end - d >= 2 ? DAY[d] + "–" + DAY[end] : end == d ? DAY[d] : DAY[d] + ", " + DAY[end]);
            d = end + 1;
        }
        return String.join(", ", out);
    }

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
            if (c.family == Family.VIDEO) video.remove(c.id);
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
        CameraBrand brand = body.cameraBrand() == null ? CameraBrand.HIKVISION : body.cameraBrand();
        String path = brand.path(channel, quality(body.streamQuality()), rtspPath(brand, body.rtspPath()));
        String username = required(body.username(), "El usuario de la cámara es obligatorio.", 120);
        if (video.configured()) {
            // En el servidor Comando no llega a la red de cámaras: prueba MediaMTX, que sí llega, y deja la vista previa.
            // Cámara ya guardada y con los mismos datos: se prueba en su propia ruta (la de la consola), sin abrir otra conexión.
            SecElecComponent saved = body.componentId() == null ? null
                : SecElecComponent.<SecElecComponent>findByIdOptional(body.componentId()).filter(c -> pointId.equals(c.pointId) && sameCamera(c, ip, body.port(), path, username, password)).orElse(null);
            VideoGateway.ServerProbe s = saved != null ? video.probeSaved(saved, password)
                : video.probeThroughServer(ip, body.port(), path, username, password);
            if (s.ok() && s.codec() != null && body.componentId() != null) saveCodec(body.componentId(), s.codec());
            return new CameraTestResult(s.ok(), s.outcome(), s.message(), s.codec(), path, s.elapsedMs(), s.previewUrl());
        }
        RtspProbe.Result r = PROBE.describe(ip, body.port(), path, username, password);
        if (r.ok() && body.componentId() != null) saveCodec(body.componentId(), r.codec());
        return new CameraTestResult(r.ok(), r.outcome(), r.message(), r.codec(), path, r.elapsedMs(), null);
    }

    /** Los datos escritos en el formulario son los mismos que tiene guardados la cámara (misma conexión con ella). */
    private boolean sameCamera(SecElecComponent c, String ip, int port, String path, String username, String password) {
        return c.family == Family.VIDEO && c.passwordCipher != null && ip.equals(c.ip) && Objects.equals(port, c.port)
            && path.equals(CameraBrand.pathOf(c)) && username.equals(c.username) && password.equals(cipher.decrypt(c.passwordCipher));
    }

    /** Guarda el códec que respondió la cámara (la consola avisa si es H.265, que el navegador no reproduce). */
    @Transactional
    void saveCodec(UUID componentId, String codec) {
        SecElecComponent c = component(componentId);
        if (c.family == Family.VIDEO) c.detectedCodec = codec;
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
            Integer previousChannel = c.channel;
            String ip = required(body.ip(), "La dirección IP es obligatoria.", 255);
            if (!HOST.matcher(ip).matches()) throw new BadRequestException("Dirección IP o nombre de equipo inválido.");
            if (body.port() == null || body.port() < 1 || body.port() > 65535) throw new BadRequestException("El puerto debe estar entre 1 y 65535.");
            if (body.channel() != null && (body.channel() < 1 || body.channel() > 256)) throw new BadRequestException("El canal debe estar entre 1 y 256.");
            if ((body.latitude() == null) != (body.longitude() == null)) throw new BadRequestException("Ingrese latitud y longitud, o deje ambas vacías.");
            c.ip = ip;
            c.port = body.port();
            c.channel = body.channel() == null ? 1 : body.channel();
            CameraBrand brand = body.cameraBrand() == null ? CameraBrand.HIKVISION : body.cameraBrand();
            String path = rtspPath(brand, body.rtspPath());
            CameraBrand.StreamQuality quality = quality(body.streamQuality());
            // Otra marca, ruta, canal o calidad: el códec guardado ya no vale hasta volver a probar.
            if (brand != c.cameraBrand || !Objects.equals(path, c.rtspPath) || !Objects.equals(c.channel, previousChannel)
                    || quality != (c.streamQuality == null ? CameraBrand.StreamQuality.MAIN : c.streamQuality)) c.detectedCodec = null;
            c.cameraBrand = brand;
            c.streamQuality = quality;
            c.rtspPath = path;
            c.latitude = coordinate(body.latitude(), 90, "La latitud debe estar entre -90 y 90.");
            c.longitude = coordinate(body.longitude(), 180, "La longitud debe estar entre -180 y 180.");
            c.streamingEnabled = body.streamingEnabled() == null || body.streamingEnabled();
            c.streamingMode = body.streamingMode() == null ? StreamingMode.COPY : body.streamingMode();
            c.username = required(body.username(), "El usuario de la cámara es obligatorio.", 120);
            String password = body.password() == null ? "" : body.password();
            if (!password.isBlank()) c.passwordCipher = cipher.encrypt(password);
            else if (creating || c.passwordCipher == null) throw new BadRequestException("La contraseña de la cámara es obligatoria.");
            c.streamUrl = streamUrl(body.streamUrl());
            c.electronicDeviceId = device(c.pointId, body.electronicDeviceId());
        } else {
            c.electronicDeviceId = null;
            c.ip = null; c.port = null; c.username = null; c.passwordCipher = null; c.streamUrl = null;
            c.channel = null; c.latitude = null; c.longitude = null; c.streamingEnabled = null; c.streamingMode = null;
            c.cameraBrand = null; c.streamQuality = null; c.rtspPath = null; c.detectedCodec = null;
        }
        c.updatedBy = platform.username();
    }

    /** El dispositivo debe ser un OBSINT de SIC:COM del mismo Punto; null deja la cámara sin dispositivo. */
    private UUID device(UUID pointId, UUID deviceId) {
        if (deviceId == null) return null;
        ElectronicMonitoringDevice d = ElectronicMonitoringDevice.<ElectronicMonitoringDevice>find("id=?1 and instanceCountryId=?2", deviceId, platform.instanceCountryId())
            .firstResultOptional().orElse(null);
        if (d == null || !d.pointId.equals(pointId) || !"OBSINT".equalsIgnoreCase(d.postType))
            throw new BadRequestException("El dispositivo elegido no es un servicio OBSINT de este Punto.");
        return d.id;
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

    /** Calidad del video; sin elegir = principal (lo que usaban las cámaras antes de existir la opción). */
    static CameraBrand.StreamQuality quality(CameraBrand.StreamQuality value) {
        return value == null ? CameraBrand.StreamQuality.MAIN : value;
    }

    /**
     * Ruta escrita a mano: obligatoria con marca "Otra" (OTHER) y se ignora con las demás, que usan la de su marca.
     * Debe empezar con "/" y no llevar espacios ni credenciales (esas van en usuario y contraseña).
     */
    static String rtspPath(CameraBrand brand, String value) {
        if (brand != CameraBrand.OTHER) return null;
        String path = optional(value, 300);
        if (path == null) throw new BadRequestException("Con la marca \"Otra\" escriba la ruta del video (por ejemplo /stream1).");
        if (!path.startsWith("/") || path.chars().anyMatch(Character::isWhitespace) || path.contains("@"))
            throw new BadRequestException("La ruta del video debe empezar con \"/\" y no llevar espacios ni usuario o contraseña.");
        return path;
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
