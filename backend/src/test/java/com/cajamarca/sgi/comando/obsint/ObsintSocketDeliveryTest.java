package com.cajamarca.sgi.comando.obsint;

import com.cajamarca.sgi.comando.socket.*;
import com.cajamarca.sgi.comando.operator.*;
import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.*;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

/** Gateway REST local y datos sintéticos: ninguna prueba conecta a internet. */
@QuarkusTest
@TestProfile(ObsintSocketDeliveryTest.Profile.class)
class ObsintSocketDeliveryTest {
    public static class Profile implements QuarkusTestProfile {
        public Map<String,String> getConfigOverrides() { return Map.of(
            "sgi.socket.enabled","true","sgi.socket.url","http://localhost:8081/test-socket",
            "sgi.socket.dispatch-every","1h","sgi.socket.presence-enabled","true",
            "sgi.socket.ack.token","ack-test","sgi.socket.ack.allowed-ips","127.0.0.1/32,::1/128","sgi.smc.enabled","false"); }
    }
    @Path("/test-socket") @PermitAll @Produces(MediaType.APPLICATION_JSON)
    public static class FakeGateway {
        public static volatile int code=202,connections=1;
        public static final List<JsonNode> received=new CopyOnWriteArrayList<>();
        @POST @Path("/api/v1/publish") @Consumes(MediaType.APPLICATION_JSON)
        public Response publish(JsonNode body) {
            received.add(body);
            return Response.status(code).entity(code==400?Map.of("error","canal inválido"):Map.of("id",body.path("id").asText(),"channel",body.path("channel").asText(),"localRecipients",connections)).build();
        }
        @GET @Path("/api/v1/presence/{channel}") public Map<String,Object> presence(@PathParam("channel") String channel) { return Map.of("online",true,"connections",1); }
        @GET @Path("/q/health/ready") public Map<String,Object> health() { return Map.of("status","UP"); }
    }
    @Inject EntityManager em;
    @Inject SocketConfig config;
    @Inject SocketPresence presence;
    @Inject ObsintMessageDispatcher dispatcher;
    @Inject ObsintMessageReceipts receipts;
    @Inject Event<ObsintMessageQueued> queued;
    @Test void nombresActualesEnListadoReciboYPublicacionSinNMasUno() {
        FakeGateway.code=202;FakeGateway.connections=1;
        QuarkusTransaction.requiringNew().run(()->{
            // Tablas temporales solo de esta conexión: no modificar catálogos UAT.
            em.createNativeQuery("create temporary table post(id uuid,instance_country_id uuid,point_id uuid,name text) on commit drop").executeUpdate();
            em.createNativeQuery("create temporary table point(id uuid,instance_country_id uuid,name text) on commit drop").executeUpdate();
            var f=ObsintSocketFixtures.create(em);UUID firstPost=UUID.randomUUID(),secondPost=UUID.randomUUID();
            em.createNativeQuery("insert into point values(:id,:tenant,'Telco actual')").setParameter("id",f.point()).setParameter("tenant",f.tenant()).executeUpdate();
            for(UUID post:List.of(firstPost,secondPost)) em.createNativeQuery("insert into post values(:id,:tenant,:point,:name)")
                .setParameter("id",post).setParameter("tenant",f.tenant()).setParameter("point",f.point())
                .setParameter("name",post.equals(firstPost)?"Garita uno":"Garita dos").executeUpdate();
            List<UUID> messages=new ArrayList<>();
            for(UUID post:List.of(firstPost,secondPost,UUID.randomUUID())) {
                var agent=new ObsintAgentRecipients.Recipient(f.employee(),UUID.randomUUID(),post,"Nombre antiguo",9428L,"Agente");
                messages.add(f.service(em,config,queued,List.of(agent)).send(f.task(),"Prueba de nombres").id);
            }
            em.flush();
            em.createNativeQuery("update obsint_agent_message_recipient set next_attempt_at=now()-interval '1 second' where instance_country_id=:tenant")
                .setParameter("tenant",f.tenant()).executeUpdate();
            for(int i=0;i<3;i++) assertTrue(dispatcher.deliverOne());
            var own=OperatorMessagesTestAccess.resource(em,config,receipts,f.tenant(),f.employee());
            var views=own.messages(null);assertEquals(3,views.size());
            var first=views.stream().filter(v->v.postId().equals(firstPost)).findFirst().orElseThrow();
            assertEquals("Telco actual",first.pointName());assertEquals("Garita uno",first.postName());
            assertEquals("Garita dos",views.stream().filter(v->v.postId().equals(secondPost)).findFirst().orElseThrow().postName());
            var missing=views.stream().filter(v->!v.postId().equals(firstPost)&&!v.postId().equals(secondPost)).findFirst().orElseThrow();
            assertNull(missing.pointName());assertNull(missing.postName());
            var receipt=own.receipt(first.recipientId(),new OperatorMessageResource.Receipt("READ"));
            assertEquals("Telco actual",receipt.pointName());assertEquals("Garita uno",receipt.postName());
            var published=FakeGateway.received.stream().filter(n->n.path("id").asText().equals(first.recipientId().toString())).findFirst().orElseThrow().path("payload");
            assertEquals("Telco actual",published.path("pointName").asText());assertEquals("Garita uno",published.path("postName").asText());
            // Del remitente solo llega su cargo: ni su usuario ni su nombre, por REST, recibo ni socket.
            assertEquals(ObsintSocketFixtures.SENDER_ROLE,first.from());assertEquals(ObsintSocketFixtures.SENDER_ROLE,receipt.from());
            assertEquals(ObsintSocketFixtures.SENDER_ROLE,published.path("from").asText());
            assertFalse(published.toString().contains("socket-test"));
            assertEquals(Set.of("messageId","recipientId","taskId","pointId","postId","from","text","sentAt","replyAllowed","pointName","postName"),
                iterableToSet(published.fieldNames()));
            // Un Puesto de otra instancia no aporta nombres aunque coincida su UUID.
            em.createNativeQuery("update post set instance_country_id=:other where id=:id").setParameter("other",UUID.randomUUID()).setParameter("id",firstPost).executeUpdate();
            var isolated=own.messages(null).stream().filter(v->v.postId().equals(firstPost)).findFirst().orElseThrow();
            assertNull(isolated.pointName());assertNull(isolated.postName());
        });
    }
    private static Set<String> iterableToSet(Iterator<String> names) { Set<String> out=new HashSet<>(); names.forEachRemaining(out::add); return out; }
    private ObsintSocketFixtures fixture() { return QuarkusTransaction.requiringNew().call(()->ObsintSocketFixtures.create(em)); }
    private UUID send(ObsintSocketFixtures f,List<ObsintAgentRecipients.Recipient> agents,String target,List<UUID> selected) {
        return QuarkusTransaction.requiringNew().call(()->f.service(em,config,queued,agents).send(f.task(),"Prueba local",target,selected).id);
    }
    private List<ObsintAgentMessageRecipient> rows(UUID message) {
        return QuarkusTransaction.requiringNew().call(()->ObsintAgentMessageRecipient.list("messageId",message));
    }
    private void awaitState(UUID message,String expected) throws InterruptedException {
        long until=System.nanoTime()+10_000_000_000L;String state;
        do {
            dispatcher.tick();state=QuarkusTransaction.requiringNew().call(()->em.find(ObsintAgentMessage.class,message).deliveryStatus);
            if(!expected.equals(state)) Thread.sleep(50);
        }while(!expected.equals(state)&&System.nanoTime()<until);
        assertEquals(expected,state);
    }
    private Map<String,Object> ack(UUID recipient,String channel,String status) { return Map.of("messageId",recipient,"channel",channel,"status",status,"at",Instant.now().toString()); }
    @Test void publicaPorPersonaSeleccionaYRegistraLecturasIndividuales() throws Exception {
        FakeGateway.code=202;FakeGateway.connections=1;
        var f=fixture();var agents=f.agents();
        UUID message=send(f,agents,"POINT",null);awaitState(message,"SENT");
        var recipients=rows(message);assertEquals(2,recipients.size());
        for(var r:recipients) {
            var publications=FakeGateway.received.stream().filter(n->n.path("id").asText().equals(r.id.toString())).toList();
            assertEquals(1,publications.size());assertEquals("persona."+r.personaId,publications.getFirst().path("channel").asText());
            assertEquals("sgi-comando",publications.getFirst().path("source").asText());assertEquals("obsint.mensaje",publications.getFirst().path("event").asText());
        }
        var one=recipients.getFirst();String channel=config.channelFor(one.personaId);
        given().contentType("application/json").body(ack(one.id,channel,"READ")).post("/api/internal/socket/acks").then().statusCode(401);
        given().header("X-Socket-Ack-Token","ack-test").contentType("application/json").body(ack(one.id,"persona.0","READ")).post("/api/internal/socket/acks").then().statusCode(409);
        given().header("X-Socket-Ack-Token","ack-test").contentType("application/json").body(ack(UUID.randomUUID(),channel,"READ")).post("/api/internal/socket/acks").then().statusCode(404);
        given().header("X-Socket-Ack-Token","ack-test").contentType("application/json").body(ack(one.id,channel,"UNKNOWN")).post("/api/internal/socket/acks").then().statusCode(400);
        for(int i=0;i<2;i++) given().header("X-Socket-Ack-Token","ack-test").contentType("application/json").body(ack(one.id,channel,"READ")).post("/api/internal/socket/acks").then().statusCode(204);
        var confirmed=rows(message).stream().filter(r->r.id.equals(one.id)).findFirst().orElseThrow();
        assertEquals("SOCKET_ACK",confirmed.readVia);Instant readAt=confirmed.readAt;
        // Un agente leído y otro solo enviado: el mensaje agregado no puede ser READ.
        awaitState(message,"SENT");
        QuarkusTransaction.requiringNew().run(()->{
            var own=OperatorMessagesTestAccess.resource(em,config,receipts,f.tenant(),one.employeeId);
            assertEquals(1,own.messages(null).size());
            var other=OperatorMessagesTestAccess.resource(em,config,receipts,f.tenant(),UUID.randomUUID());
            assertTrue(other.messages(null).isEmpty());
            assertThrows(NotFoundException.class,()->other.receipt(one.id,new OperatorMessageResource.Receipt("READ")));
            assertThrows(BadRequestException.class,()->own.receipt(one.id,new OperatorMessageResource.Receipt("REJECTED")));
            own.receipt(one.id,new OperatorMessageResource.Receipt("READ"));
            own.receipt(one.id,new OperatorMessageResource.Receipt("DELIVERED"));
        });
        confirmed=rows(message).stream().filter(r->r.id.equals(one.id)).findFirst().orElseThrow();
        assertEquals(readAt,confirmed.readAt);assertEquals("OPERATOR_API",confirmed.readVia);assertEquals("READ",confirmed.status);
        var two=recipients.getLast();
        given().header("X-Socket-Ack-Token","ack-test").contentType("application/json").body(ack(two.id,config.channelFor(two.personaId),"READ")).post("/api/internal/socket/acks").then().statusCode(204);
        awaitState(message,"READ");
        QuarkusTransaction.requiringNew().run(()->{
            var view=ObsintResource.MessageView.of(em.find(ObsintAgentMessage.class,message));
            assertEquals(2,view.counts().read());assertEquals(2,view.counts().sent());assertEquals(2,view.counts().delivered());
            assertEquals("Agente uno",view.recipients().stream().filter(r->r.employeeId().equals(agents.getFirst().employeeId())).findFirst().orElseThrow().fullName());
        });
        UUID selected=send(f,agents,"AGENTS",List.of(agents.getFirst().employeeId(),agents.getFirst().employeeId()));awaitState(selected,"SENT");assertEquals(1,rows(selected).size());
        for(List<UUID> invalid:List.of(List.<UUID>of(),List.of(UUID.randomUUID()))) {
            long before=QuarkusTransaction.requiringNew().call(()->ObsintAgentMessage.count("taskId",f.task()));
            QuarkusTransaction.requiringNew().run(()->assertThrows(BadRequestException.class,
                ()->f.service(em,config,queued,agents).send(f.task(),"Selección inválida","AGENTS",invalid)));
            assertEquals(before,QuarkusTransaction.requiringNew().call(()->ObsintAgentMessage.count("taskId",f.task())));
        }
        ObsintResource resource=new ObsintResource();resource.service=f.service(em,config,queued,agents);resource.presence=presence;
        var available=resource.agentsOnShift(f.task());assertEquals(2,available.size());assertTrue(available.getFirst().reachable());assertEquals(Boolean.TRUE,available.getFirst().online());
        assertThrows(NotFoundException.class,()->resource.agentsOnShift(UUID.randomUUID()));
        QuarkusTransaction.requiringNew().run(()->assertThrows(BadRequestException.class,
            ()->f.service(em,config,queued,agents).send(f.task(),"Destino inválido","OTRO",null)));
    }
    @Test void faltanVinculosRechazosReintentosYAppCerrada() throws Exception {
        FakeGateway.code=202;FakeGateway.connections=0;var f=fixture();var agents=new ArrayList<>(f.agents());
        var second=agents.getLast();agents.set(1,new ObsintAgentRecipients.Recipient(second.employeeId(),second.assignmentId(),second.postId(),second.postName(),null,second.fullName()));
        UUID mixed=send(f,agents,"POINT",null);awaitState(mixed,"SENT");
        var list=rows(mixed);assertEquals(1,list.stream().filter(r->"NO_PERSONA_LINK".equals(r.lastError)).count());
        var sent=list.stream().filter(r->"SENT".equals(r.status)).findFirst().orElseThrow();assertEquals(0,sent.localRecipients);
        long count=FakeGateway.received.stream().filter(n->n.path("id").asText().equals(sent.id.toString())).count();dispatcher.tick();
        assertEquals(count,FakeGateway.received.stream().filter(n->n.path("id").asText().equals(sent.id.toString())).count());
        FakeGateway.code=400;UUID rejected=send(f,f.agents().subList(0,1),"POINT",null);awaitState(rejected,"FAILED");assertTrue(rows(rejected).getFirst().lastError.startsWith("SOCKET_400:"));
        FakeGateway.code=503;UUID retry=send(f,f.agents().subList(0,1),"POINT",null);
        long until=System.nanoTime()+5_000_000_000L;ObsintAgentMessageRecipient r;
        do { dispatcher.tick();r=rows(retry).getFirst();if(r.attempts==0)Thread.sleep(50); }while(r.attempts==0&&System.nanoTime()<until);
        assertEquals("PENDING",r.status);assertTrue(r.attempts>=1);assertTrue(r.nextAttemptAt.isAfter(Instant.now()));UUID recipient=r.id;
        FakeGateway.code=202;QuarkusTransaction.requiringNew().run(()->em.createNativeQuery("update obsint_agent_message_recipient set next_attempt_at=now() where id=:id").setParameter("id",recipient).executeUpdate());
        awaitState(retry,"SENT");assertTrue(FakeGateway.received.stream().filter(n->n.path("id").asText().equals(recipient.toString())).count()>=2);
        UUID noAgent=send(f,List.of(),"POINT",null);assertEquals("NO_AGENT_ON_SHIFT",QuarkusTransaction.requiringNew().call(()->em.find(ObsintAgentMessage.class,noAgent).failureReason));
        ObsintResource resource=new ObsintResource();resource.service=f.service(em,config,queued,agents);resource.presence=presence;
        var missing=resource.agentsOnShift(f.task()).stream().filter(a->!a.reachable()).findFirst().orElseThrow();assertNull(missing.online());
        QuarkusTransaction.requiringNew().run(()->{
            ObsintAgentMessageRecipient expired=ObsintAgentMessageRecipient.findById(recipient);
            expired.status="FAILED";expired.lastError="EXPIRED";
            var own=OperatorMessagesTestAccess.resource(em,config,receipts,f.tenant(),expired.employeeId);
            assertTrue(own.messages(null).stream().anyMatch(m->m.recipientId().equals(recipient)));
            own.receipt(recipient,new OperatorMessageResource.Receipt("READ"));
            assertEquals("READ",expired.status);assertEquals("OPERATOR_API",expired.readVia);
        });
    }
}
