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
 * PRUEBA: probado contra el NVR simulado local (.local/mediamtx).
 * H.265 → H.264: el navegador no reproduce H.265. Si la cámara está en TRANSCODE o se detectó H.265, la ruta no toma la cámara
 * directo: MediaMTX corre FFmpeg mientras alguien mira (runOnDemand), que lee la cámara, la recodifica a H.264 y la publica en la
 * misma ruta por el RTSP interno de MediaMTX. Requiere la imagen bluenviron/mediamtx:…-ffmpeg y rtsp encendido solo en 127.0.0.1.
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
    @Inject VideoAuthLog authLog;

    /** Cada cuánto se confirma que MediaMTX sigue teniendo la cámara: sus registros viven en memoria y se pierden si se reinicia. */
    static final Duration CHECK_EVERY = Duration.ofSeconds(30);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    /** Lo que se registró por cámara y cuándo se confirmó por última vez. */
    private record Registration(String source, Instant checkedAt) {}
    /** Lo que este backend registró en cada ruta (una por cámara) y cuándo lo revisó. */
    private final Map<String, Registration> registered = new ConcurrentHashMap<>();

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
        String camera = rtspSource(c.ip, c.port, CameraBrand.pathOf(c), c.username, cipher.decrypt(c.passwordCipher));
        // Una ruta por cámara (no por componente): dos componentes con la misma cámara comparten la conexión.
        String path = cameraPath(camera);
        cameraByPath.put(path, camera);
        // Lo registrado depende del modo: si cambia (p. ej. se detectó H.265), se vuelve a registrar. El códec puede venir de la
        // cámara guardada o de lo que este backend ya vio en MediaMTX (knownCodec), así no se arranca en directo una H.265 conocida.
        boolean transcode = transcodes(c) || (transcodeH265 && isH265(knownCodec.get(path)));
        String source = transcode ? transcodeJson(camera) : sourceJson(camera, true);
        Registration known = registered.get(path);
        Instant now = Instant.now();
        boolean changed = known == null || !known.source().equals(source);
        if (changed || known.checkedAt().plus(CHECK_EVERY).isBefore(now)) {
            // Si MediaMTX ya no la tiene (p. ej. se reinició) o cambió el modo, se registra. Pero registrar de nuevo una ruta
            // reinicia la conexión y corta a quien mira: si alguien la está viendo, se deja como está.
            Boolean exists = exists(path);
            if (exists == null) {
                LOG.warnf("[SGI-VIDEO] video_sin_senal path=%s camara=%s motivo=api_mediamtx_no_responde", path, c.code);
                return Optional.empty();
            }
            boolean watched = exists && changed && watching(path);
            // Excepción a "no cortar a quien mira": H.265 en directo solo lo ven algunos navegadores; el resto queda en negro.
            // Si hay que convertir y MediaMTX la tiene en directo, se pasa a H.264 igual (corte de unos segundos).
            boolean forceSwitch = watched && transcode && Boolean.FALSE.equals(remoteTranscodes(path));
            if (forceSwitch) watched = false;
            boolean keep = exists && (!changed || watched);
            if (!keep && !registerJson(path, source)) {
                registered.remove(path);
                LOG.warnf("[SGI-VIDEO] video_sin_senal path=%s camara=%s motivo=mediamtx_no_acepto_el_registro", path, c.code);
                return Optional.empty();
            }
            if (!keep) LOG.infof("[SGI-VIDEO] video_ruta_registrada path=%s camara=%s modo=%s motivo=%s", path, c.code,
                transcode ? "transcodifica" : "directo", !exists ? "mediamtx_no_la_tenia" : forceSwitch ? "h265_en_directo_se_pasa_a_h264_aunque_miren"
                    : known == null ? "primera_vez_en_este_backend" : "cambio_el_modo");
            else if (changed) LOG.infof("[SGI-VIDEO] video_ruta_conservada path=%s camara=%s motivo=%s", path, c.code,
                watched ? "alguien_la_mira_no_se_corta" : "ya_estaba_igual_en_mediamtx");
            // Se dejó sin tocar algo que este backend no registró: se marca para revisarlo cuando nadie mire.
            String recorded = keep && changed ? (known == null ? UNKNOWN : known.source()) : source;
            registered.put(path, new Registration(recorded, now));
            registeredAs.put(path, recorded);
        }
        return Optional.of(browserUrl(path));
    }

    /** Cuánto vive en MediaMTX la vista previa de "Probar conexión". */
    static final Duration PREVIEW_TTL = Duration.ofMinutes(5);
    /** Vistas previas registradas y cuándo se borran. */
    private final Map<String, Instant> previews = new ConcurrentHashMap<>();
    /** Revisiones seguidas en que una vista previa vencida no tuvo uso. */
    private final Map<String, Integer> previewMisses = new ConcurrentHashMap<>();
    /** URL RTSP (con su clave, solo en memoria) de cada ruta en uso: para pasarla a H.264 sin volver a leer la cámara de la base. */
    private final Map<String, String> cameraByPath = new ConcurrentHashMap<>();

    /** La ruta está en uso: alguien la mira (lectores en MediaMTX) o está entregando video. */
    private boolean inUse(String path) {
        Hit state = liveState(path);
        if (state.code() != 200) return false;
        java.util.regex.Matcher r = READERS.matcher(state.body());
        boolean readers = r.find() && r.group(1).contains("{");
        return readers || READY.matcher(state.body()).find();
    }

    /** Cómo está registrada la ruta en MediaMTX ahora: true = convierte con FFmpeg, false = cámara directa, null = no se sabe. */
    private Boolean remoteTranscodes(String path) {
        try {
            HttpResponse<String> r = http.send(request("/v3/config/paths/get/" + path).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200 || r.body() == null) return null;
            return RUN_FFMPEG.matcher(r.body()).find();
        } catch (Exception e) {
            LOG.debugf("[SGI-VIDEO] video_config_unreachable path=%s %s", path, e.getMessage());
            return null;
        }
    }
    static final java.util.regex.Pattern RUN_FFMPEG = java.util.regex.Pattern.compile("\"runOnDemand\"\\s*:\\s*\"ffmpeg");

    /**
     * Si la ruta está en directo entregando H.265 y la conversión está encendida, la pasa a H.264 (FFmpeg) aunque alguien mire:
     * el directo H.265 solo lo ven algunos navegadores. Devuelve true si la cambió.
     */
    private boolean switchToH264IfNeeded(String path, String liveBody, String origen) {
        if (!transcodeH265 || liveBody == null) return false;
        java.util.regex.Matcher t = TRACKS.matcher(liveBody);
        String codec = t.find() ? t.group(1) : null;
        if (!isH265(codec)) return false;
        knownCodec.put(path, codec);
        String camera = cameraByPath.get(path);
        if (camera == null || !Boolean.FALSE.equals(remoteTranscodes(path))) return false;
        String json = transcodeJson(camera);
        String err = registerError(path, json);
        if (err != null) {
            LOG.warnf("[SGI-VIDEO] video_paso_a_h264 path=%s resultado=fallo origen=%s %s", path, origen, err);
            return false;
        }
        registered.computeIfPresent(path, (k, v) -> new Registration(json, Instant.now()));
        registeredAs.put(path, json);
        LOG.warnf("[SGI-VIDEO] video_paso_a_h264 path=%s resultado=ok origen=%s motivo=h265_en_directo_solo_lo_ven_algunos_navegadores", path, origen);
        return true;
    }

    /** Borra de MediaMTX las vistas previas vencidas (cada minuto y en cada nueva vista previa). */
    @io.quarkus.scheduler.Scheduled(every = "1m", delayed = "1m")
    void removeExpiredPreviews() {
        Instant now = Instant.now();
        previews.forEach((path, until) -> {
            if (until.isAfter(now)) return;
            if (inUse(path)) {
                // Alguien la sigue mirando: no se le corta; se revisa de nuevo en un minuto.
                previewMisses.remove(path);
                previews.put(path, now.plus(Duration.ofMinutes(1)));
                return;
            }
            // Un instante sin video (la cámara o FFmpeg se reconectan) no basta: se borra solo tras 2 revisiones seguidas sin uso.
            if (previewMisses.merge(path, 1, Integer::sum) < 2) {
                LOG.infof("[SGI-VIDEO] video_vista_previa_vencida path=%s accion=se_espera motivo=sin_uso_en_una_revision", path);
                previews.put(path, now.plus(Duration.ofMinutes(1)));
                return;
            }
            previewMisses.remove(path);
            previews.remove(path);
            // La misma cámara la usa la consola: la ruta se queda (sin nadie mirando no tiene conexión abierta con la cámara).
            if (registered.containsKey(path)) {
                LOG.infof("[SGI-VIDEO] video_vista_previa_vencida path=%s accion=se_conserva motivo=la_usa_la_consola", path);
                return;
            }
            knownCodec.remove(path);
            registeredAs.remove(path);
            LOG.infof("[SGI-VIDEO] video_vista_previa_vencida path=%s accion=se_borra motivo=nadie_la_mira", path);
            delete(path);
        });
    }

    /** Lo último que se vio de cada cámara en uso, para registrar solo los cambios. */
    record Watch(boolean ready, int readers, long bytes, boolean stalled) {}
    private final Map<String, Watch> lastWatch = new ConcurrentHashMap<>();
    private static final java.util.regex.Pattern READERS = java.util.regex.Pattern.compile("\"readers\"\\s*:\\s*\\[(.*?)\\]", java.util.regex.Pattern.DOTALL);

    /**
     * Vigila cada 30 s las cámaras en uso (consola y vistas previas) y deja en el log solo lo que cambia: se conectó, se cayó,
     * cuántos miran, o la cámara dejó de mandar video estando "lista". Así, ante un corte, el log dice la hora y qué pasó.
     * Buscar con: docker logs <backend> 2>&1 | grep SGI-VIDEO
     */
    @io.quarkus.scheduler.Scheduled(every = "30s", delayed = "30s")
    void watchCameras() {
        if (!configured()) return;
        java.util.Set<String> paths = new java.util.HashSet<>(registered.keySet());
        paths.addAll(previews.keySet());
        lastWatch.keySet().retainAll(paths);
        for (String path : paths) {
            Hit state = liveState(path);
            Watch before = lastWatch.get(path);
            if (state.code() != 200) {
                if (before == null || before.ready() || before.readers() > 0)
                    LOG.warnf("[SGI-VIDEO] video_estado path=%s evento=sin_datos_de_mediamtx respuesta=%d %s", path, state.code(),
                        state.error() == null ? "" : state.error());
                lastWatch.put(path, new Watch(false, 0, before == null ? 0 : before.bytes(), false));
                continue;
            }
            String body = state.body();
            boolean ready = READY.matcher(body).find();
            // Cámara nueva conectada en directo que resultó H.265: se pasa a H.264 para que la vean todos.
            if (ready && switchToH264IfNeeded(path, body, "vigilancia")) { lastWatch.remove(path); continue; }
            java.util.regex.Matcher r = READERS.matcher(body), b = BYTES.matcher(body);
            int readers = r.find() ? (int) r.group(1).chars().filter(ch -> ch == '{').count() : 0;
            long bytes = b.find() ? Long.parseLong(b.group(1)) : 0;
            // Lista y con gente mirando, pero sin bytes nuevos desde la vuelta anterior: la cámara dejó de mandar video.
            boolean stalled = before != null && ready && readers > 0 && before.ready() && bytes == before.bytes();
            Watch now = new Watch(ready, readers, bytes, stalled);
            if (before == null || before.ready() != ready || before.readers() != readers || before.stalled() != stalled) {
                String evento = before == null ? "en_uso"
                    : stalled && !before.stalled() ? "camara_no_manda_video"
                    : !stalled && before.stalled() ? "camara_volvio_a_mandar_video"
                    : !before.ready() && ready ? "conectada_con_la_camara"
                    : before.ready() && !ready ? "desconectada_de_la_camara"
                    : "cambio_de_espectadores";
                String text = "[SGI-VIDEO] video_estado path=%s evento=%s lista=%s mirando=%d recibido_mb=%d";
                if (evento.equals("camara_no_manda_video") || evento.equals("desconectada_de_la_camara") && readers > 0)
                    LOG.warnf(text, path, evento, ready, readers, bytes / 1_000_000);
                else LOG.infof(text, path, evento, ready, readers, bytes / 1_000_000);
            }
            lastWatch.put(path, now);
        }
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
        steps.add(new Step("Servidor de video configurado", true, serverInfo()));
        steps.add(mediamtxAuthConfig());
        String path = "live/diagnostico-" + UUID.randomUUID();
        // sourceOnDemand=false explícito: si el servidor lo tiene activado por defecto, MediaMTX rechaza una ruta "publisher" con él.
        String error = registerError(path, "{\"source\":\"publisher\",\"sourceOnDemand\":false}");
        boolean registered = error == null;
        steps.add(new Step("Comando entra a la API de MediaMTX", registered, registered ? "Ruta de prueba registrada." : error));
        if (!registered) return new Diagnostic(true, false, false, "Comando no puede registrar cámaras en el servidor de video.", steps);
        try {
            String base = checkBase(browserUrl(path));
            String unsigned = base.substring(0, base.indexOf('?'));
            Instant asked = Instant.now();
            Hit noToken = hit(unsigned);
            Hit badToken = hit(base.substring(0, base.length() - 3) + "xxx");
            Hit withToken = hit(base);
            boolean reachable = withToken.code() > 0;
            steps.add(new Step("El backend llega al video de MediaMTX", reachable,
                reachable ? "Respondió " + withToken.code() + " en " + hideToken(base) + "."
                    : withToken.text(base) + " (revisar SGI_VIDEO_MEDIAMTX_CHECK_URL: el backend debe poder abrir esa dirección)."));
            if (!reachable) return new Diagnostic(true, false, false, "El backend no llega a la dirección de video de MediaMTX.", steps);
            boolean rejectsNoToken = noToken.code() == 401, rejectsBadToken = badToken.code() == 401, acceptsToken = withToken.code() != 401;
            steps.add(new Step("Sin token: se rechaza", rejectsNoToken, noToken.text(unsigned)
                + (rejectsNoToken ? "" : " → MediaMTX entrega video sin permiso (authMethod no es http).")));
            steps.add(new Step("Token alterado: se rechaza", rejectsBadToken, badToken.text(base)));
            steps.add(new Step("Token de Comando: se acepta", acceptsToken, withToken.text(base)
                + (acceptsToken ? " (404 es normal: la ruta de prueba no tiene video)."
                    : " → MediaMTX no acepta la firma: sigue en modo prueba (usuario y clave propios) o no llega a /api/internal/video/auth de este Comando.")));
            steps.add(authStep(path, asked, !acceptsToken));
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
        steps.add(new Step("Servidor de video configurado", true, serverInfo()));
        ServerProbe s = probeThroughServer(host, port, rtspPath, user, password);
        steps.add(new Step("Comando registra la cámara en MediaMTX", s.path() != null,
            s.path() != null ? "Ruta temporal " + s.path() + "." : s.message()));
        if (s.path() == null) return new CameraDiagnostic(false, "Comando no pudo registrar la cámara en el servidor de video.", null, rtspPath, null, 0, steps);
        steps.add(new Step("MediaMTX se conecta a la cámara", s.ok(), s.message()));
        if (!s.ok()) return new CameraDiagnostic(false, "El servidor de video no se pudo conectar con la cámara: revise IP, usuario, clave o ruta.",
            null, rtspPath, null, 0, steps);
        boolean converted = transcodeH265 && isH265(s.codec());
        steps.add(converted
            ? new Step("La cámara envía H.264 (el navegador lo reproduce)", true, "Códec: " + s.codec()
                + ". El servidor de video lo convierte a H.264 con FFmpeg (usa CPU del servidor por cada cámara que se mira).")
            : codecStep(s.codec()));
        boolean h264 = isH264(s.codec()) || converted;
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
        String unsigned = base.substring(0, base.indexOf('?'));
        Hit noToken = hit(unsigned);
        steps.add(new Step("Sin token: se rechaza", noToken.code() == 401, noToken.text(unsigned)
            + (noToken.code() == 401 ? "" : noToken.code() == 0 ? " (el backend no llega a la dirección del video)" : " → MediaMTX entrega video sin permiso (authMethod no es http).")));
        Instant asked = Instant.now();
        Hit playlist = playlist(base);
        boolean plays = playlist.code() == 200;
        steps.add(new Step("Con la URL firmada de Comando: llega el video", plays, plays ? "MediaMTX entregó la lista de video (#EXTM3U) en " + hideToken(base) + "."
            : playlist.text(base) + (playlist.code() == 0 ? " (revisar SGI_VIDEO_MEDIAMTX_CHECK_URL)."
            : playlist.code() == 401 ? " → MediaMTX no acepta la firma: sigue en modo prueba o no llega a /api/internal/video/auth de este Comando."
            : converted ? " → No llegó el video convertido: revisar que MediaMTX use la imagen -ffmpeg, tenga rtsp encendido en 127.0.0.1"
                + " y los logs de FFmpeg (docker logs sgi-mediamtx)."
            : " → MediaMTX recibe la cámara pero no entregó la lista de video.")));
        if (!plays) steps.add(authStep(s.path(), asked, playlist.code() == 401));
        boolean ok = steps.stream().allMatch(Step::ok);
        String summary = ok ? "Todo bien: la cámara llega a MediaMTX y solo se ve con la URL firmada de Comando."
            : !h264 ? "El video llega, pero en H.265: el navegador no lo va a reproducir (o encender sgi.video.transcode-h265)."
            : converted && !plays ? "La cámara está en H.265 y la conversión a H.264 no entregó video."
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
        removeExpiredPreviews();
        String camera = rtspSource(host, port, rtspPath, user, password);
        // La misma ruta que la consola usa para esa cámara: probar no abre otra conexión con ella.
        String path = cameraPath(camera);
        ServerProbe p = probe(path, camera, false, host, port);
        // La vista previa sigue 5 min desde la última prueba; si no conectó, se borra en el acto (salvo que la use la consola).
        if (p.previewUrl() != null) previews.put(path, Instant.now().plus(PREVIEW_TTL));
        else { previews.remove(path); if (!registered.containsKey(path)) delete(path); }
        return p;
    }

    /**
     * "Probar conexión" de una cámara ya guardada y sin cambios: usa su misma ruta live/cam-<id>, la que ve la consola.
     * Quien prueba y quien mira comparten la única conexión de MediaMTX con la cámara.
     */
    public ServerProbe probeSaved(SecElecComponent c, String password) {
        String camera = rtspSource(c.ip, c.port, CameraBrand.pathOf(c), c.username, password);
        String path = cameraPath(camera);
        Registration known = registered.get(path);
        if (known != null && c.detectedCodec != null) {
            // Lo que ya registró la consola: si alguien la está mirando, se reutiliza esa conexión sin tocar la ruta.
            knownCodec.putIfAbsent(path, c.detectedCodec);
            registeredAs.putIfAbsent(path, known.source());
        }
        ServerProbe p = probe(path, camera, c.streamingMode == SecElecComponent.StreamingMode.TRANSCODE, c.ip, c.port);
        if (p.ok()) {
            // Queda registrada igual que la registra liveUrl, así la consola no la vuelve a registrar (ni corta a quien mira).
            String finalJson = finalJson(camera, p.codec(), c.streamingMode == SecElecComponent.StreamingMode.TRANSCODE);
            registered.put(path, new Registration(finalJson, Instant.now()));
        }
        return p;
    }

    /**
     * Ruta fija por cámara (IP, puerto, ruta, usuario y clave): la consola, "Probar conexión" y dos componentes con la misma
     * cámara caen en la misma ruta y en la misma conexión con la cámara (las cámaras aceptan pocas). Otra clave = otra ruta,
     * así una prueba con datos errados no toca la que funciona. El nombre es una huella secreta: no revela IP ni clave.
     */
    String cameraPath(String camera) { return "live/cam-" + tokens.fingerprint(camera); }

    /** Registro que este backend no hizo (p. ej. tras reiniciarse) y no tocó porque alguien miraba. */
    private static final String UNKNOWN = "?";

    /** La ruta está entregando video (alguien la está mirando o la cámara está conectada). */
    private boolean watching(String path) {
        Hit state = liveState(path);
        return state.code() == 200 && READY.matcher(state.body()).find();
    }

    /** Lo que se registra al final: la conversión a H.264 si corresponde; si no, la cámara directo y solo mientras alguien mira. */
    String finalJson(String camera, String codec, boolean forceTranscode) {
        return forceTranscode || (transcodeH265 && isH265(codec)) ? transcodeJson(camera) : sourceJson(camera, true);
    }

    /** Códec original de cada ruta probada (la conversión muestra H.264 en MediaMTX): evita reconectar si ya se sabe. */
    private final Map<String, String> knownCodec = new ConcurrentHashMap<>();

    /**
     * Prueba en la ruta dada. Si ya está registrada y alguien la está mirando, no abre otra conexión: responde con lo que ya se
     * sabe. Si no, la registra "a pedido" (MediaMTX se conecta recién cuando se pide el video y suelta la cámara 10 s después
     * de que nadie mira), pide el video para que se conecte, espera a que esté lista y lee el códec. Con H.265 y la
     * conversión encendida, la pasa a FFmpeg.
     */
    private ServerProbe probe(String path, String camera, boolean forceTranscode, String host, int port) {
        long start = System.nanoTime();
        String url = browserUrl(path);
        cameraByPath.put(path, camera);
        Hit current = liveState(path);
        boolean ready = current.code() == 200 && READY.matcher(current.body()).find();
        if (ready) {
            // Ya entrega video con estos mismos datos (la ruta depende de la clave): se reutiliza esa conexión. El mensaje dice
            // lo que MediaMTX está haciendo de verdad (convierte o directo), no lo que debería hacer.
            Boolean converting = remoteTranscodes(path);
            java.util.regex.Matcher t = TRACKS.matcher(current.body());
            String seen = t.find() ? t.group(1) : null;
            // Convirtiendo, MediaMTX muestra H.264 (la salida de FFmpeg): el códec real de la cámara es el que ya se conocía.
            String codec = Boolean.TRUE.equals(converting) && knownCodec.get(path) != null ? knownCodec.get(path) : seen;
            if (codec != null && !Boolean.TRUE.equals(converting)) knownCodec.put(path, codec);
            boolean needsH264 = forceTranscode || (transcodeH265 && isH265(codec));
            if (needsH264 && Boolean.FALSE.equals(converting)) {
                if (switchToH264IfNeeded(path, current.body(), "probar_conexion") || forceTranscode && registerJson(path, transcodeJson(camera))) {
                    LOG.infof("[SGI-VIDEO] video_probe_reused path=%s codec=%s accion=paso_a_h264", path, codec);
                    return new ServerProbe(RtspProbe.Outcome.OK, "El servidor de video ya estaba conectado a la cámara en " + codec
                        + " sin convertir: se pasó a H.264 para que la vean todos los navegadores (quien estaba mirando tiene un corte de unos segundos).",
                        codec, elapsed(start), path, url);
                }
                LOG.warnf("[SGI-VIDEO] video_probe_reused path=%s codec=%s accion=no_se_pudo_pasar_a_h264", path, codec);
                return new ServerProbe(RtspProbe.Outcome.OK, "El servidor de video está conectado a la cámara en " + codec
                    + " sin convertir y no se pudo pasar a H.264: algunos navegadores no la verán.", codec, elapsed(start), path, url);
            }
            LOG.infof("[SGI-VIDEO] video_probe_reused path=%s codec=%s convierte=%s", path, codec, converting);
            String what = Boolean.TRUE.equals(converting)
                ? "El servidor de video ya está conectado a la cámara (" + (codec == null ? "códec desconocido" : codec) + ": se convierte a H.264 para el navegador)."
                : "El servidor de video ya está conectado a la cámara" + (codec == null ? "" : " (" + codec + ")") + ".";
            return new ServerProbe(RtspProbe.Outcome.OK, what + " Se reutilizó la conexión abierta.", codec, elapsed(start), path, url);
        }
        String error = registerError(path, sourceJson(camera, true));
        if (error != null) return new ServerProbe(RtspProbe.Outcome.PROTOCOL_ERROR, error, null, elapsed(start), null, null);
        String hls = checkBase(publicUrl.get().replaceAll("/+$", "") + "/" + path + "/index.m3u8?token=" + tokens.sign(path));
        // Además pide el video, como lo haría el navegador; su respuesta va al detalle si la cámara no conecta.
        java.util.concurrent.CompletableFuture<Hit> wake = http.sendAsync(HttpRequest.newBuilder(URI.create(hls)).timeout(PROBE_WAIT).GET().build(),
                HttpResponse.BodyHandlers.ofString())
            .thenApply(r -> new Hit(r.statusCode(), r.statusCode() / 100 == 2 ? null : shorten(r.body()), null))
            .exceptionally(e -> new Hit(0, null, describe(e)));
        Instant until = Instant.now().plus(PROBE_WAIT);
        Hit state = null;
        while (Instant.now().isBefore(until)) {
            state = liveState(path);
            if (state.code() == 200 && READY.matcher(state.body()).find()) {
                java.util.regex.Matcher t = TRACKS.matcher(state.body());
                String codec = t.find() ? t.group(1) : null;
                LOG.infof("[SGI-VIDEO] video_probe_ok path=%s codec=%s", path, codec);
                knownCodec.put(path, codec == null ? "" : codec);
                String finalJson = finalJson(camera, codec, forceTranscode);
                if (!finalJson.equals(sourceJson(camera, true))) {
                    // El navegador no reproduce H.265: la ruta pasa a la versión que recodifica FFmpeg (misma ruta y token).
                    String err = registerError(path, finalJson);
                    if (err != null) {
                        LOG.warnf("[SGI-VIDEO] video_probe_transcode_failed path=%s %s", path, err);
                        registeredAs.put(path, sourceJson(camera, true));
                        return new ServerProbe(RtspProbe.Outcome.OK, "El servidor de video se conectó a la cámara (" + codec
                            + "), pero no se pudo activar la conversión a H.264: algunos navegadores no la verán. " + err, codec, elapsed(start), path, url);
                    }
                }
                registeredAs.put(path, finalJson);
                return new ServerProbe(RtspProbe.Outcome.OK, okMessage(codec, forceTranscode), codec, elapsed(start), path, url);
            }
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        knownCodec.remove(path);
        registeredAs.remove(path);
        Hit video = wake.getNow(new Hit(0, null, "sin respuesta todavía"));
        String detail = "No se pudo conectar: revise IP, usuario, clave o ruta (el servidor de video no recibió imagen en " + PROBE_WAIT.toSeconds()
            + " s). Estado en MediaMTX: " + stateText(state) + ". Pedido del video: " + video.text(hls) + "."
            + " Si los datos son correctos, MediaMTX no llega a " + host + ":" + port + " (pedir a Infra: docker logs sgi-mediamtx).";
        LOG.infof("[SGI-VIDEO] video_probe_timeout path=%s host=%s state=%s video=%s", path, host, state == null ? null : state.body(), video);
        return new ServerProbe(RtspProbe.Outcome.TIMEOUT, detail, null, elapsed(start), path, null);
    }

    /** Lo último que este backend registró en cada ruta probada (para saber si se puede reutilizar sin tocarla). */
    private final Map<String, String> registeredAs = new ConcurrentHashMap<>();

    private boolean registeredAs(String path, String json) { return json.equals(registeredAs.get(path)); }

    private String okMessage(String codec, boolean forceTranscode) {
        return forceTranscode || (transcodeH265 && isH265(codec))
            ? "El servidor de video se conectó a la cámara (" + (codec == null || codec.isBlank() ? "códec desconocido" : codec) + ": se convierte a H.264 para el navegador)."
            : "El servidor de video se conectó a la cámara.";
    }

    private static final java.util.regex.Pattern BYTES =java.util.regex.Pattern.compile("\"bytesReceived\"\\s*:\\s*(\\d+)");
    private static final java.util.regex.Pattern SOURCE = java.util.regex.Pattern.compile("\"source\"\\s*:\\s*(null|\\{[^}]*\\})");

    /** Lo que MediaMTX dice de la ruta: si está lista, cuántos bytes recibió de la cámara y qué fuente tiene. */
    private static String stateText(Hit state) {
        if (state == null) return "no se consultó";
        if (state.code() != 200) return "la API " + state.text("/v3/paths/get") .replaceFirst("\\.$", "");
        String body = state.body();
        java.util.regex.Matcher b = BYTES.matcher(body), s = SOURCE.matcher(body);
        return "lista=" + READY.matcher(body).find() + ", bytes recibidos de la cámara=" + (b.find() ? b.group(1) : "?")
            + ", fuente=" + (s.find() ? s.group(1) : "?");
    }

    private static long elapsed(long start) { return (System.nanoTime() - start) / 1_000_000; }

    /** Estado en vivo de la ruta (GET /v3/paths/get/…), con el cuerpo completo si respondió 200. */
    private Hit liveState(String path) {
        try {
            HttpResponse<String> r = http.send(request("/v3/paths/get/" + path).GET().build(), HttpResponse.BodyHandlers.ofString());
            return new Hit(r.statusCode(), r.statusCode() == 200 ? r.body() : shorten(r.body()), null);
        } catch (Exception e) {
            LOG.debugf("[SGI-VIDEO] video_probe_state_unreachable path=%s %s", path, e.getMessage());
            return new Hit(0, null, describe(e));
        }
    }

    /**
     * Pide la lista HLS hasta que MediaMTX la entrega: con sourceOnDemand, la primera vez se conecta a la cámara y puede tardar.
     * 200 solo si llegó una lista de video; si no, el último código (0 si no hubo respuesta).
     */
    private Hit playlist(String url) {
        Hit last = new Hit(0, null, "sin intentos");
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() == 200 && r.body().contains("#EXTM3U")) return new Hit(200, null, null);
                last = new Hit(r.statusCode(), shorten(r.body()), null);
                if (r.statusCode() == 401) return last;
            } catch (Exception e) {
                LOG.warnf("[SGI-VIDEO] video_diagnostic_playlist_unreachable %s", e.getMessage());
                last = new Hit(0, null, describe(e));
            }
            try { Thread.sleep(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return last; }
        }
        return last;
    }

    /** Respuesta de un GET: código (0 si no hubo respuesta) y, si no fue 2xx, qué respondió o por qué no hubo respuesta. */
    record Hit(int code, String body, String error) {
        /** "Respondió 401: …" o "Sin respuesta de <url>: <error>", para mostrar en el diagnóstico. */
        String text(String url) {
            if (code == 0) return "Sin respuesta de " + hideToken(url) + (error == null ? "" : ": " + error) + ".";
            return "Respondió " + code + (body == null || body.isBlank() ? "." : ": " + body);
        }
    }

    private Hit hit(String url, Duration timeout) {
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(timeout).GET().build(), HttpResponse.BodyHandlers.ofString());
            return new Hit(r.statusCode(), r.statusCode() / 100 == 2 ? null : shorten(r.body()), null);
        } catch (Exception e) {
            LOG.warnf("[SGI-VIDEO] video_gateway_check_unreachable url=%s %s", hideToken(url), e.getMessage());
            return new Hit(0, null, describe(e));
        }
    }

    private Hit hit(String url) { return hit(url, Duration.ofSeconds(4)); }

    static String hideToken(String url) { return url == null ? null : url.replaceAll("token=[^&]*", "token=…"); }

    /** Respuesta del servidor recortada y sin claves de cámara (rtsp://usuario:clave@…). */
    static String shorten(String body) {
        if (body == null) return null;
        String b = body.replaceAll("rtsp://[^@/\\s\"]*@", "rtsp://***@").replaceAll("\\s+", " ").strip();
        return b.length() > 300 ? b.substring(0, 300) + "…" : b;
    }

    static String describe(Throwable e) {
        Throwable c = e;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        return c.getClass().getSimpleName() + (c.getMessage() == null ? "" : ": " + c.getMessage());
    }

    /**
     * Si MediaMTX le preguntó a este backend por la ruta (VideoAuthLog) y qué se le respondió. Sin consultas, MediaMTX no está
     * llamando a /api/internal/video/auth de este backend: la causa está en su configuración (authHTTPAddress) o su red.
     */
    private Step authStep(String path, Instant from, boolean mediamtxDenied) {
        java.util.List<VideoAuthLog.Entry> calls = authLog.since(path, from.minusSeconds(1));
        String name = "MediaMTX le pregunta a Comando si deja ver el video";
        if (calls.isEmpty()) return new Step(name, false, "Comando no recibió ninguna consulta de MediaMTX para " + path
            + " → MediaMTX no está llamando a /api/internal/video/auth de este backend: revisar authMethod y authHTTPAddress en mediamtx.yml,"
            + " que MediaMTX se haya recargado y que su contenedor llegue a esa dirección (con varios backends, pudo ir a otro).");
        long allowed = calls.stream().filter(VideoAuthLog.Entry::allowed).count();
        long withToken = calls.stream().filter(VideoAuthLog.Entry::withToken).count();
        String reasons = calls.stream().filter(e -> !e.allowed()).map(VideoAuthLog.Entry::reason).distinct()
            .collect(java.util.stream.Collectors.joining(", "));
        VideoAuthLog.Entry last = calls.get(0);
        String detail = "Comando recibió " + calls.size() + " consultas de MediaMTX (ip " + last.ip() + ", " + last.protocol() + "): "
            + allowed + " aceptadas, " + (calls.size() - allowed) + " rechazadas" + (reasons.isEmpty() ? "" : " (" + reasons + ")")
            + ", " + withToken + " traían token.";
        if (allowed > 0) return new Step(name, !mediamtxDenied, detail + (mediamtxDenied
            ? " → Comando aceptó el token, pero MediaMTX igual respondió 401: no está tomando la respuesta de Comando (revisar el log de MediaMTX)." : ""));
        if (withToken == 0) return new Step(name, false, detail + " → Ninguna traía el token: el proxy (nginx) le quita ?token=… a la URL antes de MediaMTX.");
        return new Step(name, false, detail);
    }

    private static final java.util.regex.Pattern AUTH_METHOD = java.util.regex.Pattern.compile("\"authMethod\"\\s*:\\s*\"([^\"]*)\"");
    private static final java.util.regex.Pattern AUTH_ADDRESS = java.util.regex.Pattern.compile("\"authHTTPAddress\"\\s*:\\s*\"([^\"]*)\"");
    private static final java.util.regex.Pattern AUTH_EXCLUDE = java.util.regex.Pattern.compile("\"authHTTPExclude\"\\s*:\\s*(\\[[^\\]]*\\])");

    /** Cómo pide permiso MediaMTX (authMethod, authHTTPAddress), leído de su API. Solo esos datos: nada de usuarios ni claves. */
    private Step mediamtxAuthConfig() {
        String name = "MediaMTX pide permiso a Comando (configuración)";
        Hit r;
        try {
            HttpResponse<String> res = http.send(request("/v3/config/global/get").GET().build(), HttpResponse.BodyHandlers.ofString());
            r = new Hit(res.statusCode(), res.body(), null);
        } catch (Exception e) {
            r = new Hit(0, null, describe(e));
        }
        if (r.code() != 200) return new Step(name, true, "No se pudo leer la configuración de MediaMTX (no impide el video): "
            + new Hit(r.code(), shorten(r.body()), r.error()).text(apiUrl.get() + "/v3/config/global/get"));
        java.util.regex.Matcher m = AUTH_METHOD.matcher(r.body()), a = AUTH_ADDRESS.matcher(r.body()), x = AUTH_EXCLUDE.matcher(r.body());
        String method = m.find() ? m.group(1) : "?", address = a.find() ? a.group(1) : "?", exclude = x.find() ? x.group(1) : "?";
        boolean ok = "http".equals(method) && address.endsWith("/api/internal/video/auth");
        return new Step(name, ok, "authMethod=" + method + " · authHTTPAddress=" + address + " · authHTTPExclude=" + exclude
            + (ok ? "." : "http".equals(method) ? " → authHTTPAddress debe terminar en /api/internal/video/auth (la de este Comando)."
                : " → authMethod debe ser http para que MediaMTX le pregunte a Comando."));
    }

    /** A qué servidor de video habla este backend (sin claves): va en cada diagnóstico para descartar configuración. */
    public String serverInfo() {
        if (!configured()) return "Sin servidor de video configurado.";
        return "API " + apiUrl.get() + " (usuario " + apiUser.filter(v -> !v.isBlank()).orElse("—") + ") · video " + publicUrl.get()
            + checkUrl.filter(v -> !v.isBlank()).map(v -> " · el backend pide el video en " + v).orElse("") + " · protocolo " + protocol;
    }

    /**
     * Al eliminar la cámara: borra su ruta antigua (live/cam-<id>). La ruta por cámara se deja, porque otro componente puede
     * usar la misma cámara; sin nadie mirando no tiene conexión abierta con ella.
     */
    public void remove(UUID cameraId) {
        delete(path(cameraId));
    }

    private void delete(String path) {
        if (!configured()) return;
        try {
            int status = http.send(request("/v3/config/paths/delete/" + path).DELETE().build(), HttpResponse.BodyHandlers.discarding()).statusCode();
            // Cualquier borrado de una ruta deja huella: si alguien estaba mirando, aquí se ve que lo cortó Comando.
            if (!path.startsWith("live/diagnostico-")) LOG.infof("[SGI-VIDEO] video_ruta_borrada path=%s respuesta=%d", path, status);
        } catch (Exception e) {
            LOG.warnf("[SGI-VIDEO] video_gateway_remove_failed path=%s %s", path, e.getMessage());
        }
    }

    /** Ruta antigua, una por componente (antes de compartir por cámara): solo para borrarla. */
    static String path(UUID cameraId) { return "live/cam-" + cameraId; }

    /** rtsp://usuario:clave@host:puerto/ruta (la de la marca, ver CameraBrand), con usuario y clave codificados (pueden tener @, : o /). */
    static String rtspSource(String host, int port, String path, String user, String password) {
        String h = host.contains(":") ? "[" + host + "]" : host;
        return "rtsp://" + enc(user) + ":" + enc(password) + "@" + h + ":" + port + path;
    }

    private static String enc(String v) { return URLEncoder.encode(v, StandardCharsets.UTF_8).replace("+", "%20"); }

    /** Convertir H.265 → H.264 con FFmpeg en MediaMTX (sgi.video.transcode-h265); apagado = solo se avisa que H.265 no se ve. */
    @ConfigProperty(name = "sgi.video.transcode-h265", defaultValue = "false") boolean transcodeH265;

    /** La cámara se recodifica: marcada TRANSCODE, o se detectó H.265 y la conversión está encendida. */
    boolean transcodes(SecElecComponent c) {
        return c.streamingMode == SecElecComponent.StreamingMode.TRANSCODE || (transcodeH265 && isH265(c.detectedCodec));
    }

    static boolean isH265(String codec) {
        if (codec == null) return false;
        String v = codec.toUpperCase().replace(".", "");
        return v.contains("H265") || v.contains("HEVC");
    }

    /** sourceOnDemand: solo se conecta a la cámara mientras alguien mira. */
    static String sourceJson(String source, boolean onDemand) {
        return "{\"source\":\"" + json(source) + "\",\"sourceOnDemand\":" + onDemand + ",\"rtspTransport\":\"tcp\"}";
    }

    /**
     * Ruta que publica FFmpeg (source: publisher por defecto): MediaMTX lo arranca cuando alguien pide el video y lo detiene
     * 10 s después de que nadie mira. FFmpeg lee la cámara por TCP, quita el audio, recodifica a H.264 de baja latencia y lo
     * publica en esta misma ruta por el RTSP interno ($RTSP_PORT y $MTX_PATH los pone MediaMTX). VideoAuthResource deja
     * publicar solo desde 127.0.0.1.
     */
    static String transcodeJson(String cameraUrl) {
        String cmd = "ffmpeg -hide_banner -loglevel error -rtsp_transport tcp -i '" + cameraUrl.replace("'", "%27") + "'"
            + " -an -c:v libx264 -preset veryfast -tune zerolatency -profile:v main -pix_fmt yuv420p -g 50"
            + " -f rtsp -rtsp_transport tcp rtsp://127.0.0.1:$RTSP_PORT/$MTX_PATH";
        // sourceOnDemand:false explícito: pathDefaults de MediaMTX lo trae en yes y con source publisher responde 400.
        return "{\"source\":\"publisher\",\"sourceOnDemand\":false,\"runOnDemand\":\"" + json(cmd) + "\",\"runOnDemandRestart\":true,\"runOnDemandStartTimeout\":\"20s\",\"runOnDemandCloseAfter\":\"10s\"}";
    }

    private static String json(String v) { return v.replace("\\", "\\\\").replace("\"", "\\\""); }

    private boolean registerJson(String path, String json) { return registerError(path, json) == null; }

    /** null si MediaMTX aceptó la ruta; si no, qué respondió (código y mensaje, sin la clave de la cámara) para mostrarlo. */
    private String registerError(String path, String json) {
        try {
            HttpResponse<String> r = http.send(request("/v3/config/paths/replace/" + path)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 == 2) return null;
            String body = r.body() == null ? "" : r.body().replaceAll("rtsp://[^@/\\s\"]*@", "rtsp://***@").strip();
            if (body.length() > 300) body = body.substring(0, 300) + "…";
            LOG.warnf("[SGI-VIDEO] video_gateway_register_failed path=%s status=%d body=%s", path, r.statusCode(), body);
            return "La API de MediaMTX (" + apiUrl.get() + ") respondió " + r.statusCode()
                + (r.statusCode() == 401 ? " (rechazó el usuario y la clave de Comando)" : r.statusCode() == 403 ? " (nginx no deja entrar desde esta IP)" : "")
                + (body.isEmpty() ? "." : ": " + body);
        } catch (Exception e) {
            LOG.warnf("[SGI-VIDEO] video_gateway_unreachable path=%s %s", path, e.getMessage());
            return "La API de MediaMTX (" + apiUrl.get() + ") no respondió: " + describe(e) + ".";
        }
    }

    /** true/false si MediaMTX tiene o no la ruta; null si no respondió (la consola muestra "Sin señal" y se reintenta en la próxima vista). */
    private Boolean exists(String path) {
        try {
            int status = http.send(request("/v3/config/paths/get/" + path).GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 200) return true;
            if (status == 404) {
                LOG.infof("[SGI-VIDEO] video_gateway_reregister path=%s (MediaMTX ya no la tenía)", path);
                return false;
            }
            LOG.warnf("[SGI-VIDEO] video_gateway_check_failed path=%s status=%d", path, status);
        } catch (Exception e) {
            LOG.warnf("[SGI-VIDEO] video_gateway_unreachable path=%s %s", path, e.getMessage());
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
