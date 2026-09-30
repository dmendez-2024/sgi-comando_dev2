package com.cajamarca.sgi.comando.operation;

import com.cajamarca.sgi.comando.operator.VisintFixtures;
import com.cajamarca.sgi.comando.visint.VisualReviewWorker;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class OperationResourceTest {
    @Inject EntityManager em;
    @Inject VisualReviewWorker worker;
    static final String POINT = "40000000-0000-0000-0000-000000000001";
    static final Path SAME = Path.of("src/test/resources/fixtures/sample.jpg"), OTHER = Path.of("src/test/resources/fixtures/sample2.jpg");

    @Test void listAndDetailShowVerdict() throws Exception {
        UUID event = VisintFixtures.executeWithPhoto(em, VisintFixtures.publishVisintPatrol(List.of(OTHER, SAME)), SAME);
        as("coord").queryParam("pointId", POINT).get("/api/operation/executions").then().statusCode(200)
            .body("find{it.id=='" + event + "'}.review.status", is("QUEUED_FOR_VISINT"))
            .body("find{it.id=='" + event + "'}.evidenceIds.size()", is(1))
            .body("find{it.id=='" + event + "'}.taskName", is("Portón prueba")).body("find{it.id=='" + event + "'}.module", is("PATRULLA"));
        worker.processDue();
        var detail = as("coord").get("/api/operation/executions/" + event).then().statusCode(200)
            .body("review.status", is("PASSED")).body("review.result", is("PASS")).body("review.simulated", is(true))
            .body("review.findings", containsString("simulado")).body("evidences.size()", is(1))
            .body("standards.size()", is(2)).body("standards[1].position", is(2))
            .body("review.reasonCode", is("OK")).body("review.modelVersion", containsString("simulado")).extract();
        assertEquals((String) detail.path("standards[1].id"), detail.path("review.matchedStandardImageId"), "coincide la foto estándar idéntica a la del agente");
    }

    @Test void servesAgentPhotoAndStandards() throws Exception {
        UUID event = VisintFixtures.executeWithPhoto(em, VisintFixtures.publishVisintPatrol(List.of(SAME, OTHER)), OTHER);
        var detail = as("coord").get("/api/operation/executions/" + event).then().statusCode(200).extract();
        assertArrayEquals(Files.readAllBytes(OTHER), as("coord").get("/api/operation/evidences/" + detail.path("evidences[0].id") + "/content").then().statusCode(200).extract().asByteArray());
        assertArrayEquals(Files.readAllBytes(SAME), as("coord").get("/api/operation/executions/" + event + "/standards/" + detail.path("standards[0].id")).then().statusCode(200).extract().asByteArray());
        assertArrayEquals(Files.readAllBytes(OTHER), as("coord").get("/api/operation/executions/" + event + "/standards/" + detail.path("standards[1].id")).then().statusCode(200).extract().asByteArray());
    }

    /** Si después se cambian las fotos estándar del Hito, Operación sigue mostrando las que se enviaron a VISINT. */
    @Test void standardsAreTheSnapshotSent() throws Exception {
        Map<String,Object> ids = VisintFixtures.publishVisintPatrol();
        UUID event = VisintFixtures.executeWithPhoto(em, ids, SAME);
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("update standard_reference_image set object_key='standard/patrol/otra.jpg' where target_type='PATROL_CHECKPOINT' and target_id=:id")
            .setParameter("id", UUID.fromString(ids.get("checkpointId").toString())).executeUpdate());
        String std = as("coord").get("/api/operation/executions/" + event).then().statusCode(200).extract().path("standards[0].id");
        assertArrayEquals(Files.readAllBytes(SAME), as("coord").get("/api/operation/executions/" + event + "/standards/" + std).then().statusCode(200).extract().asByteArray());
    }

    @Test void retryOnlyFromErrorAndNotForAgents() throws Exception {
        UUID event = VisintFixtures.executeWithPhoto(em, VisintFixtures.publishVisintPatrol(), SAME);
        String review = as("coord").get("/api/operation/executions/" + event).then().statusCode(200).extract().path("review.id");
        as("coord").post("/api/operation/visual-reviews/" + review + "/retry").then().statusCode(409);
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("update visual_review set status='ERROR_FINAL', attempts=5 where id=:id")
            .setParameter("id", UUID.fromString(review)).executeUpdate());
        as("agente").post("/api/operation/visual-reviews/" + review + "/retry").then().statusCode(403);
        as("coord").post("/api/operation/visual-reviews/" + review + "/retry").then().statusCode(200)
            .body("status", is("QUEUED_FOR_VISINT")).body("attempts", is(0));
    }

    @Test void agentsCannotListOperation() {
        as("agente").queryParam("pointId", POINT).get("/api/operation/executions").then().statusCode(403);
    }
}
