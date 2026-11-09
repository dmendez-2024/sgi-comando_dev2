package com.cajamarca.sgi.comando.socket;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class SocketPublisherTest {
    @Test void publicaJsonYClasificaRespuestasSinInternet() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var pool=Executors.newCachedThreadPool();server.setExecutor(pool);
        var code=new AtomicInteger(202);var wait=new AtomicInteger();var received=new AtomicReference<JsonNode>();
        var mapper=new ObjectMapper();
        server.createContext("/api/v1/publish",exchange->{
            received.set(mapper.readTree(exchange.getRequestBody()));
            try { if(wait.get()>0) Thread.sleep(wait.get()); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            byte[] body=(code.get()==400?"{\"error\":\"canal inválido\"}":"{\"localRecipients\":0}").getBytes(StandardCharsets.UTF_8);
            try { exchange.sendResponseHeaders(code.get(),body.length);exchange.getResponseBody().write(body); } finally { exchange.close(); }
        });server.start();
        SocketPublisher p=new SocketPublisher();p.config=new SocketConfig();p.config.url="http://127.0.0.1:"+server.getAddress().getPort();
        p.config.source="sgi-comando";p.config.event="obsint.mensaje";p.config.httpSeconds=1;p.mapper=mapper;p.metrics=new SimpleMeterRegistry();p.init();
        try {
            UUID id=UUID.randomUUID();var result=p.publish(id,"persona.9428",Map.of("text","Prueba"));
            assertEquals(SocketPublisher.Outcome.ACCEPTED,result.outcome());assertEquals(0,result.localRecipients());
            assertEquals(id.toString(),received.get().path("id").asText());
            assertEquals("sgi-comando",received.get().path("source").asText());assertEquals("persona.9428",received.get().path("channel").asText());
            assertEquals("obsint.mensaje",received.get().path("event").asText());assertEquals("Prueba",received.get().path("payload").path("text").asText());
            code.set(400);result=p.publish(id,"persona.9428",Map.of());assertEquals(SocketPublisher.Outcome.REJECTED,result.outcome());assertTrue(result.reason().contains("canal inválido"));
            for(int status:List.of(404,408,429,503)) { code.set(status);assertEquals(SocketPublisher.Outcome.UNAVAILABLE,p.publish(id,"persona.9428",Map.of()).outcome()); }
            code.set(202);wait.set(1500);assertEquals(SocketPublisher.Outcome.UNAVAILABLE,p.publish(id,"persona.9428",Map.of()).outcome());
            assertFalse(p.lastPublishOk());
        } finally { p.close();server.stop(0);pool.shutdownNow(); }
    }
}
