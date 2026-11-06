package com.cajamarca.sgi.comando.secelec;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Servidor de video (MediaMTX) para las cámaras de origen directo: Comando registra cada cámara con su URL RTSP y su clave
 * (ruta live/cam-<id>) y le da al navegador la URL HLS o WebRTC que entrega MediaMTX. Así no hay que pegar ninguna URL a mano.
 * PRUEBA: probado contra el NVR simulado local (.local/mediamtx). Solo modo COPY: TRANSCODE (p. ej. H.265 → H.264) no está hecho.
 */
@ApplicationScoped
public class VideoGateway {
    private static final Logger LOG = Logger.getLogger(VideoGateway.class);

    @ConfigProperty(name = "sgi.video.mediamtx.api-url") Optional<String> apiUrl;
    @ConfigProperty(name = "sgi.video.mediamtx.api-user") Optional<String> apiUser;
    @ConfigProperty(name = "sgi.video.mediamtx.api-password") Optional<String> apiPassword;
    /** Base que ve el navegador: http(s)://servidor:8888 para HLS o :8889 para WebRTC. */
    @ConfigProperty(name = "sgi.video.mediamtx.public-url") Optional<String> publicUrl;
    /** hls | webrtc */
    @ConfigProperty(name = "sgi.video.mediamtx.protocol", defaultValue = "hls") String protocol;
    @Inject CameraSecretCipher cipher;
    @Inject VideoToken tokens;

    /** Cada cuánto se confirma que MediaMTX sigue teniendo la cámara: sus registros viven en memoria y se pierden si se reinicia. */
    static final Duration CHECK_EVERY = Duration.ofSeconds(30);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    /** Lo que se registró por cámara y cuándo se confirmó por última vez. */
    private record Registration(String source, Instant checkedAt) {}
    private final Map<UUID, Registration> registered = new ConcurrentHashMap<>();

    public boolean configured() {
        return apiUrl.filter(v -> !v.isBlank()).isPresent() && publicUrl.filter(v -> !v.isBlank()).isPresent();
    }

    /**
     * URL en vivo para el navegador; vacía si no hay servidor de video, la cámara no tiene datos de conexión,
     * tiene el streaming desactivado o MediaMTX no respondió (la consola muestra "Sin señal").
     */
    public Optional<String> liveUrl(SecElecComponent c) {
        if (!configured() || c.family != SecElecComponent.Family.VIDEO || Boolean.FALSE.equals(c.streamingEnabled)
                || c.ip == null || c.port == null || c.username == null || c.passwordCipher == null) return Optional.empty();
        String path = path(c.id);
        String source = rtspSource(c.ip, c.port, CameraBrand.pathOf(c), c.username, cipher.decrypt(c.passwordCipher));
        Registration known = registered.get(c.id);
        Instant now = Instant.now();
        boolean changed = known == null || !known.source().equals(source);
        if (changed || known.checkedAt().plus(CHECK_EVERY).isBefore(now)) {
            // Si cambió la fuente (IP, marca, canal, ruta, clave…) o MediaMTX ya no la tiene (p. ej. se reinició), se vuelve a registrar.
            Boolean exists = changed ? Boolean.FALSE : exists(path);
            if (exists == null) return Optional.empty();
            if (!exists && !register(path, source)) { registered.remove(c.id); return Optional.empty(); }
            registered.put(c.id, new Registration(source, now));
        }
        return Optional.of(browserUrl(path));
    }

    /** Cuánto vive en MediaMTX la vista previa de "Probar conexión". */
    static final Duration PREVIEW_TTL = Duration.ofMinutes(5);
    /** Vistas previas registradas y cuándo se borran. */
    private final Map<String, Instant> previews = new ConcurrentHashMap<>();

    /** Borra de MediaMTX las vistas previas vencidas (cada minuto y en cada nueva vista previa). */
    @io.quarkus.scheduler.Scheduled(every = "1m", delayed = "1m")
    void removeExpiredPreviews() {
        Instant now = Instant.now();
        previews.forEach((path, until) -> {
            if (until.isAfter(now)) return;
            previews.remove(path);
            delete(path);
        });
    }

    /** URL para el navegador con el permiso firmado (?token=…): sin él, MediaMTX no entrega el video (ver VideoAuthResource). */
    private String browserUrl(String path) {
        String base = publicUrl.get().replaceAll("/+$", "");
        String url = "webrtc".equalsIgnoreCase(protocol) ? base + "/" + path + "/whep" : base + "/" + path + "/index.m3u8";
        return url + "?token=" + tokens.sign(path);
    }

    /** Desde dónde el backend pide video para el diagnóstico; vacío = la misma URL del navegador. */
    @ConfigProperty(name = "sgi.video.mediamtx.check-url") Optional<String> checkUrl;

    /** Un paso del diagnóstico: qué se probó, si salió como debía y qué respondió el servidor. */
    public record Step(String name, boolean ok, String detail) {}
    /** ok: todo el flujo funciona. protectedByToken: MediaMTX rechaza el video sin la URL firmada de Comando. */
    public record Diagnostic(boolean configured, boolean ok, boolean protectedByToken, String summary, java.util.List<Step> steps) {}

    /**
     * Prueba el flujo de video sin cámara: registra una ruta vacía (live/diagnostico-…) por la API, la pide sin token, con un
     * token alterado y con el token de Comando, y la borra. Sin publicador MediaMTX responde 404 a quien sí tiene permiso,
     * así que 401 = rechazado y cualquier otra respuesta = dejó pasar.
     */
    public Diagnostic diagnose() {
        java.util.List<Step> steps = new java.util.ArrayList<>();
        if (!configured()) return new Diagnostic(false, false, false,
            "No hay servidor de video configurado (SGI_VIDEO_MEDIAMTX_API_URL y SGI_VIDEO_MEDIAMTX_PUBLIC_URL).", steps);
        String path = "live/diagnostico-" + UUID.randomUUID();
        boolean registered = registerJson(path, "{\"source\":\"publisher\"}");
        steps.add(new Step("Comando entra a la API de MediaMTX", registered,
            registered ? "Ruta de prueba registrada." : "La API no respondió o rechazó el usuario y la clave de API de Comando."));
        if (!registered) return new Diagnostic(true, false, false, "Comando no puede registrar cámaras en el servidor de video.", steps);
        try {
            String base = checkBase(browserUrl(path));
            String unsigned = base.substring(0, base.indexOf('?'));
            int noToken = status(unsigned);
            int badToken = status(base.substring(0, base.length() - 3) + "xxx");
            int withToken = status(base);
            boolean reachable = withToken > 0;
            if (!reachable) LOG.warnf("video_diagnostic_unreachable url=%s", unsigned);
            steps.add(new Step("El backend llega al video de MediaMTX", reachable,
                reachable ? "Respondió " + withToken + "." : "Sin respuesta (revisar SGI_VIDEO_MEDIAMTX_CHECK_URL; la dirección probada queda en el log)."));
            if (!reachable) return new Diagnostic(true, false, false, "El backend no llega a la dirección de video de MediaMTX.", steps);
            boolean rejectsNoToken = noToken == 401, rejectsBadToken = badToken == 401, acceptsToken = withToken != 401;
            steps.add(new Step("Sin token: se rechaza", rejectsNoToken, "Respondió " + noToken + (rejectsNoToken ? "." : ": MediaMTX entrega video sin permiso (authMethod no es http).")));
            steps.add(new Step("Token alterado: se rechaza", rejectsBadToken, "Respondió " + badToken + "."));
            steps.add(new Step("Token de Comando: se acepta", acceptsToken, "Respondió " + withToken
                + (acceptsToken ? " (404 es normal: la ruta de prueba no tiene video)." : ": MediaMTX no acepta la firma (¿llega a /api/internal/video/auth? ¿misma SGI_VIDEO_TOKEN_KEY en todos los backends?).")));
            boolean protectedByToken = rejectsNoToken && rejectsBadToken;
            boolean ok = protectedByToken && acceptsToken;
            String summary = ok ? "Todo bien: solo se ve el video con la URL firmada de Comando."
                : !acceptsToken ? "El video no se va a ver: MediaMTX rechaza también la URL firmada."
                : "El video se ve, pero sin protección: cualquiera con la URL puede verlo.";
            return new Diagnostic(true, ok, protectedByToken, summary, steps);
        } finally {
            delete(path);
        }
    }

    /** La URL del navegador, pero pedida desde donde la ve el backend (check-url); sin check-url, la misma. */
    private String checkBase(String url) {
        return checkUrl.filter(v -> !v.isBlank()).map(v -> v.replaceAll("/+$", ""))
            .map(v -> v + url.substring(publicUrl.get().replaceAll("/+$", "").length())).orElse(url);
    }

    private static final RtspProbe PROBE = new RtspProbe(3000, 5000);

    /**
     * Resultado de probar una cámara de punta a punta sin guardarla. videoUrl: la URL firmada para abrir el video
     * (VLC, Safari o la consola); vive {@link #PREVIEW_TTL} y luego se borra sola.
     */
    public record CameraDiagnostic(boolean ok, String summary, String codec, String rtspPath, String videoUrl, long videoMinutes,
                                   java.util.List<Step> steps) {}

    /**
     * Prueba una cámara sin Punto ni orden de servicio: la registra en MediaMTX en una ruta temporal (live/prueba-…), MediaMTX
     * se conecta a la cámara y se comprueba que el video sale solo con la URL firmada. Es la misma vista previa de
     * "Probar conexión", con cada paso explicado. Sin servidor de video, solo prueba el RTSP directo desde Comando.
     */
    public CameraDiagnostic diagnoseCamera(String host, int port, String rtspPath, String user, String password) {
        java.util.List<Step> steps = new java.util.ArrayList<>();
        if (!configured()) {
            // Sin servidor de video solo se puede probar la cámara directo desde Comando (p. ej. en local).
            RtspProbe.Result r = PROBE.describe(host, port, rtspPath, user, password);
            steps.add(new Step("La cámara responde por RTSP", r.ok(), r.message()));
            if (r.ok()) steps.add(codecStep(r.codec()));
            return new CameraDiagnostic(false, r.ok()
                ? "La cámara responde, pero no hay servidor de video configurado (SGI_VIDEO_MEDIAMTX_API_URL y SGI_VIDEO_MEDIAMTX_PUBLIC_URL)."
                : "Comando no se pudo conectar con la cámara: " + r.message(), r.codec(), rtspPath, null, 0, steps);
        }

        // En el servidor Comando no llega a la red de cámaras: la prueba la hace MediaMTX, que sí llega.
        ServerProbe s = probeThroughServer(host, port, rtspPath, user, password);
        steps.add(new Step("Comando registra la cámara en MediaMTX", s.path() != null,
            s.path() != null ? "Ruta temporal " + s.path() + "." : s.message()));
        if (s.path() == null) return new CameraDiagnostic(false, "Comando no pudo registrar la cámara en el servidor de video.", null, rtspPath, null, 0, steps);
        steps.add(new Step("MediaMTX se conecta a la cámara", s.ok(), s.message()));
        if (!s.ok()) return new CameraDiagnostic(false, "El servidor de video no se pudo conectar con la cámara: revise IP, usuario, clave o ruta.",
            null, rtspPath, null, 0, steps);
        steps.add(codecStep(s.codec()));
        boolean h264 = isH264(s.codec());
        String url = s.previewUrl();
        long minutes = PREVIEW_TTL.toMinutes();
        String codec = s.codec();

        if ("webrtc".equalsIgnoreCase(protocol)) {
            steps.add(new Step("MediaMTX entrega el video", true, "Con WebRTC el backend no lo comprueba: abrir la URL en el navegador."));
            boolean ok = steps.stream().allMatch(Step::ok);
            return new CameraDiagnostic(ok, ok ? "Cámara registrada. Abra la URL del video para verla." : "Cámara registrada, pero revise los pasos en rojo.",
                codec, rtspPath, url, minutes, steps);
        }
        String base = checkBase(url);
        int noToken = status(base.substring(0, base.indexOf('?')));
        steps.add(new Step("Sin token: se rechaza", noToken == 401,
            "Respondió " + noToken + (noToken == 401 ? "." : ": MediaMTX entrega video sin permiso (authMethod no es http).")));
        int playlist = playlist(base);
        boolean plays = playlist == 200;
        steps.add(new Step("Con la URL firmada de Comando: llega el video", plays, plays ? "MediaMTX entregó la lista de video (#EXTM3U)."
            : playlist == 0 ? "Sin respuesta (revisar SGI_VIDEO_MEDIAMTX_CHECK_URL)."
            : playlist == 401 ? "Respondió 401: MediaMTX no acepta la firma (¿llega a /api/internal/video/auth?)."
            : "Respondió " + playlist + ": MediaMTX no pudo leer la cámara (revisar que MediaMTX llegue a la IP de la cámara)."));
        boolean ok = steps.stream().allMatch(Step::ok);
        String summary = ok ? "Todo bien: la cámara llega a MediaMTX y solo se ve con la URL firmada de Comando."
            : !h264 ? "El video llega, pero en H.265: el navegador no lo va a reproducir."
            : !plays ? "MediaMTX recibe la cámara, pero no entrega el video al backend."
            : "El video llega, pero sin protección: cualquiera con la URL puede verlo.";
        return new CameraDiagnostic(ok, summary, codec, rtspPath, url, minutes, steps);
    }

    private static boolean isH264(String codec) { return codec != null && codec.toUpperCase().replace(".", "").contains("H264"); }

    private static Step codecStep(String codec) {
        boolean h264 = isH264(codec);
        return new Step("La cámara envía H.264 (el navegador lo reproduce)", h264, "Códec: " + (codec == null ? "desconocido" : codec)
            + (h264 ? "." : ". Cambiar el video de la cámara a H.264; H.265 no se ve en el navegador."));
    }

    /** Cuánto se espera a que MediaMTX reciba imagen de la cámara (con sourceOnDemand se conecta recién al pedir el video). */
    static final Duration PROBE_WAIT = Duration.ofSeconds(10);
    private static final java.util.regex.Pattern READY = java.util.regex.Pattern.compile("\"(?:ready|online)\"\\s*:\\s*true");
    private static final java.util.regex.Pattern TRACKS = java.util.regex.Pattern.compile("\"tracks\"\\s*:\\s*\\[\\s*\"([^\"]+)\"");

    /** path: ruta temporal registrada (null si la API no la aceptó). previewUrl: video firmado para ver en el formulario. */
    public record ServerProbe(RtspProbe.Outcome outcome, String message, String codec, long elapsedMs, String path, String previewUrl) {
        public boolean ok() { return outcome == RtspProbe.Outcome.OK; }
    }

    /**
     * "Probar conexión" a través del servidor de video: Comando no tiene acceso a la red de cámaras, MediaMTX sí. Registra los
     * datos en una ruta temporal (live/prueba-…), pide el video para que MediaMTX se conecte y consulta su estado hasta
     * {@link #PROBE_WAIT}: lista = conectó (y con qué códec); si no, IP, usuario, clave o ruta están mal. La vista previa vive
     * {@link #PREVIEW_TTL}; si no conectó se borra en el acto.
     */
    public ServerProbe probeThroughServer(String host, int port, String rtspPath, String user, String password) {
        long start = System.nanoTime();
        removeExpiredPreviews();
        String path = "live/prueba-" + UUID.randomUUID();
        if (!register(path, rtspSource(host, port, rtspPath, user, password)))
            return new ServerProbe(RtspProbe.Outcome.PROTOCOL_ERROR, "La API del servidor de video no respondió o rechazó el usuario y la clave de Comando.",
                null, elapsed(start), null, null);
        previews.put(path, Instant.now().plus(PREVIEW_TTL));
        String url = browserUrl(path);
        String hls = checkBase(publicUrl.get().replaceAll("/+$", "") + "/" + path + "/index.m3u8?token=" + tokens.sign(path));
        // Despierta la cámara (sourceOnDemand) sin esperar la respuesta: el estado se consulta aparte.
        http.sendAsync(HttpRequest.newBuilder(URI.create(hls)).timeout(PROBE_WAIT).GET().build(), HttpResponse.BodyHandlers.discarding())
            .exceptionally(e -> null);
        Instant until = Instant.now().plus(PROBE_WAIT);
        while (Instant.now().isBefore(until)) {
            String state = liveState(path);
            if (state != null && READY.matcher(state).find()) {
                java.util.regex.Matcher t = TRACKS.matcher(state);
                String codec = t.find() ? t.group(1) : null;
                LOG.infof("video_probe_ok path=%s codec=%s", path, codec);
                return new ServerProbe(RtspProbe.Outcome.OK, "El servidor de video se conectó a la cámara.", codec, elapsed(start), path, url);
            }
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        previews.remove(path);
        delete(path);
        LOG.infof("video_probe_timeout path=%s host=%s", path, host);
        return new ServerProbe(RtspProbe.Outcome.TIMEOUT, "No se pudo conectar: revise IP, usuario, clave o ruta (el servidor de video no recibió imagen en "
            + PROBE_WAIT.toSeconds() + " s).", null, elapsed(start), path, null);
    }

    private static long elapsed(long start) { return (System.nanoTime() - start) / 1_000_000; }

    /** Estado en vivo de la ruta (GET /v3/paths/get/…); null si no respondió. */
    private String liveState(String path) {
        try {
            HttpResponse<String> r = http.send(request("/v3/paths/get/" + path).GET().build(), HttpResponse.BodyHandlers.ofString());
            return r.statusCode() == 200 ? r.body() : null;
        } catch (Exception e) {
            LOG.debugf("video_probe_state_unreachable path=%s %s", path, e.getMessage());
            return null;
        }
    }

    /**
     * Pide la lista HLS hasta que MediaMTX la entrega: con sourceOnDemand, la primera vez se conecta a la cámara y puede tardar.
     * 200 solo si llegó una lista de video; si no, el último código (0 si no hubo respuesta).
     */
    private int playlist(String url) {
        int last = 0;
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() == 200 && r.body().contains("#EXTM3U")) return 200;
                last = r.statusCode() == 200 ? 502 : r.statusCode();
                if (last == 401) return last;
            } catch (Exception e) {
                LOG.warnf("video_diagnostic_playlist_unreachable %s", e.getMessage());
                last = 0;
            }
            try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return last; }
        }
        return last;
    }

    /** Código HTTP de un GET; 0 si no hubo respuesta. */
    private int status(String url) {
        try {
            return http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(4)).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (Exception e) {
            LOG.warnf("video_gateway_check_unreachable url=%s %s", url.replaceAll("token=[^&]*", "token=…"), e.getMessage());
            return 0;
        }
    }

    /** Quita la cámara del servidor de video (al eliminarla o pasarla a otra fuente). */
    public void remove(UUID cameraId) {
        registered.remove(cameraId);
        delete(path(cameraId));
    }

    private void delete(String path) {
        if (!configured()) return;
        try {
            http.send(request("/v3/config/paths/delete/" + path).DELETE().build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            LOG.warnf("video_gateway_remove_failed path=%s %s", path, e.getMessage());
        }
    }

    static String path(UUID cameraId) { return "live/cam-" + cameraId; }

    /** rtsp://usuario:clave@host:puerto/ruta (la de la marca, ver CameraBrand), con usuario y clave codificados (pueden tener @, : o /). */
    static String rtspSource(String host, int port, String path, String user, String password) {
        String h = host.contains(":") ? "[" + host + "]" : host;
        return "rtsp://" + enc(user) + ":" + enc(password) + "@" + h + ":" + port + path;
    }

    private static String enc(String v) { return URLEncoder.encode(v, StandardCharsets.UTF_8).replace("+", "%20"); }

    /** "replace" crea la ruta si no existe y la actualiza si cambió; sourceOnDemand: solo se conecta a la cámara mientras alguien mira. */
    private boolean register(String path, String source) {
        return registerJson(path, "{\"source\":\"" + source.replace("\\", "\\\\").replace("\"", "\\\"") + "\",\"sourceOnDemand\":true,\"rtspTransport\":\"tcp\"}");
    }

    private boolean registerJson(String path, String json) {
        try {
            HttpResponse<String> r = http.send(request("/v3/config/paths/replace/" + path)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 == 2) return true;
            LOG.warnf("video_gateway_register_failed path=%s status=%d", path, r.statusCode());
        } catch (Exception e) {
            LOG.warnf("video_gateway_unreachable path=%s %s", path, e.getMessage());
        }
        return false;
    }

    /** true/false si MediaMTX tiene o no la ruta; null si no respondió (la consola muestra "Sin señal" y se reintenta en la próxima vista). */
    private Boolean exists(String path) {
        try {
            int status = http.send(request("/v3/config/paths/get/" + path).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 200) return true;
            if (status == 404) {
                LOG.infof("video_gateway_reregister path=%s (MediaMTX ya no la tenía)", path);
                return false;
            }
            LOG.warnf("video_gateway_check_failed path=%s status=%d", path, status);
        } catch (Exception e) {
            LOG.warnf("video_gateway_unreachable path=%s %s", path, e.getMessage());
        }
        return null;
    }

    private HttpRequest.Builder request(String route) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(apiUrl.get().replaceAll("/+$", "") + route)).timeout(Duration.ofSeconds(4));
        if (apiUser.filter(v -> !v.isBlank()).isPresent())
            b.header("Authorization", "Basic " + Base64.getEncoder().encodeToString((apiUser.get() + ":" + apiPassword.orElse("")).getBytes(StandardCharsets.UTF_8)));
        return b;
    }
}
