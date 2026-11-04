package com.cajamarca.sgi.comando.operator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.*;
import static org.hamcrest.Matchers.*;

/** Inicio y fin de patrulla enviados desde la cola sin conexión: hora real del teléfono y reintentos del FINISH. */
@QuarkusTest
class PatrolStartFinishTest {
    @Inject EntityManager em;
    final ObjectMapper m = new ObjectMapper();
    UUID assignment;
    Map<String,Object> ids;

    @BeforeEach void setUp() {
        assignment = ensureAgentAssignment(em);
        ids = publishPatrolWithCheckpoint(-2.17, -79.92);
    }

    ObjectNode event(String action, UUID execution, Instant executedAt) {
        ObjectNode e = m.createObjectNode().put("action", action).put("assignmentId", assignment.toString())
            .put("executionId", execution.toString()).put("patrolId", ids.get("patrolId").toString());
        if (executedAt != null) e.put("executedAt", executedAt.toString());
        if ("FINISH".equals(action)) e.putArray("checkpointResults").addObject()
            .put("checkpointId", ids.get("checkpointId").toString()).put("result", "CUMPLIDO")
            .put("validatedAt", (executedAt == null ? Instant.now() : executedAt).toString());
        return e;
    }

    ValidatableResponse send(ObjectNode e) {
        return as("agente").contentType(ContentType.JSON).body(e.toString()).post("/api/v1/operator/patrol-executions").then();
    }

    Instant column(UUID execution, String name) {
        Object v = em.createNativeQuery("select " + name + " from patrol_execution where id=:id").setParameter("id", execution).getSingleResult();
        return v instanceof Instant i ? i : v instanceof java.time.OffsetDateTime o ? o.toInstant() : ((java.sql.Timestamp) v).toInstant();
    }

    @Test void usesPhoneTimeForStartAndFinish() {
        UUID execution = UUID.randomUUID();
        Instant start = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant finish = start.plus(40, ChronoUnit.MINUTES);
        send(event("START", execution, start)).statusCode(200).body("status", is("IN_PROGRESS"));
        send(event("FINISH", execution, finish)).statusCode(200).body("status", is("RECEIVED")).body("result", is("COMPLETA"));
        Assertions.assertEquals(start, column(execution, "started_at"));
        Assertions.assertEquals(finish, column(execution, "finished_at"));
    }

    @Test void repeatedFinishReturnsStateInsteadOfConflict() {
        UUID execution = UUID.randomUUID();
        Instant start = Instant.now().minus(30, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        Instant finish = start.plus(10, ChronoUnit.MINUTES);
        send(event("START", execution, start)).statusCode(200);
        send(event("FINISH", execution, finish)).statusCode(200);
        send(event("FINISH", execution, finish)).statusCode(200).body("status", is("COMPLETED")).body("result", is("COMPLETA"));
        Assertions.assertEquals(1, ((Number) em.createNativeQuery("select count(*) from patrol_checkpoint_execution where patrol_execution_id=:id")
            .setParameter("id", execution).getSingleResult()).intValue());
    }

    @Test void withoutExecutedAtUsesReceptionTime() {
        UUID execution = UUID.randomUUID();
        Instant before = Instant.now().minusSeconds(5);
        send(event("START", execution, null)).statusCode(200);
        send(event("FINISH", execution, null)).statusCode(200);
        Assertions.assertFalse(column(execution, "started_at").isBefore(before));
        Assertions.assertFalse(column(execution, "finished_at").isBefore(before));
    }

    @Test void rejectsFutureAndInvertedDates() {
        send(event("START", UUID.randomUUID(), Instant.now().plus(1, ChronoUnit.HOURS))).statusCode(400).body(containsString("futuro"));
        UUID execution = UUID.randomUUID();
        Instant start = Instant.now().minus(20, ChronoUnit.MINUTES);
        send(event("START", execution, start)).statusCode(200);
        send(event("FINISH", execution, start.minus(5, ChronoUnit.MINUTES))).statusCode(400).body(containsString("Fechas de patrulla inválidas"));
        ObjectNode bad = event("FINISH", execution, null).put("executedAt", "ayer");
        send(bad).statusCode(400).body(containsString("inválida"));
    }
}
