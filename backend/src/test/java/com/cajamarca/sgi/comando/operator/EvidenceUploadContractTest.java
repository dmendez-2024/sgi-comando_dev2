package com.cajamarca.sgi.comando.operator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EvidenceUploadContractTest {
    final ObjectMapper m = new ObjectMapper();
    final Instant now = Instant.parse("2026-09-29T22:00:00Z");
    final String sha = "a".repeat(64);

    ObjectNode meta(int n) {
        ObjectNode o = m.createObjectNode().put("uploadBatchId", UUID.randomUUID().toString()).put("eventId", UUID.randomUUID().toString())
            .put("assignmentId", UUID.randomUUID().toString()).put("targetType", "PATROL_CHECKPOINT").put("targetId", UUID.randomUUID().toString());
        ArrayNode items = o.putArray("items");
        for (int i = 0; i < n; i++) items.addObject().put("clientEvidenceId", UUID.randomUUID().toString()).put("capturedAt", now.toString())
            .put("latitude", -2.17).put("longitude", -79.92).put("accuracyM", 8).put("source", "CAMERA").put("sha256", sha);
        return o;
    }
    List<String> names(ObjectNode o) { List<String> r = new ArrayList<>(); o.path("items").forEach(i -> r.add(i.path("clientEvidenceId").asText() + ".jpg")); return r; }
    Map<UUID,Integer> match(ObjectNode o, List<String> files) { return EvidenceUploadContract.matchFiles(EvidenceUploadContract.parse(o.toString(), m), files, 5, now); }

    @Test void acceptsThreePhotosInAnyOrder() {
        ObjectNode o = meta(3); List<String> files = names(o); Collections.reverse(files);
        var md = EvidenceUploadContract.parse(o.toString(), m);
        Map<UUID,Integer> idx = EvidenceUploadContract.matchFiles(md, files, 5, now);
        assertEquals(3, idx.size());
        assertEquals(2, idx.get(md.items().get(0).clientEvidenceId()));
        assertEquals(-2.17, md.items().get(0).latitude());
    }
    @Test void rejectsMissingOrUnknownFile() {
        ObjectNode o = meta(2);
        assertThrows(BadRequestException.class, () -> match(o, names(o).subList(0, 1)));
        List<String> other = new ArrayList<>(names(o)); other.set(1, UUID.randomUUID() + ".jpg");
        assertThrows(BadRequestException.class, () -> match(o, other));
    }
    @Test void rejectsTooManyAndDuplicateIds() {
        ObjectNode six = meta(6);
        assertThrows(BadRequestException.class, () -> match(six, names(six)));
        ObjectNode dup = meta(2);
        ((ObjectNode) dup.path("items").get(1)).put("clientEvidenceId", dup.path("items").get(0).path("clientEvidenceId").asText());
        assertThrows(BadRequestException.class, () -> match(dup, names(dup)));
    }
    @Test void rejectsFutureCaptureBadSourceBadSha() {
        for (String[] kv : new String[][]{{"capturedAt", now.plusSeconds(600).toString()}, {"source", "SCREENSHOT"}, {"sha256", "XYZ"}}) {
            ObjectNode o = meta(1); ((ObjectNode) o.path("items").get(0)).put(kv[0], kv[1]);
            assertThrows(BadRequestException.class, () -> match(o, names(o)), kv[0]);
        }
    }
    @Test void rejectsUnsupportedTarget() {
        ObjectNode t = meta(1).put("targetType", "RELIEF_PURPOSE");
        assertThrows(BadRequestException.class, () -> match(t, names(t)));
    }
    @Test void rejectsGarbageOrMissingJson() {
        assertThrows(BadRequestException.class, () -> EvidenceUploadContract.parse("{no json", m));
        assertThrows(BadRequestException.class, () -> EvidenceUploadContract.parse(null, m));
        assertThrows(BadRequestException.class, () -> EvidenceUploadContract.parse("{\"eventId\":\"x\"}", m));
    }
}
