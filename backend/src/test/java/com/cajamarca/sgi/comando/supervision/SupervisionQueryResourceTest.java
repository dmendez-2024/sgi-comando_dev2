package com.cajamarca.sgi.comando.supervision;

import com.cajamarca.sgi.comando.execution.EvidenceObject;
import com.cajamarca.sgi.comando.operator.OperatorFixtures;
import com.cajamarca.sgi.comando.storage.StorageService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;

import static com.cajamarca.sgi.comando.operator.OperatorFixtures.POST_GGTT01;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

@QuarkusTest
class SupervisionQueryResourceTest {
    private static final String TENANT="11111111-1111-1111-1111-111111111111";
    private static final String POINT="40000000-0000-0000-0000-000000000001";
    @Inject EntityManager em; @Inject StorageService storage;
    UUID evaluationId; byte[] photo;

    /** Evaluación de GGTT01 como la deja SupervisorEvaluationResource: foto del Agente en MinIO y un ítem de inventario. */
    @BeforeEach
    void seedEvaluation() throws Exception {
        UUID assignment=OperatorFixtures.ensureAgentAssignment(em); photo=Files.readAllBytes(Path.of("src/test/resources/fixtures/sample.jpg"));
        evaluationId=UUID.randomUUID(); UUID evidenceId=UUID.randomUUID(); String key="evidence/supervision/"+evaluationId+"/"+evidenceId+".jpg";
        storage.put(storage.evidenceBucket(),key,photo,"image/jpeg");
        QuarkusTransaction.requiringNew().run(()->{
            UUID employee=(UUID)em.createNativeQuery("select employee_id from operational_assignment where id=:a").setParameter("a",assignment).getSingleResult();
            em.createNativeQuery("delete from supervisor_evaluation where supervisor_assignment_id=:a and post_id=:p").setParameter("a",assignment).setParameter("p",POST_GGTT01).executeUpdate();
            EvidenceObject e=new EvidenceObject();e.id=evidenceId;e.instanceCountryId=UUID.fromString(TENANT);e.clientEvidenceId=UUID.randomUUID();e.uploadBatchId=evaluationId;e.eventId=evaluationId;
            e.assignmentId=assignment;e.employeeId=employee;e.username="supervisor";e.targetType="SUPERVISOR_AGENT_PHOTO";e.targetId=POST_GGTT01;e.bucket=storage.evidenceBucket();e.objectKey=key;
            e.contentType="image/jpeg";e.sizeBytes=photo.length;e.sha256="0".repeat(64);e.capturedAt=Instant.now();e.source="CAMERA";e.status="STORED";e.receivedAt=Instant.now();e.persist();em.flush();
            em.createNativeQuery("""
                insert into supervisor_evaluation(id,instance_country_id,supervisor_assignment_id,agent_assignment_id,supervisor_employee_id,agent_employee_id,point_id,post_id,captured_at,
                  vigilance_level,cleanliness_level,consignment_knowledge_level,situational_level,consignment_snapshot,agent_photo_evidence_id,inventory_overall_state,inventory_comments,
                  supervisor_comments,point_post_notes,status,correlation_id,created_by,created_at,payload_hash)
                values(:id,cast(:t as uuid),:a,:a,:e,:e,cast(:point as uuid),:post,now(),'HIGH','MEDIUM','VERY_HIGH','LOW','Consigna vigente',:ev,'OBSERVATIONS','Falta linterna',
                  'Agente atento al acceso','Revisar iluminación','COMPLETED',gen_random_uuid(),'supervisor',now(),'hash')""")
                .setParameter("id",evaluationId).setParameter("t",TENANT).setParameter("a",assignment).setParameter("e",employee).setParameter("point",POINT)
                .setParameter("post",POST_GGTT01).setParameter("ev",evidenceId).executeUpdate();
            em.createNativeQuery("insert into supervisor_evaluation_inventory_item(evaluation_id,asset_id,description_snapshot,observed_state,comment) values(:id,gen_random_uuid(),'Linterna','MISSING','No está en el puesto')")
                .setParameter("id",evaluationId).executeUpdate();
        });
    }

    @Test
    void listShowsEvaluationWithNamesAndLevels(){
        String item="items.find{it.id=='"+evaluationId+"'}";
        as("coord").queryParam("pointId",POINT).queryParam("postId",POST_GGTT01.toString()).get("/api/supervision/evaluations").then().statusCode(200)
            .body("total",greaterThanOrEqualTo(1))
            .body(item+".postCode",is("GGTT01")).body(item+".pointName",not(blankOrNullString())).body(item+".clientName",not(blankOrNullString()))
            .body(item+".companyName",not(blankOrNullString())).body(item+".agentName",not(blankOrNullString())).body(item+".supervisorName",not(blankOrNullString()))
            .body(item+".vigilanceLevel",is("HIGH")).body(item+".cleanlinessLevel",is("MEDIUM")).body(item+".consignmentKnowledgeLevel",is("VERY_HIGH"))
            .body(item+".situationalLevel",is("LOW")).body(item+".inventoryOverallState",is("OBSERVATIONS"));
    }

    @Test
    void dateRangeOutsideCaptureExcludesEvaluation(){
        as("coord").queryParam("postId",POST_GGTT01.toString()).queryParam("from","2000-01-01").queryParam("to","2000-01-31").get("/api/supervision/evaluations").then().statusCode(200)
            .body("total",is(0)).body("items",empty());
        as("coord").queryParam("from","2026-02-01").queryParam("to","2026-01-01").get("/api/supervision/evaluations").then().statusCode(400);
    }

    @Test
    void filtersOfferThePointAndPost(){
        as("coord").get("/api/supervision/evaluations/filters").then().statusCode(200)
            .body("points.id",hasItem(POINT)).body("posts.find{it.id=='"+POST_GGTT01+"'}.parentId",is(POINT)).body("agents",not(empty()));
    }

    @Test
    void detailIncludesCommentsAndInventory(){
        as("coord").get("/api/supervision/evaluations/"+evaluationId).then().statusCode(200)
            .body("row.postCode",is("GGTT01")).body("supervisorComments",is("Agente atento al acceso")).body("pointPostNotes",is("Revisar iluminación"))
            .body("inventoryComments",is("Falta linterna")).body("consignmentSnapshot",is("Consigna vigente")).body("hasPhoto",is(true))
            .body("inventory[0].description",is("Linterna")).body("inventory[0].observedState",is("MISSING")).body("decisions",empty());
    }

    @Test
    void photoIsServedFromStorage(){
        byte[] body=as("coord").get("/api/supervision/evaluations/"+evaluationId+"/photo").then().statusCode(200).contentType("image/jpeg").extract().asByteArray();
        assertArrayEquals(photo,body);
    }

    @Test
    void unknownEvaluationIsNotFoundAndSupervisorCannotQuery(){
        as("coord").get("/api/supervision/evaluations/"+UUID.randomUUID()).then().statusCode(404);
        as("supervisor").get("/api/supervision/evaluations").then().statusCode(403);
    }
}
