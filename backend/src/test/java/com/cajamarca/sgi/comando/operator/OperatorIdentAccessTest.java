package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.security.TestIdentTokenVerifier;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/** SGI Operador entra solo con el access token de IDENT (aud=SGI_OPR); su usuario y contrasena de SGI ya no se aceptan. */
@QuarkusTest
class OperatorIdentAccessTest {
    @Inject EntityManager em;

    private static Map<String,Object> agentClaims() {
        Map<String,Object> c = new LinkedHashMap<>();
        c.put("sub", "ident-agente");
        c.put("aud", "SGI_OPR");
        c.put("token_use", "access");
        c.put("exp", java.time.Instant.now().plusSeconds(600).getEpochSecond());
        c.put("groups", List.of("AGENTE"));
        c.put("persona_id", String.valueOf(AGENT_PERSONA_ID));
        c.put("active_instance_country_id", "11111111-1111-1111-1111-111111111111");
        return c;
    }

    private static io.restassured.specification.RequestSpecification bearer(Map<String,Object> claims) {
        return given().header("Authorization", "Bearer " + TestIdentTokenVerifier.token(claims));
    }

    @Test void agentEntersWithIdentAndGetsEmployeeByPersonaId() {
        UUID assignment = ensureAgentAssignment(em);
        Object expected = em.createNativeQuery("select employee_id from employee_operational_snapshot where persona_id=:p")
            .setParameter("p", AGENT_PERSONA_ID).getSingleResult();
        as("agente").queryParam("assignmentId", assignment).get("/api/v1/operator/runtime").then().statusCode(200)
            .body("employeeId", is(expected.toString()));
    }

    @Test void basicCredentialsOfOperatorRolesAreRejected() {
        ensureAgentAssignment(em);
        asBasic("agente").get("/api/v1/operator/runtime").then().statusCode(401)
            .header("WWW-Authenticate", containsString("Bearer"));
        asBasic("supervisor").get("/api/v1/operator/runtime").then().statusCode(401);
    }

    @Test void idTokenAndOtherAudiencesAreRejected() {
        Map<String,Object> id = agentClaims(); id.put("token_use", "id");
        bearer(id).get("/api/v1/operator/runtime").then().statusCode(401);
        Map<String,Object> web = agentClaims(); web.put("aud", "SGI_COM");
        bearer(web).get("/api/v1/operator/runtime").then().statusCode(401);
        Map<String,Object> expired = agentClaims(); expired.put("exp", java.time.Instant.now().minusSeconds(3600).getEpochSecond());
        bearer(expired).get("/api/v1/operator/runtime").then().statusCode(401);
    }

    @Test void unknownIdentRoleGivesNoOperatorAccess() {
        ensureAgentAssignment(em);
        Map<String,Object> c = agentClaims(); c.put("groups", List.of("AGENTE_SEGURIDAD"));
        bearer(c).get("/api/v1/operator/runtime").then().statusCode(403);
    }

    @Test void runtimeByPersonaNeedsNoAuthentication() {
        UUID assignment = ensureAgentAssignment(em);
        Object expected = em.createNativeQuery("select employee_id from employee_operational_snapshot where persona_id=:p")
            .setParameter("p", AGENT_PERSONA_ID).getSingleResult();
        given().queryParam("personaId", AGENT_PERSONA_ID).get("/api/v1/operator/runtime/by-persona").then().statusCode(200)
            .body("employeeId", is(expected.toString())).body("personaId", is(String.valueOf(AGENT_PERSONA_ID)))
            .body("assignments.assignmentId", hasItem(assignment.toString()));
        given().queryParam("personaId", AGENT_PERSONA_ID).queryParam("assignmentId", assignment)
            .get("/api/v1/operator/runtime/by-persona").then().statusCode(200).body("relief.assignmentId", is(assignment.toString()));
    }

    @Test void runtimeByPersonaRejectsMissingOrUnknownPersona() {
        given().get("/api/v1/operator/runtime/by-persona").then().statusCode(400);
        given().queryParam("personaId", 123456789L).get("/api/v1/operator/runtime/by-persona").then().statusCode(403);
    }

    @Test void operatorApiAcceptsPersonaIdWithoutToken() {
        UUID assignment = ensureAgentAssignment(em);
        Object expected = em.createNativeQuery("select employee_id from employee_operational_snapshot where persona_id=:p")
            .setParameter("p", AGENT_PERSONA_ID).getSingleResult();
        given().header("X-Persona-Id", AGENT_PERSONA_ID).queryParam("assignmentId", assignment)
            .get("/api/v1/operator/runtime").then().statusCode(200).body("employeeId", is(expected.toString()));
        given().queryParam("personaId", AGENT_PERSONA_ID).get("/api/v1/operator/impulses").then().statusCode(200);
        // Token que el backend no puede validar: la persona igual entra por X-Persona-Id.
        Map<String,Object> expired = agentClaims(); expired.put("exp", java.time.Instant.now().minusSeconds(3600).getEpochSecond());
        bearer(expired).header("X-Persona-Id", AGENT_PERSONA_ID)
            .get("/api/v1/operator/runtime").then().statusCode(200);
    }

    @Test void personaIdIsRejectedWhenUnknownOrOutsideOperatorRoutes() {
        given().get("/api/v1/operator/runtime").then().statusCode(401);
        given().header("X-Persona-Id", 123456789L).get("/api/v1/operator/runtime").then().statusCode(401);
        given().header("X-Persona-Id", "abc").get("/api/v1/operator/runtime").then().statusCode(401);
        ensureAgentAssignment(em);
        given().header("X-Persona-Id", AGENT_PERSONA_ID).get("/api/consignments").then().statusCode(401);
    }

    @Test void operatorTokenOnlyReachesOperatorServices() {
        as("agente").get("/api/consignments").then().statusCode(403);
        as("supervisor").get("/api/coordination/coverage").then().statusCode(403);
    }
}
