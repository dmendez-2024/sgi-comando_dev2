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

    /** Probar la misma cámara varias veces (o dos personas a la vez) cae en la misma ruta: una sola conexión con la cámara. */
    @Test void previewPathIsFixedPerCamera() {
        String a = VideoGateway.rtspSource("10.150.12.204", 554, "/Streaming/Channels/101", "admin", "Clave1");
        String p = VideoGateway.previewPath(a);
        assertTrue(p.matches("live/prueba-[0-9a-f]{24}"), p);
        assertEquals(p, VideoGateway.previewPath(a), "misma cámara, misma ruta");
        assertNotEquals(p, VideoGateway.previewPath(VideoGateway.rtspSource("10.150.12.204", 554, "/Streaming/Channels/102", "admin", "Clave1")), "otro stream");
        assertNotEquals(p, VideoGateway.previewPath(VideoGateway.rtspSource("10.150.12.204", 554, "/Streaming/Channels/101", "admin", "Otra")), "otra clave");
        assertFalse(p.contains("Clave1"), "la clave no aparece en la ruta");
    }

    @Test void onePathPerCamera() {
        UUID id = UUID.fromString("64a021bc-bb13-486f-8349-8743626d0696");
        assertEquals("live/cam-64a021bc-bb13-486f-8349-8743626d0696", VideoGateway.path(id));
    }
}
