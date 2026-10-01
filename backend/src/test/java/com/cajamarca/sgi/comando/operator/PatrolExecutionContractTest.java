package com.cajamarca.sgi.comando.operator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import jakarta.ws.rs.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PatrolExecutionContractTest {
    final UUID employee = UUID.randomUUID(), country = UUID.randomUUID();
    final Instant now = Instant.parse("2026-09-29T22:00:00Z");

    ObjectNode batch() {
        ObjectMapper m = new ObjectMapper();
        ObjectNode b = m.createObjectNode().put("batchId", UUID.randomUUID().toString()).put("correlationId", UUID.randomUUID().toString())
            .put("employeeId", employee.toString()).put("instanceCountryId", country.toString()).put("deviceId", "test").put("capturedAt", now.toString());
        ObjectNode e = b.putArray("events").addObject().put("type", "PATROL_CHECKPOINT_COMPLETED");
        for (String k : new String[]{"eventId", "assignmentId", "patrolRunId", "patrolId", "checkpointId"}) e.put(k, UUID.randomUUID().toString());
        e.put("executedAt", now.toString());
        e.putArray("evidenceIds").add(UUID.randomUUID().toString());
        return b;
    }
    ObjectNode ev(ObjectNode b) { return (ObjectNode) b.path("events").get(0); }

    @Test void accepts() { assertDoesNotThrow(() -> PatrolExecutionContract.validate(batch(), employee, country, now)); }
    @Test void acceptsNoPhotos() { var b = batch(); ev(b).putArray("evidenceIds"); assertDoesNotThrow(() -> PatrolExecutionContract.validate(b, employee, country, now)); }
    @Test void refusesDuplicatePhotoIds() {
        var b = batch(); String id = UUID.randomUUID().toString(); ev(b).putArray("evidenceIds").add(id).add(id);
        assertThrows(BadRequestException.class, () -> PatrolExecutionContract.validate(b, employee, country, now));
    }
    @Test void refusesFutureExecution() {
        var b = batch(); ev(b).put("executedAt", now.plusSeconds(900).toString());
        assertThrows(BadRequestException.class, () -> PatrolExecutionContract.validate(b, employee, country, now));
    }
    @Test void refusesLongObservation() {
        var b = batch(); ev(b).put("observation", "x".repeat(1001));
        assertThrows(BadRequestException.class, () -> PatrolExecutionContract.validate(b, employee, country, now));
    }
    @Test void refusesMissingCheckpoint() {
        var b = batch(); ev(b).remove("checkpointId");
        assertThrows(BadRequestException.class, () -> PatrolExecutionContract.validate(b, employee, country, now));
    }
    @Test void refusesOtherEmployee() { assertThrows(ForbiddenException.class, () -> PatrolExecutionContract.validate(batch(), UUID.randomUUID(), country, now)); }
}
