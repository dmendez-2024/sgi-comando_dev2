package com.cajamarca.sgi.comando.smc;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SmcPerformanceFactsDatabaseTest {
    @Inject EntityManager em;
    @Inject DayOffWorkedService accreditation;
    final UUID tenant=UUID.randomUUID(),employee=UUID.randomUUID(),assignment=UUID.randomUUID();
    final UUID post=UUID.randomUUID(),point=UUID.randomUUID(),shift=UUID.randomUUID(),plan=UUID.randomUUID(),definition=UUID.randomUUID(),assignmentPlan=UUID.randomUUID();
    final Instant since=Instant.parse("2026-01-01T00:00:00Z"), start=since.plusSeconds(86400),end=start.plusSeconds(43200),changed=end.plusSeconds(3600);

    void fixtures() {
        // Usar la misma transacción de fixtures incluso al comprobar rechazos esperados.
        accreditation=new DayOffWorkedService();accreditation.em=em;
        accreditation.mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        for(String ddl:List.of(
            "employee_operational_snapshot(employee_id uuid,instance_country_id uuid,persona_id bigint)",
            "assignment_plan(id uuid,instance_country_id uuid,status text)",
            "operational_assignment(id uuid,instance_country_id uuid,shift_occurrence_id uuid,assignment_plan_id uuid,employee_id uuid,actual_employee_id uuid,status text,assigned_at timestamptz,actual_assigned_at timestamptz,created_at timestamptz)",
            "shift_occurrence(id uuid,instance_country_id uuid,post_id uuid,starts_at timestamptz,ends_at timestamptz)",
            "post(id uuid,instance_country_id uuid,point_id uuid)",
            "patrol_plan(id uuid,instance_country_id uuid,patrol_definition_id uuid,post_id uuid)",
            "patrol_definition(id uuid,instance_country_id uuid,point_id uuid,version_no integer,structure_type text,schedule_type text)",
            "patrol_occurrence(id uuid,instance_country_id uuid,patrol_plan_id uuid,window_start timestamptz,window_end timestamptz,status text,execution_id uuid,updated_at timestamptz)",
            "patrol_execution(id uuid,instance_country_id uuid,patrol_plan_id uuid,patrol_definition_id uuid,assignment_id uuid,employee_id uuid,occurrence_id uuid,started_at timestamptz,finished_at timestamptz,result text,updated_at timestamptz)",
            "patrol_checkpoint(id uuid,instance_country_id uuid,patrol_definition_id uuid)",
            "patrol_checkpoint_execution(id uuid,instance_country_id uuid,patrol_execution_id uuid,checkpoint_id uuid,result text)",
            "task_execution(id uuid,instance_country_id uuid,patrol_execution_id uuid)",
            "visual_review(task_execution_id uuid,instance_country_id uuid,simulated boolean)"))
            em.createNativeQuery("create temp table "+ddl+" on commit drop").executeUpdate();
        for(String table:List.of("smc_fact_delivery","day_off_worked_accreditation","smc_source_cursor"))
            em.createNativeQuery("create temp table "+table+" (like public."+table+" including all) on commit drop").executeUpdate();
        insert("employee_operational_snapshot",employee,tenant,98765L);
        insert("assignment_plan",assignmentPlan,tenant,"PUBLISHED");
        insert("operational_assignment",assignment,tenant,shift,assignmentPlan,employee,null,"ASSIGNED",since,null,since);
        insert("shift_occurrence",shift,tenant,post,start,end);
        insert("post",post,tenant,point);
        insert("patrol_plan",plan,tenant,definition,post);
        insert("patrol_definition",definition,tenant,point,3,"CLOSED","PROGRAMMED");
    }
    void insert(String table,Object... values) {
        var q=em.createNativeQuery("insert into "+table+" values("+String.join(",",Collections.nCopies(values.length,"?"))+")");
        for(int i=0;i<values.length;i++)q.setParameter(i+1,values[i]);q.executeUpdate();
    }
    List<SmcFactSources.Candidate> candidates(String type,Instant from,UUID after,int limit) {
        var s=new SmcFactSources();s.em=em;return s.candidates(tenant,type,from,after,Instant.now(),limit);
    }
    List<SmcFactSources.Candidate> candidates(String type) {return candidates(type,since,null,100);}
    UUID occurrence(UUID pe,String status,Instant windowEnd) {
        UUID id=UUID.randomUUID();insert("patrol_occurrence",id,pe,plan,start.plusSeconds(3600),windowEnd,status,null,changed);return id;
    }
    UUID execution(UUID occurrence,String result,Instant finished) {
        UUID id=UUID.randomUUID();insert("patrol_execution",id,tenant,plan,definition,assignment,employee,occurrence,start.plusSeconds(3700),finished,result,changed);return id;
    }
    void delivered(SmcFact f) {
        em.createNativeQuery("""
            insert into smc_fact_delivery(id,instance_country_id,source_type,source_id,event_id,fact_type,payload_json)
            values(:id,:tenant,:type,:source,:event,:fact,'{}')
            """).setParameter("id",UUID.randomUUID()).setParameter("tenant",tenant).setParameter("type",f.sourceType())
            .setParameter("source",f.sourceId()).setParameter("event",f.eventId()).setParameter("fact",f.factType()).executeUpdate();
    }
    @Test void explicitlyClosedExecutionProducesZeroEvenWithoutCheckpointsAndKeepsIdentity() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();UUID id=execution(null,"NO_EJECUTADA",end);
            execution(null,"NO_EJECUTADA",null); // no cierre final
            var result=candidates("PATROL_EXECUTION");assertEquals(1,result.size());
            var f=result.getFirst().fact();assertEquals(id,f.sourceId());assertEquals(BigDecimal.ZERO,f.value());
            assertEquals("ROUTE_COMPLETION",f.factType());assertEquals(end,f.occurredAt());assertEquals(employee,f.employeeId());
            assertEquals(f.eventId(),candidates("PATROL_EXECUTION").getFirst().fact().eventId());
            delivered(f);assertTrue(candidates("PATROL_EXECUTION").isEmpty());
        });
    }
    @Test void completedAndIncompletePatrolsKeepTheirPercentages() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();UUID a=execution(null,"INCOMPLETA",end),b=execution(null,"COMPLETA",end);
            UUID k1=UUID.randomUUID(),k2=UUID.randomUUID();
            insert("patrol_checkpoint",k1,tenant,definition);insert("patrol_checkpoint",k2,tenant,definition);
            insert("patrol_checkpoint_execution",UUID.randomUUID(),tenant,a,k1,"CUMPLIDO");
            for(UUID k:List.of(k1,k2))insert("patrol_checkpoint_execution",UUID.randomUUID(),tenant,b,k,"CUMPLIDO");
            var facts=candidates("PATROL_EXECUTION");assertEquals(2,facts.size());
            assertEquals(Set.of(new BigDecimal("50.00"),new BigDecimal("100.00")),facts.stream().map(c->c.fact().value()).collect(java.util.stream.Collectors.toSet()));
        });
    }
    @Test void missedOccurrenceHasUniqueResponsibleAndStableZeroFact() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();UUID id=occurrence(tenant,"NO_EJECUTADA",start.plusSeconds(7200));
            occurrence(tenant,"PENDING",start.plusSeconds(7200));occurrence(UUID.randomUUID(),"NO_EJECUTADA",start.plusSeconds(7200));
            occurrence(tenant,"NO_EJECUTADA",Instant.now().plusSeconds(3600));
            var result=candidates("PATROL_NOT_EXECUTED");assertEquals(1,result.size());var f=result.getFirst().fact();
            assertEquals(id,f.sourceId());assertEquals(employee,f.employeeId());assertEquals(point,f.pointId());
            assertEquals(post,f.postId());assertEquals(shift,f.shiftId());assertEquals(BigDecimal.ZERO,f.value());
            assertEquals(start.plusSeconds(7200),f.occurredAt());assertEquals(changed,result.getFirst().changedAt());
            delivered(f);assertTrue(candidates("PATROL_NOT_EXECUTED").isEmpty());
        });
    }
    @Test void startedPatrolAndAmbiguousResponsibilityNeverProduceMissedOccurrence() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();UUID id=occurrence(tenant,"NO_EJECUTADA",start.plusSeconds(7200));
            execution(id,null,null);assertTrue(candidates("PATROL_NOT_EXECUTED").isEmpty());
            em.createNativeQuery("delete from patrol_execution").executeUpdate();
            insert("operational_assignment",UUID.randomUUID(),tenant,shift,assignmentPlan,UUID.randomUUID(),null,"ASSIGNED",since,null,since);
            assertTrue(candidates("PATROL_NOT_EXECUTED").isEmpty());
        });
    }
    @Test void cursorTiePaginationPreservesOccurrencesAndNewTypesAreAllowed() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();for(int i=0;i<3;i++)occurrence(tenant,"NO_EJECUTADA",start.plusSeconds(7200));
            var first=candidates("PATROL_NOT_EXECUTED",since,null,1);assertEquals(1,first.size());
            var rest=candidates("PATROL_NOT_EXECUTED",first.getFirst().changedAt(),first.getFirst().fact().sourceId(),100);
            assertEquals(2,rest.size());assertTrue(rest.stream().noneMatch(c->c.fact().sourceId().equals(first.getFirst().fact().sourceId())));
            for(String type:List.of("PATROL_NOT_EXECUTED","DAY_OFF_WORKED"))
                em.createNativeQuery("insert into smc_source_cursor(instance_country_id,source_type,changed_at) values(:t,:type,:at)")
                    .setParameter("t",tenant).setParameter("type",type).setParameter("at",since).executeUpdate();
        });
    }
    DayOffWorkedService.Request request(UUID a,String work) {
        return new DayOffWorkedService.Request(a,start.atZone(ZoneId.of("America/Guayaquil")).toLocalDate(),"RRHH:rest-test",work,"Trabajo efectivo acreditado");
    }
    @Test void accreditationIsAuditedIdempotentAndPublishesOneWithOriginalContext() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();var r=request(assignment,"OPS:attendance-test");
            var saved=accreditation.accredit(tenant,"director-test",r);assertFalse(saved.duplicate());
            var repeated=accreditation.accredit(tenant,"other-director",r);assertTrue(repeated.duplicate());assertEquals(saved.id(),repeated.id());
            var record=accreditation.get(tenant,saved.id());assertEquals("director-test",record.get("accreditedBy"));
            assertEquals("RRHH:rest-test",record.get("restEvidenceRef"));
            var f=candidates("DAY_OFF_WORKED").getFirst().fact();assertEquals("DAY_OFF_WORKED",f.factType());
            assertEquals(BigDecimal.ONE,f.value());assertEquals(saved.id(),f.sourceId());assertEquals(end,f.occurredAt());
            assertEquals(employee,f.employeeId());assertEquals(98765L,f.personaId());assertEquals(point,f.pointId());
            assertFalse(f.payload().toString().contains("attendance-test")); // no filtrar documentos al receptor
            delivered(f);assertTrue(candidates("DAY_OFF_WORKED").isEmpty());
            assertThrows(NotFoundException.class,()->accreditation.get(UUID.randomUUID(),saved.id()));
        });
    }
    @Test void conflictingEvidenceOrSecondShiftOnSameRestDayIsRejected() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();accreditation.accredit(tenant,"director-test",request(assignment,"OPS:one"));
            assertEquals(409,assertThrows(ClientErrorException.class,()->accreditation.accredit(tenant,"director-test",request(assignment,"OPS:changed"))).getResponse().getStatus());
            UUID other=UUID.randomUUID();insert("operational_assignment",other,tenant,shift,assignmentPlan,employee,null,"ASSIGNED",since,null,since);
            assertEquals(409,assertThrows(ClientErrorException.class,()->accreditation.accredit(tenant,"director-test",request(other,"OPS:two"))).getResponse().getStatus());
            assertEquals(1L,((Number)em.createNativeQuery("select count(*) from day_off_worked_accreditation").getSingleResult()).longValue());
        });
    }
    @Test void noAccreditationForFutureRemovedForeignOrInvalidDateAndMissingEvidence() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();assertThrows(NotFoundException.class,()->accreditation.accredit(UUID.randomUUID(),"director",request(assignment,"OPS:test")));
            assertThrows(BadRequestException.class,()->accreditation.accredit(tenant,"director",request(assignment," ")));
            var invalid=new DayOffWorkedService.Request(assignment,LocalDate.of(2030,1,1),"RRHH:test","OPS:test","Motivo");
            assertThrows(BadRequestException.class,()->accreditation.accredit(tenant,"director",invalid));
            em.createNativeQuery("update operational_assignment set status='REMOVED'").executeUpdate();
            assertThrows(BadRequestException.class,()->accreditation.accredit(tenant,"director",request(assignment,"OPS:test")));
            em.createNativeQuery("update operational_assignment set status='ASSIGNED'").executeUpdate();
            em.createNativeQuery("update shift_occurrence set ends_at=:at").setParameter("at",Instant.now().plusSeconds(3600)).executeUpdate();
            assertThrows(BadRequestException.class,()->accreditation.accredit(tenant,"director",request(assignment,"OPS:test")));
            assertEquals(0L,((Number)em.createNativeQuery("select count(*) from day_off_worked_accreditation").getSingleResult()).longValue());
        });
    }
}
