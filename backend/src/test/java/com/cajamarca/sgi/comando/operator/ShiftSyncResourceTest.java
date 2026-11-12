package com.cajamarca.sgi.comando.operator;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.util.*;
import static io.restassured.RestAssured.given;

@QuarkusTest
class ShiftSyncResourceTest {
    @Inject EntityManager em;
    static final String PATH="/api/v1/operator/shift-sync";
    @Test void anonymousCannotPrepareOrClose() {
        given().contentType("application/json").body("{\"action\":\"PREPARE\"}").post(PATH).then().statusCode(401);
    }
    @Test void administrativeUserCannotCloseAnAgentsTurn() {
        UUID id=UUID.randomUUID();String username="shift-admin-"+id.toString().substring(0,8),password="synthetic-"+UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(()->em.createNativeQuery("""
            insert into app_user(id,username,password_hash,roles,display_name,instance_country_id,active)
            select :id,:name,crypt(:password,gen_salt('bf')),'PRESIDENTE',:name,instance_country_id,true
            from instance_country_context where singleton_key=1
            """).setParameter("id",id).setParameter("name",username).setParameter("password",password).executeUpdate());
        try {
            given().auth().preemptive().basic(username,password).contentType("application/json")
                .body(Map.of("action","COMPLETE","assignmentId",UUID.randomUUID(),"deviceId","test","allAcknowledged",true,"operations",List.of()))
                .post(PATH).then().statusCode(403);
        } finally {QuarkusTransaction.requiringNew().run(()->em.createNativeQuery("delete from app_user where id=:id").setParameter("id",id).executeUpdate());}
    }
}
