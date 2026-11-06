package com.cajamarca.sgi.comando.secelec;

import com.cajamarca.sgi.comando.secelec.RtspProbe.Outcome;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** "Probar conexión": DESCRIBE RTSP con Digest contra una cámara simulada. */
class RtspProbeTest {
    final RtspProbe probe = new RtspProbe(1000, 2000);

    @Test void okWithDigestReportsCodec() throws Exception {
        try (FakeRtspCamera cam = new FakeRtspCamera("admin", "Clave123", 4, "H265")) {
            RtspProbe.Result r = probe.describe("127.0.0.1", cam.port(), "/Streaming/Channels/201", "admin", "Clave123");
            assertEquals(Outcome.OK, r.outcome(), r.message());
            assertEquals("H.265", r.codec());
        }
    }

    @Test void wrongPassword() throws Exception {
        try (FakeRtspCamera cam = new FakeRtspCamera("admin", "Clave123", 4, "H264")) {
            assertEquals(Outcome.AUTH_FAILED, probe.describe("127.0.0.1", cam.port(), "/Streaming/Channels/101", "admin", "otra").outcome());
        }
    }

    @Test void missingChannel() throws Exception {
        try (FakeRtspCamera cam = new FakeRtspCamera("admin", "Clave123", 2, "H264")) {
            RtspProbe.Result r = probe.describe("127.0.0.1", cam.port(), "/Streaming/Channels/501", "admin", "Clave123");
            assertEquals(Outcome.NOT_FOUND, r.outcome());
            assertTrue(r.message().contains("/Streaming/Channels/501"), r.message());
        }
    }

    @Test void closedPortIsUnreachable() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) { port = s.getLocalPort(); }
        assertEquals(Outcome.UNREACHABLE, probe.describe("127.0.0.1", port, "/stream1", "admin", "x").outcome());
    }

    @Test void silentServerTimesOut() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            assertEquals(Outcome.TIMEOUT, probe.describe("127.0.0.1", s.getLocalPort(), "/stream1", "admin", "x").outcome());
        }
    }

    /** Otra marca: la ruta de Dahua para el canal 2. */
    @Test void otherBrandPath() throws Exception {
        try (FakeRtspCamera cam = new FakeRtspCamera("admin", "Clave123", "H264", ".*/cam/realmonitor\\?channel=2&subtype=0$")) {
            String path = CameraBrand.DAHUA.path(2, null);
            assertEquals("/cam/realmonitor?channel=2&subtype=0", path);
            assertEquals(Outcome.OK, probe.describe("127.0.0.1", cam.port(), path, "admin", "Clave123").outcome());
            assertEquals(Outcome.NOT_FOUND, probe.describe("127.0.0.1", cam.port(), CameraBrand.HIKVISION.path(2, null), "admin", "Clave123").outcome());
        }
    }

    @Test void brandPaths() {
        assertEquals("/Streaming/Channels/301", CameraBrand.HIKVISION.path(3, null));
        assertEquals("/unicast/c3/s0/live", CameraBrand.UNIVIEW.path(3, null));
        assertEquals("/h264Preview_03_main", CameraBrand.REOLINK.path(3, null));
        assertEquals("/axis-media/media.amp?camera=3", CameraBrand.AXIS.path(3, null));
        assertEquals("/stream1", CameraBrand.TPLINK.path(3, null));
        assertEquals("/live/ch0", CameraBrand.OTHER.path(3, "/live/ch0"));
    }

    @Test void digestWithQopAndCodecParsing() {
        String h = RtspProbe.authorization("Digest realm=\"r\", nonce=\"n\", qop=\"auth\", opaque=\"o\"", "u", "p", "rtsp://h:554/x");
        assertTrue(h.contains("qop=auth") && h.contains("cnonce=") && h.contains("opaque=\"o\""), h);
        assertTrue(RtspProbe.authorization("Basic realm=\"r\"", "u", "p", "x").startsWith("Basic "));
        assertEquals("H.264", RtspProbe.codec("m=audio 0 RTP/AVP 8\na=rtpmap:8 PCMA/8000\nm=video 0 RTP/AVP 96\na=rtpmap:96 H264/90000\n"));
        assertNull(RtspProbe.codec("m=audio 0 RTP/AVP 8\na=rtpmap:8 PCMA/8000\n"));
    }
}
