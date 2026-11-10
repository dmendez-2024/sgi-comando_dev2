package com.cajamarca.sgi.comando.secelec;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** URL RTSP que Comando registra en el servidor de video: rtsp://usuario:clave@IP:puerto + la ruta de la marca. */
class VideoGatewayTest {
    @Test void rtspSourceLikeHikvision() {
        assertEquals("rtsp://admin:Hik12345@192.168.1.10:554/Streaming/Channels/101",
            VideoGateway.rtspSource("192.168.1.10", 554, CameraBrand.HIKVISION.path(1, null), "admin", "Hik12345"));
        assertEquals("rtsp://admin:Hik12345@camara-hikvision-sim:554/Streaming/Channels/301",
            VideoGateway.rtspSource("camara-hikvision-sim", 554, CameraBrand.HIKVISION.path(3, null), "admin", "Hik12345"));
    }

    @Test void rtspSourceOtherBrands() {
        assertEquals("rtsp://admin:Clave1@10.0.0.5:554/cam/realmonitor?channel=2&subtype=0",
            VideoGateway.rtspSource("10.0.0.5", 554, CameraBrand.DAHUA.path(2, null), "admin", "Clave1"));
        assertEquals("rtsp://admin:Clave1@10.0.0.6:8554/live/ch0",
            VideoGateway.rtspSource("10.0.0.6", 8554, CameraBrand.OTHER.path(1, "/live/ch0"), "admin", "Clave1"));
    }

    @Test void credentialsWithSpecialCharactersAreEncoded() {
        assertEquals("rtsp://comando%40view:p%40ss%3Aw%2Fd%20x@[fe80::1]:8554/Streaming/Channels/201",
            VideoGateway.rtspSource("fe80::1", 8554, CameraBrand.HIKVISION.path(2, null), "comando@view", "p@ss:w/d x"));
    }

    /** H.265 → H.264: MediaMTX corre FFmpeg mientras alguien mira, que lee la cámara y publica H.264 en la misma ruta. */
    @Test void transcodeRunsFfmpegOnDemand() {
        String cam = VideoGateway.rtspSource("10.150.12.204", 554, CameraBrand.HIKVISION.path(1, null), "admin", "p$ss'w\"d");
        String json = VideoGateway.transcodeJson(cam);
        assertTrue(json.contains("\"runOnDemand\":\"ffmpeg "), json);
        assertTrue(json.contains("-i 'rtsp://admin:p%24ss%27w%22d@10.150.12.204:554/Streaming/Channels/101'"), "la clave va codificada: sin $, comillas ni espacios");
        assertTrue(json.contains("-c:v libx264"), json);
        assertTrue(json.contains("rtsp://127.0.0.1:$RTSP_PORT/$MTX_PATH"), "publica en su propia ruta por el RTSP interno");
        assertTrue(json.contains("\"runOnDemandCloseAfter\":\"10s\""), json);
        assertTrue(json.contains("\"source\":\"publisher\""), "la fuente es FFmpeg (publisher), no la cámara directo");
        assertTrue(json.contains("\"sourceOnDemand\":false"), "con publisher MediaMTX rechaza sourceOnDemand:true (viene de pathDefaults)");
    }

    @Test void whichCamerasAreConverted() {
        assertTrue(VideoGateway.isH265("H265"));
        assertTrue(VideoGateway.isH265("H.265"));
        assertTrue(VideoGateway.isH265("HEVC"));
        assertFalse(VideoGateway.isH265("H264"));
        assertFalse(VideoGateway.isH265(null));

        VideoGateway g = new VideoGateway();
        SecElecComponent c = new SecElecComponent();
        c.streamingMode = SecElecComponent.StreamingMode.COPY;
        c.detectedCodec = "H265";
        assertFalse(g.transcodes(c), "conversión apagada: H.265 queda tal cual");
        g.transcodeH265 = true;
        assertTrue(g.transcodes(c), "conversión encendida: H.265 detectado se convierte");
        c.detectedCodec = "H264";
        assertFalse(g.transcodes(c), "H.264 no se toca");
        c.streamingMode = SecElecComponent.StreamingMode.TRANSCODE;
        assertTrue(g.transcodes(c), "marcada Transcodificar: siempre");
    }

    static VideoGateway gateway(String key) {
        VideoToken t = new VideoToken();
        t.configuredKey = java.util.Optional.of(key);
        t.ttl = java.time.Duration.ofHours(2);
        t.init();
        VideoGateway g = new VideoGateway();
        g.tokens = t;
        return g;
    }

    /** Comando sabe si MediaMTX convierte la ruta (FFmpeg) o la entrega directa leyendo su configuración: así no promete conversión que no hay. */
    @Test void detectsWhetherMediamtxConverts() {
        String camera = VideoGateway.rtspSource("10.150.12.204", 554, "/Streaming/Channels/102", "admin", "Clave1");
        assertTrue(VideoGateway.RUN_FFMPEG.matcher(VideoGateway.transcodeJson(camera)).find(), "registro con FFmpeg = convierte");
        assertFalse(VideoGateway.RUN_FFMPEG.matcher(VideoGateway.sourceJson(camera, true)).find(), "registro directo = no convierte");
        // Lo que devuelve la API de MediaMTX trae espacios y otros campos: igual se reconoce.
        assertTrue(VideoGateway.RUN_FFMPEG.matcher("{\"name\":\"live/x\", \"runOnDemand\": \"ffmpeg -hide_banner ...\"}").find());
        assertFalse(VideoGateway.RUN_FFMPEG.matcher("{\"name\":\"live/x\",\"runOnDemand\":\"\"}").find());
    }

    /**
     * Una ruta por cámara: la consola, "Probar conexión" y dos componentes con la misma cámara caen en la misma ruta, así
     * comparten una sola conexión con la cámara (las cámaras aceptan pocas).
     */
    @Test void onePathPerCamera() {
        VideoGateway g = gateway("llave-de-prueba");
        String a = VideoGateway.rtspSource("10.150.12.204", 554, "/Streaming/Channels/101", "admin", "Clave1");
        String p = g.cameraPath(a);
        assertTrue(p.matches("live/cam-[0-9a-f]{24}"), p);
        assertEquals(p, g.cameraPath(a), "misma cámara, misma ruta (aunque esté en dos componentes)");
        assertNotEquals(p, g.cameraPath(VideoGateway.rtspSource("10.150.12.204", 554, "/Streaming/Channels/102", "admin", "Clave1")), "otro stream");
        assertNotEquals(p, g.cameraPath(VideoGateway.rtspSource("10.150.12.204", 554, "/Streaming/Channels/101", "admin", "Otra")),
            "otra clave: una prueba con datos errados no toca la ruta que funciona");
        assertFalse(p.contains("Clave1"), "la clave no aparece en la ruta");
        assertNotEquals(p, gateway("otra-llave").cameraPath(a), "el nombre depende de la llave de Comando: no se puede adivinar la clave");
    }

    /** La ruta antigua (una por componente) solo se usa para borrarla. */
    @Test void oldPathPerComponent() {
        UUID id = UUID.fromString("64a021bc-bb13-486f-8349-8743626d0696");
        assertEquals("live/cam-64a021bc-bb13-486f-8349-8743626d0696", VideoGateway.path(id));
    }
}
