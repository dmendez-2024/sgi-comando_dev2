package com.cajamarca.sgi.comando.patrols;

import com.cajamarca.sgi.comando.security.TestIdentTokenVerifier;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@QuarkusTest
class PatrolDuplicateTest {
    private static final UUID TEST_POST_ID=UUID.fromString("92000000-0000-0000-0000-000000000003");
    @Inject EntityManager em;

    @BeforeEach void assignFixturePointToACompany(){
        QuarkusTransaction.requiringNew().run(()->{
            em.createNativeQuery("""
                insert into company(id,instance_country_id,code,name,status,created_at,updated_at)
                values('29000000-0000-0000-0000-000000000001','11111111-1111-1111-1111-111111111111','TEST-DUPLICATE','Compañía prueba duplicación','ACTIVE',now(),now())
                on conflict do nothing
                """).executeUpdate();
            em.createNativeQuery("""
                update point set company_id='29000000-0000-0000-0000-000000000001'
                where id='92000000-0000-0000-0000-000000000002'
                """).executeUpdate();
        });
    }

    @AfterEach void restoreFixturePoint(){
        QuarkusTransaction.requiringNew().run(()->{
            em.createNativeQuery("update point set company_id=null where id='92000000-0000-0000-0000-000000000002'").executeUpdate();
            em.createNativeQuery("delete from company where id='29000000-0000-0000-0000-000000000001'").executeUpdate();
        });
    }

    @Test void duplicatesCompletePatrolWithOnlyANewScheduleAndRejectsPublishedProtocol(){
        String protocolId=asPresident().contentType(ContentType.JSON)
            .body(Map.of("postId",TEST_POST_ID,"name","Protocolo para duplicar "+UUID.randomUUID()))
            .post("/api/patrols/protocols").then().statusCode(200).extract().path("id");
        String patrolId=asPresident().contentType(ContentType.JSON)
            .body(Map.of("name","Ronda perimetral","structureType","CLOSED","scheduleType","PROGRAMMED"))
            .post("/api/patrols/protocols/"+protocolId+"/patrols").then().statusCode(200).extract().path("id");
        asPresident().contentType(ContentType.JSON).body(Map.of(
            "name","Ronda perimetral","description","Control completo del perímetro","structureType","CLOSED",
            "scheduleType","PROGRAMMED","sequenceType","FLEXIBLE","windowStart","22:00","windowEnd","22:45","repetitions",1))
            .put("/api/patrols/patrols/"+patrolId).then().statusCode(200);
        String checkpointId=asPresident().contentType(ContentType.JSON).body(Map.of(
            "name","Portón principal","description","Validar el acceso","originMode","FIELD","latitude",-2.17,"longitude",-79.92,
            "gpsAccuracyM",5.0,"controlType","FOTOGRAFIA","requiresEvidence",true))
            .post("/api/patrols/patrols/"+patrolId+"/checkpoints").then().statusCode(200).extract().path("id");
        asPresident().multiPart("file",Path.of("src/test/resources/fixtures/sample.jpg").toFile(),"image/jpeg")
            .post("/api/patrols/checkpoints/"+checkpointId+"/standard-images").then().statusCode(200);

        asPresident().contentType(ContentType.JSON).body(Map.of("windowStart","22:00","windowEnd","22:45"))
            .post("/api/patrols/patrols/"+patrolId+"/duplicate").then().statusCode(400);

        var duplicated=asPresident().contentType(ContentType.JSON).body(Map.of("windowStart","23:00","windowEnd","23:45"))
            .post("/api/patrols/patrols/"+patrolId+"/duplicate").then().statusCode(200)
            .body("code",is("PAT-002"),"name",is("Ronda perimetral"),"description",is("Control completo del perímetro"),
                "structureType",is("CLOSED"),"scheduleType",is("PROGRAMMED"),"sequenceType",is("FLEXIBLE"),
                "windowStart",is("23:00"),"windowEnd",is("23:45"),"versionNo",is(1),
                "checkpoints.size()",is(1),"checkpoints[0].name",is("Portón principal"),
                "checkpoints[0].rules.size()",greaterThan(0),"checkpoints[0].standardImages.size()",is(1)).extract();
        String duplicateId=duplicated.path("id"),duplicateCheckpointId=duplicated.path("checkpoints[0].id");
        assertNotEquals(patrolId,duplicateId);
        assertNotEquals(checkpointId,duplicateCheckpointId);
        LocalTime[] originalWindow=QuarkusTransaction.requiringNew().call(()->{
            PatrolDefinition original=PatrolDefinition.findById(UUID.fromString(patrolId));
            return new LocalTime[]{original.windowStart,original.windowEnd};
        });
        assertEquals(LocalTime.of(22,0),originalWindow[0]);
        assertEquals(LocalTime.of(22,45),originalWindow[1]);

        asPresident().post("/api/patrols/protocols/"+protocolId+"/publish").then().statusCode(200).body("status",is("ACTIVO"));
        asPresident().contentType(ContentType.JSON).body(Map.of("windowStart","00:00","windowEnd","00:45"))
            .post("/api/patrols/patrols/"+patrolId+"/duplicate").then().statusCode(409);
    }

    private static RequestSpecification asPresident(){
        Map<String,Object> claims=Map.of(
            "sub","ident-presidente-duplicate-test","aud","SGI_COM","token_use","access",
            "exp",Instant.now().plusSeconds(600).getEpochSecond(),"groups",List.of("PRESIDENTE"),
            "active_instance_country_id","11111111-1111-1111-1111-111111111111","email","presidente@cajamarca.test");
        return given().header("Authorization","Bearer "+TestIdentTokenVerifier.token(claims));
    }
}
