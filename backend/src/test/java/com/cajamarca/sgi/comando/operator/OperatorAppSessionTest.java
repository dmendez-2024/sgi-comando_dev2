package com.cajamarca.sgi.comando.operator;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.*;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

/** Registro de inicio y cierre de sesión del agente en la app ({@code operator_app_session}). */
@QuarkusTest
class OperatorAppSessionTest {
    @Inject EntityManager em;
    UUID assignment;

    @BeforeEach void setUp() { assignment = ensureAgentAssignment(em); }

    @Test void loginAndLogoutAreRecordedWithDefaultApplication() {
        UUID session = UUID.randomUUID();
        Instant in = Instant.now().minus(Duration.ofHours(2)).truncatedTo(ChronoUnit.MILLIS), out = Instant.now().minus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MILLIS);
        String login = "{\"sessionId\":\"" + session + "\",\"loggedInAt\":\"" + in + "\",\"assignmentId\":\"" + assignment + "\",\"deviceId\":\"dev-1\",\"appVersion\":\"1.0.0\"}";

        as("agente").contentType(ContentType.JSON).body(login).post("/api/v1/operator/sessions")
            .then().statusCode(200).body("application", is("SGI_OPERADOR")).body("idempotentReplay", is(false));
        // Reintento desde la cola sin conexión.
        as("agente").contentType(ContentType.JSON).body(login).post("/api/v1/operator/sessions")
            .then().statusCode(200).body("idempotentReplay", is(true));
        as("agente").contentType(ContentType.JSON).body("{\"loggedOutAt\":\"" + out + "\"}")
            .post("/api/v1/operator/sessions/" + session + "/logout")
            .then().statusCode(200).body("logoutReason", is("USER")).body("loggedOutAt", is(out.toString()));

        Object[] row = (Object[]) em.createNativeQuery("""
            select application,username,assignment_id,device_id,logged_in_at is not null,logged_out_at is not null
            from operator_app_session where id=:id""").setParameter("id", session).getSingleResult();
        assertEquals("SGI_OPERADOR", row[0]);
        assertEquals("ident-agente", row[1]);
        assertEquals(assignment.toString(), row[2].toString());
        assertEquals("dev-1", row[3]);
        assertEquals(Boolean.TRUE, row[4]);
        assertEquals(Boolean.TRUE, row[5]);
    }

    @Test void logoutBeforeLoginArrivesIsCompletedLater() {
        UUID session = UUID.randomUUID();
        Instant in = Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MILLIS), out = Instant.now().minus(Duration.ofMinutes(1)).truncatedTo(ChronoUnit.MILLIS);
        as("agente").contentType(ContentType.JSON).body("{\"loggedOutAt\":\"" + out + "\",\"reason\":\"expired\"}")
            .post("/api/v1/operator/sessions/" + session + "/logout").then().statusCode(200).body("logoutReason", is("EXPIRED"));
        as("agente").contentType(ContentType.JSON).body("{\"sessionId\":\"" + session + "\",\"loggedInAt\":\"" + in + "\"}")
            .post("/api/v1/operator/sessions").then().statusCode(200).body("loggedInAt", is(in.toString())).body("logoutReason", is("EXPIRED"));
    }

    @Test void unknownApplicationIsRejected() {
        as("agente").contentType(ContentType.JSON)
            .body("{\"sessionId\":\"" + UUID.randomUUID() + "\",\"loggedInAt\":\"" + Instant.now() + "\",\"application\":\"OTRA\"}")
            .post("/api/v1/operator/sessions").then().statusCode(400);
    }
}
