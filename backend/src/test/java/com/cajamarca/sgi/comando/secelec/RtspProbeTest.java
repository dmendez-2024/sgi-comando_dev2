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
            RtspProbe.Result r = probe.describe("127.0.0.1", cam.port(), 2, "admin", "Clave123");
            assertEquals(Outcome.OK, r.outcome(), r.message());
            assertEquals("H.265", r.codec());
        }
    }

    @Test void wrongPassword() throws Exception {
        try (FakeRtspCamera cam = new FakeRtspCamera("admin", "Clave123", 4, "H264")) {
            assertEquals(Outcome.AUTH_FAILED, probe.describe("127.0.0.1", cam.port(), 1, "admin", "otra").outcome());
        }
    }

    @Test void missingChannel() throws Exception {
        try (FakeRtspCamera cam = new FakeRtspCamera("admin", "Clave123", 2, "H264")) {
            RtspProbe.Result r = probe.describe("127.0.0.1", cam.port(), 5, "admin", "Clave123");
            assertEquals(Outcome.NOT_FOUND, r.outcome());
            assertTrue(r.message().contains("canal 5"));
        }
    }

    @Test void closedPortIsUnreachable() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) { port = s.getLocalPort(); }
        assertEquals(Outcome.UNREACHABLE, probe.describe("127.0.0.1", port, 1, "admin", "x").outcome());
    }

    @Test void silentServerTimesOut() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            assertEquals(Outcome.TIMEOUT, probe.describe("127.0.0.1", s.getLocalPort(), 1, "admin", "x").outcome());
        }
    }

    @Test void digestWithQopAndCodecParsing() {
        String h = RtspProbe.authorization("Digest realm=\"r\", nonce=\"n\", qop=\"auth\", opaque=\"o\"", "u", "p", "rtsp://h:554/x");
        assertTrue(h.contains("qop=auth") && h.contains("cnonce=") && h.contains("opaque=\"o\""), h);
        assertTrue(RtspProbe.authorization("Basic realm=\"r\"", "u", "p", "x").startsWith("Basic "));
        assertEquals("H.264", RtspProbe.codec("m=audio 0 RTP/AVP 8\na=rtpmap:8 PCMA/8000\nm=video 0 RTP/AVP 96\na=rtpmap:96 H264/90000\n"));
        assertNull(RtspProbe.codec("m=audio 0 RTP/AVP 8\na=rtpmap:8 PCMA/8000\n"));
    }
}
