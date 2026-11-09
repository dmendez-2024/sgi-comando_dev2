package com.cajamarca.sgi.comando.obsint;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.*;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.util.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/** La caída del gateway no impide arrancar; se conservan y vencen los mensajes pendientes. */
@QuarkusTest
@TestProfile(ObsintSocketUnavailableTest.Profile.class)
class ObsintSocketUnavailableTest {
    public static class Profile implements QuarkusTestProfile {
        public Map<String,String> getConfigOverrides() { return Map.of(
            "sgi.socket.enabled","true","sgi.socket.url","http://localhost:1/no-disponible",
            "sgi.socket.http-timeout-seconds","1","sgi.socket.message-ttl-minutes","1",
            "sgi.socket.dispatch-every","1h","sgi.smc.enabled","false"); }
    }
    @Inject EntityManager em;
    @Inject ObsintMessageDispatcher dispatcher;
    @Inject com.cajamarca.sgi.comando.socket.SocketConfig config;
    @Inject jakarta.enterprise.event.Event<ObsintMessageQueued> queued;
    @Test void conservaReadinessYVencePendientesSinSocket() throws InterruptedException {
        given().get("/q/health/ready").then().statusCode(200).body("status",is("UP"));
        given().get("/q/health/group/socket").then().statusCode(503).body("status",is("DOWN"));
        var fixture=QuarkusTransaction.requiringNew().call(()->ObsintSocketFixtures.create(em));
        UUID m=QuarkusTransaction.requiringNew().call(()->fixture.send(em,config,queued,true).id);
        dispatcher.tick();
        QuarkusTransaction.requiringNew().run(()->{
            ObsintAgentMessageRecipient r=ObsintAgentMessageRecipient.find("messageId",m).firstResult();
            // El despachador puede haber registrado un intento HTTP antes de esta consulta.
            em.createNativeQuery("update obsint_agent_message_recipient set created_at=now()-interval '2 minutes' where message_id=:id")
                .setParameter("id",m).executeUpdate();
        });
        long until=System.nanoTime()+5_000_000_000L;
        String state;
        do {
            dispatcher.tick();
            state=QuarkusTransaction.requiringNew().call(()->em.find(ObsintAgentMessage.class,m).deliveryStatus);
            if(!"FAILED".equals(state)) Thread.sleep(50);
        } while(!"FAILED".equals(state) && System.nanoTime()<until);
        org.junit.jupiter.api.Assertions.assertEquals("FAILED",state);
        org.junit.jupiter.api.Assertions.assertEquals("EXPIRED",QuarkusTransaction.requiringNew().call(()->em.find(ObsintAgentMessage.class,m).failureReason));
    }
}
