package com.cajamarca.sgi.comando.visint;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class MockVisintAdapterTest {
    @Inject VisintClient client;
    @Inject MockVisintAdapter mock;

    /** Foto del agente con huella evidenceSha contra fotos estándar con esas huellas (por defecto una, "a…a"). */
    static VisintPort.ReviewRequest request(String evidenceSha, String... standardShas) {
        List<VisintPort.StandardRef> std = new ArrayList<>();
        String[] shas = standardShas.length == 0 ? new String[]{"a".repeat(64)} : standardShas;
        for (int i = 0; i < shas.length; i++) std.add(new VisintPort.StandardRef(UUID.randomUUID(), i + 1, "b", "s" + i, shas[i], "image/jpeg"));
        return new VisintPort.ReviewRequest(UUID.randomUUID(), 1, UUID.randomUUID(), "PATROL_CHECKPOINT", UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            new VisintPort.EvidenceRef(UUID.randomUUID(), "b", "k", evidenceSha, "image/jpeg", Instant.now(), null, null), std, UUID.randomUUID(), 0.8);
    }

    @Test void photoIdenticalToAStandardPassesAndPointsToIt() {
        var req = request("a".repeat(64), "b".repeat(64), "a".repeat(64));
        var r = client.review(req);
        assertEquals("PASS", r.result());
        assertEquals(req.standards().get(1).imageId(), r.matchedStandardImageId());
        assertEquals("OK", r.reasonCode());
        assertTrue(r.findings().contains("simulado"));
        assertTrue(client.simulated());
    }

    @Test void deterministicVerdict() {
        var req = request("b".repeat(64), "c".repeat(64));
        String first = client.review(req).result();
        assertEquals(first, client.review(req).result());
        assertTrue(Set.of("PASS", "FAIL", "ERROR").contains(first));
    }

    @Test void canSimulateOutageAndForcedResult() {
        mock.failNextCalls(1);
        assertThrows(VisintUnavailableException.class, () -> client.review(request("a".repeat(64))));
        mock.forceNextResult("ERROR");
        assertEquals("ERROR", client.review(request("a".repeat(64))).result());
        assertEquals("PASS", client.review(request("a".repeat(64))).result());
    }
}
