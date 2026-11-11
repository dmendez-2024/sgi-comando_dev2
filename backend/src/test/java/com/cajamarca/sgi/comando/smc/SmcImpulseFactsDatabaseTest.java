package com.cajamarca.sgi.comando.smc;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** PostgreSQL real y tablas temporales; nunca modifica datos operativos. */
@QuarkusTest
class SmcImpulseFactsDatabaseTest {
    @Inject EntityManager em;
    final UUID tenant=UUID.randomUUID(), employee=UUID.randomUUID(), assignment=UUID.randomUUID();
    final UUID point=UUID.randomUUID(), post=UUID.randomUUID(), shift=UUID.randomUUID(), relief=UUID.randomUUID();
    final Instant since=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS).minusSeconds(86400);
    final String[] rules={"REL_A_TIEMPO","REL_FOTO_CUERPO","BIT_CA_CORRECTO","PAT_CORRECTA","CON_CORRECTA"};
    final String[] sources={"RELIEF","RELIEF","LOGBOOK","PATROL","CONSIGNMENT"};
    final Set<String> types=Set.of("RELIEF_COMPLETED_ON_TIME","RELIEF_FULL_BODY_PHOTO","ACCESS_CONTROL_OK","PATROL_OK","CONSIGNMENT_OK");

    void fixtures() {
        for(String ddl:List.of(
            "impulse_evaluation(id uuid,instance_country_id uuid,employee_id uuid,assignment_id uuid,occurred_at timestamptz,evaluated_at timestamptz,rule_code text,rule_version integer,source_type text,source_id uuid,decision text,amount numeric)",
            "employee_operational_snapshot(employee_id uuid,instance_country_id uuid,persona_id bigint)",
            "operational_assignment(id uuid,instance_country_id uuid,shift_occurrence_id uuid)",
            "shift_occurrence(id uuid,instance_country_id uuid,post_id uuid)",
            "post(id uuid,instance_country_id uuid,point_id uuid)",
            "task_execution(id uuid,instance_country_id uuid,target_id uuid,target_type text,group_id uuid,patrol_execution_id uuid)",
            "visual_review(task_execution_id uuid,instance_country_id uuid,simulated boolean)",
            "consignment_evidence(id uuid,instance_country_id uuid,consignment_id uuid)",
            "consignment_compliance(id uuid,instance_country_id uuid,consignment_id uuid)"))
            em.createNativeQuery("create temp table "+ddl+" on commit drop").executeUpdate();
        em.createNativeQuery("create temp table smc_fact_delivery (like public.smc_fact_delivery including all) on commit drop").executeUpdate();
        insert("employee_operational_snapshot",employee,tenant,123L);
        insert("operational_assignment",assignment,tenant,shift);
        insert("shift_occurrence",shift,tenant,post);
        insert("post",post,tenant,point);
        for(int i=0;i<rules.length;i++) evaluation(tenant,assignment,since.plusSeconds(60),since.plusSeconds(120),
            rules[i],sources[i],i<2?relief:UUID.randomUUID(),i%2==0?"AWARDED":"NO_AWARD");
    }

    UUID evaluation(UUID pe,UUID assigned,Instant occurred,Instant evaluated,String rule,String source,UUID origin,String decision) {
        UUID id=UUID.randomUUID();
        insert("impulse_evaluation",id,pe,employee,assigned,occurred,evaluated,rule,2,source,origin,decision,
            "AWARDED".equals(decision)?5:0);
        return id;
    }

    List<SmcFactSources.Candidate> candidates(UUID pe,Instant start,UUID after,int limit) {
        var source=new SmcFactSources();source.em=em;
        return source.candidates(pe,"IMPULSE_EVALUATION",start,after,Instant.now(),limit);
    }

    @Test void fiveEligibleOccurrencesIncludeNoAwardAndKeepStableIdentityAndContext() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();var facts=candidates(tenant,since,null,100);
            assertEquals(5,facts.size());
            assertEquals(types,facts.stream().map(c->c.fact().factType()).collect(java.util.stream.Collectors.toSet()));
            assertEquals(5,facts.stream().map(c->c.fact().eventId()).distinct().count());
            assertEquals(facts.stream().map(c->c.fact().eventId()).toList(),
                candidates(tenant,since,null,100).stream().map(c->c.fact().eventId()).toList());
            for(var candidate:facts) {
                var fact=candidate.fact();assertEquals(BigDecimal.ONE,fact.value());
                assertEquals(point,fact.pointId());assertEquals(post,fact.postId());assertEquals(shift,fact.shiftId());
                assertEquals(123L,fact.personaId());assertEquals(since.plusSeconds(60),fact.occurredAt());
                assertEquals(since.plusSeconds(120),candidate.changedAt());
                assertEquals(fact.sourceId().toString(),fact.metadata().get("evaluationId"));
                assertFalse(fact.payload().containsKey("amount"));assertFalse(fact.metadata().containsKey("amount"));
                assertFalse(fact.metadata().containsKey("draw"));
            }
        });
    }

    @Test void excludesInvalidFactsAndOtherInstancesAndRetainsCursorPagination() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();Instant occurred=since.plusSeconds(60),evaluated=since.plusSeconds(120);
            evaluation(tenant,assignment,occurred,evaluated,"PAT_CORRECTA","PATROL",UUID.randomUUID(),"NOT_ELIGIBLE");
            evaluation(UUID.randomUUID(),assignment,occurred,evaluated,"PAT_CORRECTA","PATROL",UUID.randomUUID(),"AWARDED");
            evaluation(tenant,assignment,occurred,since.minusSeconds(1),"PAT_CORRECTA","PATROL",UUID.randomUUID(),"AWARDED");
            evaluation(tenant,assignment,occurred,Instant.now().plusSeconds(600),"PAT_CORRECTA","PATROL",UUID.randomUUID(),"AWARDED");
            evaluation(tenant,assignment,occurred,evaluated,"NOV_APROBADA","NOVELTY",UUID.randomUUID(),"AWARDED");
            evaluation(tenant,assignment,occurred,evaluated,"PAT_CORRECTA","LOGBOOK",UUID.randomUUID(),"AWARDED");
            assertEquals(5,candidates(tenant,since,null,100).size());
            var first=candidates(tenant,since,null,2);assertEquals(2,first.size());
            var last=first.getLast();var rest=candidates(tenant,last.changedAt(),last.fact().sourceId(),100);
            assertEquals(3,rest.size());
            var ids=new HashSet<UUID>();first.forEach(c->ids.add(c.fact().eventId()));rest.forEach(c->ids.add(c.fact().eventId()));
            assertEquals(5,ids.size());
        });
    }

    @Test void deliveryDeduplicatesOneEvaluationWithoutDroppingOtherFactOfSameRelief() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();var facts=candidates(tenant,since,null,100);
            var punctual=facts.stream().map(SmcFactSources.Candidate::fact).filter(f->f.factType().equals("RELIEF_COMPLETED_ON_TIME")).findFirst().orElseThrow();
            em.createNativeQuery("""
                insert into smc_fact_delivery(id,instance_country_id,source_type,source_id,event_id,fact_type,payload_json)
                values(:id,:tenant,:source,:origin,:event,:type,'{}')
                """).setParameter("id",UUID.randomUUID()).setParameter("tenant",tenant).setParameter("source",punctual.sourceType())
                .setParameter("origin",punctual.sourceId()).setParameter("event",punctual.eventId()).setParameter("type",punctual.factType()).executeUpdate();
            var remaining=candidates(tenant,since,null,100);assertEquals(4,remaining.size());
            assertTrue(remaining.stream().anyMatch(c->c.fact().factType().equals("RELIEF_FULL_BODY_PHOTO")));
            assertTrue(remaining.stream().noneMatch(c->c.fact().eventId().equals(punctual.eventId())));
            em.createNativeQuery("update impulse_evaluation set assignment_id=null").executeUpdate();
            for(var c:candidates(tenant,since,null,100)) {
                assertNull(c.fact().pointId());assertNull(c.fact().postId());assertNull(c.fact().shiftId());
            }
        });
    }

    void insert(String table,Object... values) {
        var q=em.createNativeQuery("insert into "+table+" values("+String.join(",",Collections.nCopies(values.length,"?"))+")");
        for(int i=0;i<values.length;i++)q.setParameter(i+1,values[i]);q.executeUpdate();
    }

    @Test void simulatedEvidenceNeverBecomesAnExportableImpulse() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();
            for(String type:List.of("PATROL","LOGBOOK","CONSIGNMENT")) {
                Object[] e=(Object[])em.createNativeQuery("select id,source_id from impulse_evaluation where source_type=:type")
                    .setParameter("type",type).getSingleResult();
                UUID task=UUID.randomUUID(),target=UUID.randomUUID();
                insert("task_execution",task,tenant,target,"LOGBOOK".equals(type)?"LOGBOOK_FIELD":"CONSIGNMENT_EVIDENCE",
                    "LOGBOOK".equals(type)?e[1]:assignment,"PATROL".equals(type)?e[1]:null);
                if("CONSIGNMENT".equals(type)) {
                    UUID consignment=UUID.randomUUID();
                    insert("consignment_compliance",e[1],tenant,consignment);
                    insert("consignment_evidence",target,tenant,consignment);
                }
                insert("visual_review",task,tenant,true);
                var s=new SmcFactSources();s.em=em;
                assertTrue(s.hasSimulatedEvidence(tenant,"IMPULSE_EVALUATION",(UUID)e[0]));
            }
            assertEquals(2,candidates(tenant,since,null,100).size());
            em.createNativeQuery("update visual_review set simulated=false").executeUpdate();
            assertEquals(5,candidates(tenant,since,null,100).size());
            em.createNativeQuery("update visual_review set simulated=true,instance_country_id=:other")
                .setParameter("other",UUID.randomUUID()).executeUpdate();
            assertEquals(5,candidates(tenant,since,null,100).size());
        });
    }

    @Test void previouslyQueuedSimulationIsRejectedBeforeAnyHttpCall() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();var fact=candidates(tenant,since,null,100).stream().map(SmcFactSources.Candidate::fact)
                .filter(f->f.factType().equals("PATROL_OK")).findFirst().orElseThrow();
            UUID origin=(UUID)em.createNativeQuery("select source_id from impulse_evaluation where id=:id")
                .setParameter("id",fact.sourceId()).getSingleResult();
            UUID task=UUID.randomUUID();insert("task_execution",task,tenant,UUID.randomUUID(),"PATROL_CHECKPOINT",null,origin);
            insert("visual_review",task,tenant,true);
            em.createNativeQuery("""
                insert into smc_fact_delivery(id,instance_country_id,source_type,source_id,event_id,fact_type,payload_json)
                values(:id,:tenant,'IMPULSE_EVALUATION',:origin,:event,'PATROL_OK','{}')
                """).setParameter("id",UUID.randomUUID()).setParameter("tenant",tenant).setParameter("origin",fact.sourceId())
                .setParameter("event",fact.eventId()).executeUpdate();
            var publisher=new SmcFactPublisher();publisher.em=em;publisher.sources=new SmcFactSources();publisher.sources.em=em;
            // No se proporciona adaptador HTTP: invocarlo haría fallar esta prueba.
            assertTrue(publisher.deliverOne(tenant));
            Object[] state=(Object[])em.createNativeQuery("select status,last_error,attempts from smc_fact_delivery").getSingleResult();
            assertEquals("REJECTED",state[0]);assertEquals("SIMULATED_OPERATIONAL_SOURCE",state[1]);assertEquals(1,((Number)state[2]).intValue());
        });
    }

    @Test void simulatedPatrolCannotExportPerformanceAndRealCheckpointCountsAreCalculated() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();
            for(String ddl:List.of(
                "patrol_execution(id uuid,instance_country_id uuid,employee_id uuid,finished_at timestamptz,updated_at timestamptz,patrol_plan_id uuid,patrol_definition_id uuid,assignment_id uuid,result text)",
                "patrol_plan(id uuid,instance_country_id uuid,patrol_definition_id uuid,post_id uuid)",
                "patrol_definition(id uuid,instance_country_id uuid,point_id uuid,version_no integer,structure_type text,schedule_type text)",
                "patrol_checkpoint(id uuid,instance_country_id uuid,patrol_definition_id uuid)",
                "patrol_checkpoint_execution(instance_country_id uuid,checkpoint_id uuid,patrol_execution_id uuid,result text)"))
                em.createNativeQuery("create temp table "+ddl+" on commit drop").executeUpdate();
            UUID run=UUID.randomUUID(),definition=UUID.randomUUID(),checkpoint=UUID.randomUUID();
            insert("patrol_execution",run,tenant,employee,since.plusSeconds(60),since.plusSeconds(120),null,definition,assignment,"COMPLETA");
            insert("patrol_definition",definition,tenant,point,2,"CLOSED","PROGRAMMED");
            insert("patrol_checkpoint",checkpoint,tenant,definition);
            insert("patrol_checkpoint",UUID.randomUUID(),tenant,definition);
            insert("patrol_checkpoint_execution",tenant,checkpoint,run,"CUMPLIDO");
            var source=new SmcFactSources();source.em=em;
            var real=source.candidates(tenant,"PATROL_EXECUTION",since,null,Instant.now(),100);
            assertEquals(1,real.size());assertEquals(0,new BigDecimal("50").compareTo(real.getFirst().fact().value()));
            UUID task=UUID.randomUUID();insert("task_execution",task,tenant,checkpoint,"PATROL_CHECKPOINT",null,run);
            insert("visual_review",task,tenant,true);
            assertTrue(source.candidates(tenant,"PATROL_EXECUTION",since,null,Instant.now(),100).isEmpty());
            assertTrue(source.hasSimulatedEvidence(tenant,"PATROL_EXECUTION",run));
            assertFalse(source.hasSimulatedEvidence(UUID.randomUUID(),"PATROL_EXECUTION",run));
        });
    }
}
