package com.cajamarca.sgi.comando.operator;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.*;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class OperatorRuntimeTest {
    @Inject EntityManager em;

    @Test void runtimeIncludesActivePatrolCheckpoints() {
        UUID assignment = ensureAgentAssignment(em);
        Map<String,Object> ids = publishPatrolWithCheckpoint(-2.17, -79.92);
        String cp = "patrols.find{it.protocolId=='" + ids.get("protocolId") + "'}.checkpoints[0]";
        as("agente").queryParam("assignmentId", assignment).get("/api/v1/operator/runtime").then().statusCode(200)
            .body("relief.configurationVersion", notNullValue())
            .body(cp + ".checkpointId", is(ids.get("checkpointId")))
            .body(cp + ".maxPhotos", is(1))
            .body(cp + ".standardImages.size()", is(1))
            .body(cp + ".hasStandardImage", is(true))
            .body(cp + ".radiusM", is(50));
    }

    @Test void draftProtocolsAreNotExposed() {
        UUID assignment = ensureAgentAssignment(em);
        Map<String,Object> draft = createDraftPatrolWithCheckpoint(-2.17, -79.92);
        as("agente").queryParam("assignmentId", assignment).get("/api/v1/operator/runtime").then().statusCode(200)
            .body("patrols.protocolId", not(hasItem(draft.get("protocolId"))));
    }
}
