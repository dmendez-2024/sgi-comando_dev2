package com.cajamarca.sgi.comando.smc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** Ejecuta el adaptador, OAuth2 y lectura OpenBao reales contra servidores HTTP locales. */
class SmcImpulseHttpTest {
    @TempDir Path directory;

    @Test void sendsImpulseOccurrencesApprovalAndKilometersWithServiceTokenAndStableIdempotency() throws Exception {
        String key="sgi.m2m.client-id.SMC",previous=System.getProperty(key);
        System.setProperty(key,"sgi-com-to-smc-local-test");
        try(var h=new SmcOpenBaoTest.Harness(directory,"OAUTH2_CLIENT_CREDENTIALS","vault:kv/data/local-test/smc")) {
            var sent=new AtomicReference<String>();var eventKey=new AtomicReference<String>();
            h.server.removeContext("/api/v1/operational-facts");
            h.server.createContext("/api/v1/operational-facts",exchange->{
                assertEquals("Bearer local-ident-access",exchange.getRequestHeaders().getFirst("Authorization"));
                eventKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
                sent.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                SmcOpenBaoTest.reply(exchange,201,"{\"accepted\":true,\"smcFactId\":\"1024\"}");
            });
            for(String type:new String[]{"RELIEF_COMPLETED_ON_TIME","RELIEF_FULL_BODY_PHOTO","ACCESS_CONTROL_OK","PATROL_OK","CONSIGNMENT_OK","NOVELTY_APPROVED","PATROL_KM"}) {
                String source=switch(type){case "NOVELTY_APPROVED"->"NOVELTY_APPROVAL";case "PATROL_KM"->"PATROL_DISTANCE";default->"IMPULSE_EVALUATION";};
                BigDecimal value="PATROL_KM".equals(type)?new BigDecimal("0.100075"):BigDecimal.ONE;
                var fact=new SmcFact(h.pe,source,UUID.randomUUID(),UUID.randomUUID(),type,
                    Instant.now(),value,null,null,null,Map.of("source",source),123L);
                for(int attempt=0;attempt<2;attempt++) {
                    assertEquals("SENT",SmcDeliveryOutcome.from(h.adapter.send(h.pe,fact.payload())).status());
                    var body=h.json.readTree(sent.get());assertEquals(type,body.path("factType").asText());
                    assertEquals(0,value.compareTo(body.path("value").decimalValue()));assertEquals(fact.eventId().toString(),eventKey.get());
                    assertFalse(body.has("amount"));assertFalse(body.has("draw"));
                }
            }
            assertEquals(1,h.tokenCalls.get());assertEquals(1,h.secretReads.get());
        } finally {if(previous==null)System.clearProperty(key);else System.setProperty(key,previous);}
    }
}
