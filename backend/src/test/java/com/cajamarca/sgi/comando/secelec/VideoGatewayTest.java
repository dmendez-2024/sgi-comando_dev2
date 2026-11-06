package com.cajamarca.sgi.comando.secelec;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Test void onePathPerCamera() {
        UUID id = UUID.fromString("64a021bc-bb13-486f-8349-8743626d0696");
        assertEquals("live/cam-64a021bc-bb13-486f-8349-8743626d0696", VideoGateway.path(id));
    }
}
