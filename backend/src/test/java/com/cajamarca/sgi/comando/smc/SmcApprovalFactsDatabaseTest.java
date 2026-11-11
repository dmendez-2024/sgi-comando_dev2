package com.cajamarca.sgi.comando.smc;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SmcApprovalFactsDatabaseTest {
    @Inject EntityManager em;
    UUID tenant=UUID.randomUUID();
    final UUID employee=UUID.randomUUID(),assignment=UUID.randomUUID();
    final UUID point=UUID.randomUUID(),post=UUID.randomUUID(),shift=UUID.randomUUID();
    final Instant since=Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(86400);

    void fixtures() {
        for(String ddl:List.of(
            "operator_operational_report(id uuid,instance_country_id uuid,employee_id uuid,assignment_id uuid,point_id uuid,post_id uuid,report_type text,status text)",
            "operator_operational_report_decision(id uuid,instance_country_id uuid,report_id uuid,action text,resulting_status text,created_at timestamptz,actor_employee_id uuid)",
            "employee_operational_snapshot(employee_id uuid,instance_country_id uuid,persona_id bigint)",
            "operational_assignment(id uuid,instance_country_id uuid,shift_occurrence_id uuid)"))
            em.createNativeQuery("create temp table "+ddl+" on commit drop").executeUpdate();
        em.createNativeQuery("create temp table smc_fact_delivery (like public.smc_fact_delivery including all) on commit drop").executeUpdate();
        insert("employee_operational_snapshot",employee,tenant,123L);
        insert("operational_assignment",assignment,tenant,shift);
    }
    UUID report(UUID pe,String status,String type,boolean audit) {
        UUID id=UUID.randomUUID();
        insert("operator_operational_report",id,pe,employee,assignment,point,post,type,status);
        if(audit) approve(pe,id,since.plusSeconds(60));
        return id;
    }
    void approve(UUID pe,UUID report,Instant time) {
        insert("operator_operational_report_decision",UUID.randomUUID(),pe,report,"APPROVE","APPROVED",time,UUID.randomUUID());
    }
    List<SmcFactSources.Candidate> candidates(Instant start,UUID after,int limit) {
        var s=new SmcFactSources();s.em=em;
        return s.candidates(tenant,"NOVELTY_APPROVAL",start,after,Instant.now(),limit);
    }
    @Test void approvalIsOneFactForReporterWithValueOneAndApprovalTimestamp() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();UUID id=report(tenant,"APPROVED","INCIDENT",true);
            approve(tenant,id,since.plusSeconds(120));
            var result=candidates(since,null,100);assertEquals(1,result.size());
            var f=result.getFirst().fact();assertEquals(id,f.sourceId());assertEquals(employee,f.employeeId());
            assertEquals("NOVELTY_APPROVED",f.factType());assertEquals(BigDecimal.ONE,f.value());
            assertEquals(since.plusSeconds(60),f.occurredAt());assertEquals(f.occurredAt(),result.getFirst().changedAt());
            assertEquals(point,f.pointId());assertEquals(post,f.postId());assertEquals(shift,f.shiftId());
            assertEquals(123L,f.personaId());assertFalse(f.payload().containsKey("amount"));
            assertEquals(f.eventId(),candidates(since,null,100).getFirst().fact().eventId());
            em.createNativeQuery("""
                insert into smc_fact_delivery(id,instance_country_id,source_type,source_id,event_id,fact_type,payload_json)
                values(:id,:pe,'NOVELTY_APPROVAL',:origin,:event,'NOVELTY_APPROVED','{}')
                """).setParameter("id",UUID.randomUUID()).setParameter("pe",tenant).setParameter("origin",id)
                .setParameter("event",f.eventId()).executeUpdate();
            assertTrue(candidates(since,null,100).isEmpty());
        });
    }
    @Test void confirmedDiscardedUnauditedWrongTenantAndOutOfWindowAreExcluded() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();
            for(String status:List.of("PENDING","CONFIRMED","DISCARDED"))report(tenant,status,"INCIDENT",true);
            report(tenant,"APPROVED","INCIDENT",false);
            report(UUID.randomUUID(),"APPROVED","INCIDENT",true);
            UUID old=report(tenant,"APPROVED","INCIDENT",false);approve(tenant,old,since.minusSeconds(1));
            UUID future=report(tenant,"APPROVED","INCIDENT",false);approve(tenant,future,Instant.now().plusSeconds(600));
            UUID foreignAudit=report(tenant,"APPROVED","INCIDENT",false);approve(UUID.randomUUID(),foreignAudit,since.plusSeconds(60));
            assertTrue(candidates(since,null,100).isEmpty());
        });
    }
    @Test void allThreeReportTypesAndEqualTimestampPaginationKeepEveryApproval() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();for(String type:List.of("INCIDENT","FINDING","VULNERABILITY"))report(tenant,"APPROVED",type,true);
            var first=candidates(since,null,1);assertEquals(1,first.size());
            var rest=candidates(first.getFirst().changedAt(),first.getFirst().fact().sourceId(),100);
            assertEquals(2,rest.size());var ids=new HashSet<UUID>();ids.add(first.getFirst().fact().eventId());
            rest.forEach(c->ids.add(c.fact().eventId()));assertEquals(3,ids.size());
        });
    }
    void insert(String table,Object... values) {
        var q=em.createNativeQuery("insert into "+table+" values("+String.join(",",Collections.nCopies(values.length,"?"))+")");
        for(int i=0;i<values.length;i++)q.setParameter(i+1,values[i]);q.executeUpdate();
    }

    @Test void databaseFieldsReachHttpUnchangedAndRetryKeepsOriginalSnapshot() throws Exception {
        var directory=java.nio.file.Files.createTempDirectory("sgi-smc-db-http-");
        String key="sgi.m2m.client-id.SMC",previous=System.getProperty(key);
        System.setProperty(key,"sgi-com-to-smc-local-test");
        try(var h=new SmcOpenBaoTest.Harness(directory,"OAUTH2_CLIENT_CREDENTIALS","vault:kv/data/local-test/smc")) {
            tenant=h.pe;
            var received=new java.util.concurrent.CopyOnWriteArrayList<com.fasterxml.jackson.databind.JsonNode>();
            var keys=new java.util.concurrent.CopyOnWriteArrayList<String>();
            h.server.removeContext("/api/v1/operational-facts");
            h.server.createContext("/api/v1/operational-facts",exchange->{
                received.add(h.json.readTree(exchange.getRequestBody()));keys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
                SmcOpenBaoTest.reply(exchange,received.size()==1?503:201,received.size()==1?"{}":"{\"accepted\":true,\"smcFactId\":\"local-receipt\"}");
            });
            QuarkusTransaction.requiringNew().run(()->{
                fixtures();long person=java.util.concurrent.ThreadLocalRandom.current().nextLong(10000,Long.MAX_VALUE);
                em.createNativeQuery("update employee_operational_snapshot set persona_id=:person").setParameter("person",person).executeUpdate();
                UUID reportId=report(tenant,"APPROVED","FINDING",true);
                var fact=candidates(since,null,100).getFirst().fact();
                String payload;
                try {payload=h.json.writeValueAsString(fact.payload());}catch(Exception e){throw new RuntimeException(e);}
                em.createNativeQuery("""
                    insert into smc_fact_delivery(id,instance_country_id,source_type,source_id,event_id,fact_type,payload_json)
                    values(:id,:pe,'NOVELTY_APPROVAL',:origin,:event,'NOVELTY_APPROVED',:payload)
                    """).setParameter("id",UUID.randomUUID()).setParameter("pe",tenant).setParameter("origin",reportId)
                    .setParameter("event",fact.eventId()).setParameter("payload",payload).executeUpdate();
                var publisher=new SmcFactPublisher();publisher.em=em;publisher.mapper=h.json;publisher.adapter=h.adapter;
                publisher.sources=new SmcFactSources();publisher.sources.em=em;
                assertTrue(publisher.deliverOne(tenant));
                // Modificar el read model después no debe sustituir la identidad del snapshot al reintentar.
                em.createNativeQuery("update employee_operational_snapshot set persona_id=persona_id-1").executeUpdate();
                em.createNativeQuery("update smc_fact_delivery set next_attempt_at=current_timestamp").executeUpdate();
                assertTrue(publisher.deliverOne(tenant));assertFalse(publisher.deliverOne(tenant));
                assertEquals(2,received.size());assertEquals(received.get(0),received.get(1));assertEquals(keys.get(0),keys.get(1));
                var body=received.getFirst();assertEquals(employee.toString(),body.path("employeeId").asText());
                assertEquals(tenant.toString(),body.path("instanceCountryId").asText());assertEquals(person,body.path("persona_id").asLong());
                assertEquals(point.toString(),body.path("pointId").asText());assertEquals(post.toString(),body.path("postId").asText());
                assertEquals(shift.toString(),body.path("shiftId").asText());assertEquals(since.plusSeconds(60).toString(),body.path("occurredAt").asText());
                assertEquals(reportId.toString(),body.path("metadata").path("sourceId").asText());
                assertFalse(body.has("amount"));assertFalse(body.has("draw"));assertEquals("SENT",em.createNativeQuery("select status from smc_fact_delivery").getSingleResult());
            });
        } finally {
            if(previous==null)System.clearProperty(key);else System.setProperty(key,previous);
            java.nio.file.Files.deleteIfExists(directory.resolve("secret_id"));
            java.nio.file.Files.deleteIfExists(directory.resolve("role_id"));
            java.nio.file.Files.deleteIfExists(directory);
        }
    }
}
