package com.cajamarca.sgi.comando.smc;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.restassured.specification.RequestSpecification;
import java.util.*;
import static io.restassured.RestAssured.given;

@QuarkusTest
class DayOffWorkedResourceTest {
    static final String PATH="/api/v1/integrations/smc/day-off-worked";
    @Inject EntityManager em;
    final String national="smc-national-"+UUID.randomUUID().toString().substring(0,8);
    final String coordinator="smc-coord-"+UUID.randomUUID().toString().substring(0,8);
    final String password="local-test-"+UUID.randomUUID();
    @BeforeEach void users() {
        QuarkusTransaction.requiringNew().run(()->{
            for(var user:Map.of(national,"PRESIDENTE",coordinator,"COORDINADOR_COMPANIA").entrySet())
                em.createNativeQuery("""
                    insert into app_user(id,username,password_hash,roles,display_name,instance_country_id,active)
                    select :id,:name,crypt(:password,gen_salt('bf')),:role,:name,instance_country_id,true
                    from instance_country_context where singleton_key=1
                    """).setParameter("id",UUID.randomUUID()).setParameter("name",user.getKey())
                    .setParameter("password",password).setParameter("role",user.getValue()).executeUpdate();
        });
    }
    @AfterEach void cleanup() {
        QuarkusTransaction.requiringNew().run(()->em.createNativeQuery("delete from app_user where username in (:names)")
            .setParameter("names",List.of(national,coordinator)).executeUpdate());
    }
    RequestSpecification as(String username) {return given().auth().preemptive().basic(username,password);}
    Map<String,Object> request() {
        return Map.of("assignmentId",UUID.randomUUID(),"restDate","2026-01-02",
            "restEvidenceRef","RRHH:test","workEvidenceRef","OPS:test","reason","Acreditación de prueba");
    }
    @Test void requiresAuthentication() {
        given().contentType(ContentType.JSON).body(request()).post(PATH).then().statusCode(401);
        given().get(PATH+"/"+UUID.randomUUID()).then().statusCode(401);
    }
    @Test void coordinatorCannotAccreditOrReadNationalRecords() {
        as(coordinator).contentType(ContentType.JSON).body(request()).post(PATH).then().statusCode(403);
        as(coordinator).get(PATH+"/"+UUID.randomUUID()).then().statusCode(403);
    }
    @Test void nationalUserReachesContractAndOnlyOwnTenantRecords() {
        as(national).contentType(ContentType.JSON).body(request()).post(PATH).then().statusCode(404);
        as(national).contentType(ContentType.JSON).body(Map.of()).post(PATH).then().statusCode(400);
        as(national).get(PATH+"/"+UUID.randomUUID()).then().statusCode(404);
    }
}
