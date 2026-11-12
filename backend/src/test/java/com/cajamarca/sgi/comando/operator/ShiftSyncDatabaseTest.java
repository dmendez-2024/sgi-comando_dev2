package com.cajamarca.sgi.comando.operator;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ShiftSyncDatabaseTest {
    @Inject EntityManager em;
    @Inject com.cajamarca.sgi.comando.smc.SmcFactSources sources;
    final ObjectMapper json=new ObjectMapper();
    final UUID tenant=UUID.randomUUID(),employee=UUID.randomUUID(),assignment=UUID.randomUUID(),shift=UUID.randomUUID(),post=UUID.randomUUID(),point=UUID.randomUUID(),plan=UUID.randomUUID();
    final UUID consignment=UUID.randomUUID(),protocol=UUID.randomUUID(),patrol=UUID.randomUUID();
    final Instant start=Instant.now().minusSeconds(43200),end=Instant.now().minusSeconds(5);
    boolean changed=false;
    ShiftSyncService service;
    void fixtures() {
        for(String ddl:List.of(
            "operational_assignment(id uuid,instance_country_id uuid,employee_id uuid,actual_employee_id uuid,shift_occurrence_id uuid,assignment_plan_id uuid,status text)",
            "assignment_plan(id uuid,instance_country_id uuid,status text)",
            "shift_occurrence(id uuid,instance_country_id uuid,post_id uuid,starts_at timestamptz,ends_at timestamptz)",
            "post(id uuid,instance_country_id uuid,point_id uuid)",
            "patrol_execution(id uuid,instance_country_id uuid,assignment_id uuid,employee_id uuid,patrol_definition_id uuid,patrol_plan_id uuid,started_at timestamptz,finished_at timestamptz)",
            "patrol_plan(id uuid,instance_country_id uuid,patrol_definition_id uuid)",
            "task_execution(id uuid,instance_country_id uuid,assignment_id uuid,employee_id uuid)",
            "task_execution_evidence(task_execution_id uuid,evidence_id uuid)",
            "evidence_object(id uuid,instance_country_id uuid,assignment_id uuid,employee_id uuid,event_id uuid,status text)",
            "employee_operational_snapshot(employee_id uuid,instance_country_id uuid,persona_id bigint)",
            "consignment(id uuid,instance_country_id uuid,protocol_id uuid,point_id uuid,post_id uuid,status text,scope_type text,updated_at timestamptz,application_type text,application_days_json text,application_time_from time,application_time_to time,validity_from timestamptz,validity_until timestamptz)",
            "consignment_protocol(id uuid,instance_country_id uuid,status text,updated_at timestamptz)",
            "consignment_post_scope(instance_country_id uuid,consignment_id uuid,post_id uuid)",
            "patrol_definition(id uuid,instance_country_id uuid,protocol_id uuid,status text,structure_type text,schedule_type text,updated_at timestamptz,window_start time,window_end time)",
            "patrol_protocol(id uuid,instance_country_id uuid,status text,updated_at timestamptz)",
            "patrol_protocol_post_scope(instance_country_id uuid,protocol_id uuid,post_id uuid)"))
            em.createNativeQuery("create temp table "+ddl+" on commit drop").executeUpdate();
        for(String table:List.of("operator_shift_sync","operator_shift_patrol_omission","consignment_compliance","core_instance_country_snapshot","smc_fact_delivery"))
            em.createNativeQuery("create temp table "+table+" (like public."+table+" including all) on commit drop").executeUpdate();
        insert("assignment_plan",plan,tenant,"PUBLISHED");
        insert("operational_assignment",assignment,tenant,employee,null,shift,plan,"ASSIGNED");
        insert("shift_occurrence",shift,tenant,post,start,Instant.now().plusSeconds(3600));
        insert("post",post,tenant,point);
        insert("employee_operational_snapshot",employee,tenant,12345L);
        insert("consignment_protocol",protocol,tenant,"ACTIVO",start);
        insert("consignment",consignment,tenant,protocol,point,post,"VIGENTE","POINT",start,"ALWAYS","[]",null,null,null,null);
        service=new ShiftSyncService() {
            @Override ArrayNode expected(UUID t,Assignment a) {
                ArrayNode expected=json.createArrayNode();
                expected.addObject().put("kind","CONSIGNMENT").put("id",consignment.toString()).put("protocolId",protocol.toString()).put("version",changed?"changed":"v1");
                expected.addObject().put("kind","PATROL").put("id",patrol.toString()).put("protocolId",protocol.toString()).put("from",start.plusSeconds(3600).toString()).put("to",start.plusSeconds(7200).toString());
                return expected;
            }
        };
        service.em=em;service.mapper=json;
    }
    void insert(String table,Object...values) {
        var q=em.createNativeQuery("insert into "+table+" values("+String.join(",",Collections.nCopies(values.length,"?"))+")");
        for(int i=0;i<values.length;i++)q.setParameter(i+1,values[i]);q.executeUpdate();
    }
    ObjectNode request(String action) {return json.createObjectNode().put("action",action).put("assignmentId",assignment.toString()).put("deviceId","device-test").put("allAcknowledged",true).set("operations",json.createArrayNode());}
    JsonNode submit(ObjectNode r) {return service.submit(tenant,employee,"agent-test",r);}
    void prepareAndEnd() {assertEquals("PREPARED",submit(request("PREPARE")).path("status").asText());em.createNativeQuery("update shift_occurrence set ends_at=:at").setParameter("at",end).executeUpdate();}
    long count(String table) {return ((Number)em.createNativeQuery("select count(*) from "+table).getSingleResult()).longValue();}
    @Test void pendingTurnProducesNothingUntilFinalReceiptAndRepeatIsIdempotent() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();prepareAndEnd();assertEquals(0,count("consignment_compliance"));assertEquals(0,count("operator_shift_patrol_omission"));
            assertEquals("READY",submit(request("CHECK")).path("status").asText());
            assertEquals(0,count("consignment_compliance"));
            JsonNode receipt=submit(request("COMPLETE"));assertEquals("CLOSED",receipt.path("status").asText());
            assertEquals(1,receipt.path("patrolsNotExecuted").asInt());assertEquals(1,receipt.path("consignmentsNotDone").asInt());
            assertEquals("NO_CUMPLIDA",em.createNativeQuery("select result from consignment_compliance").getSingleResult());
            assertNull(em.createNativeQuery("select executed_at from consignment_compliance").getSingleResult());
            assertTrue(submit(request("COMPLETE")).path("duplicate").asBoolean());assertEquals(1,count("consignment_compliance"));
            assertThrows(ClientErrorException.class,()->service.ensureOpen(tenant,assignment));
        });
    }
    @Test void incompleteManifestWrongDeviceWrongAgentAndEarlyEndNeverCreateOmissions() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();submit(request("PREPARE"));
            assertThrows(ClientErrorException.class,()->submit(request("COMPLETE")));
            em.createNativeQuery("update shift_occurrence set ends_at=:at").setParameter("at",end).executeUpdate();
            assertThrows(ClientErrorException.class,()->submit(request("COMPLETE").put("allAcknowledged",false)));
            assertThrows(ClientErrorException.class,()->submit(request("COMPLETE").put("deviceId","other")));
            assertThrows(ForbiddenException.class,()->service.submit(tenant,UUID.randomUUID(),"wrong",request("COMPLETE")));
            ObjectNode incomplete=request("COMPLETE");((ArrayNode)incomplete.get("operations")).addObject().put("kind","PATRULLAS_HITO").put("id",UUID.randomUUID().toString());
            assertThrows(ClientErrorException.class,()->submit(incomplete));
            assertEquals(0,count("consignment_compliance"));assertEquals(0,count("operator_shift_patrol_omission"));
        });
    }
    @Test void missingPhotoOpenPatrolOrChangedConfigurationBlocksClosure() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();prepareAndEnd();UUID task=UUID.randomUUID();insert("task_execution",task,tenant,assignment,employee);
            ObjectNode body=request("COMPLETE");((ArrayNode)body.get("operations")).addObject().put("kind","PATRULLAS_HITO").put("id",task.toString());
            assertThrows(ClientErrorException.class,()->submit(body));
            changed=true;assertThrows(ClientErrorException.class,()->submit(request("COMPLETE")));changed=false;
            insert("patrol_execution",UUID.randomUUID(),tenant,assignment,employee,patrol,null,start.plusSeconds(4000),null);
            assertThrows(ClientErrorException.class,()->submit(request("COMPLETE")));assertEquals(0,count("consignment_compliance"));
        });
    }
    @Test void receivedActivitiesArePreservedAndDoNotBecomeOmissions() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();prepareAndEnd();
            insert("patrol_execution",UUID.randomUUID(),tenant,assignment,employee,patrol,null,start.plusSeconds(4000),start.plusSeconds(5000));
            em.createNativeQuery("""
                insert into consignment_compliance(id,instance_country_id,protocol_id,consignment_id,assignment_id,employee_id,
                username,result,payload_hash,payload_json,confirmed_at,executed_at)
                values(:id,:t,:p,:c,:a,:e,'agent','CUMPLIDA','hash','{}',current_timestamp,:at)
                """).setParameter("id",UUID.randomUUID()).setParameter("t",tenant).setParameter("p",protocol)
                .setParameter("c",consignment).setParameter("a",assignment).setParameter("e",employee).setParameter("at",start.plusSeconds(5000)).executeUpdate();
            var receipt=submit(request("COMPLETE"));assertEquals(0,receipt.path("patrolsNotExecuted").asInt());assertEquals(0,receipt.path("consignmentsNotDone").asInt());
            assertEquals("CUMPLIDA",em.createNativeQuery("select result from consignment_compliance").getSingleResult());
        });
    }
    @Test void preparationAfterEndAndClosureWithoutPreparationRequireReview() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();em.createNativeQuery("update shift_occurrence set ends_at=:at").setParameter("at",end).executeUpdate();
            assertThrows(ClientErrorException.class,()->submit(request("PREPARE")));
            assertThrows(ClientErrorException.class,()->submit(request("COMPLETE")));assertEquals(0,count("operator_shift_sync"));
        });
    }
    @Test void calendarHandlesNightWindowsWeekdaysAndBoundaryWithoutOverlap() {
        ZoneId zone=ZoneId.of("America/Guayaquil");Instant from=Instant.parse("2026-11-12T03:00:00Z"),to=Instant.parse("2026-11-12T11:00:00Z");
        var windows=ShiftSyncService.windows(from,to,LocalTime.of(23,30),LocalTime.of(0,30),Set.of("WED"),zone,true);
        assertEquals(1,windows.size());assertEquals(Instant.parse("2026-11-12T04:30:00Z"),windows.getFirst().start());
        assertTrue(ShiftSyncService.windows(from,to,LocalTime.of(23,30),LocalTime.of(0,30),Set.of("THU"),zone,true).isEmpty());
        assertTrue(ShiftSyncService.windows(from,to,LocalTime.of(6,0),LocalTime.of(7,0),Set.of(),zone,false).isEmpty());
        assertTrue(ShiftSyncService.windows(from,to,LocalTime.of(6,0),LocalTime.of(6,0),Set.of(),zone,false).isEmpty());
    }
    @Test void realSnapshotIncludesOnlyApplicableConfigurationAndDetectsChanges() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();var real=new ShiftSyncService();real.em=em;real.mapper=json;
            UUID valid=UUID.randomUUID(),invalid=UUID.randomUUID();
            insert("consignment",valid,tenant,protocol,point,post,"VIGENTE","POINT",start,"CALENDAR","[\"WED\"]",LocalTime.of(23,30),LocalTime.of(0,30),null,null);
            insert("consignment",invalid,tenant,protocol,point,post,"VIGENTE","POINT",start,"CALENDAR","[]",LocalTime.of(23,30),LocalTime.of(0,30),null,null);
            insert("patrol_protocol",protocol,tenant,"ACTIVO",start);
            insert("patrol_protocol_post_scope",tenant,protocol,post);
            insert("patrol_definition",patrol,tenant,protocol,"ACTIVO","CLOSED","PROGRAMMED",start,LocalTime.of(23,30),LocalTime.of(0,30));
            var a=new ShiftSyncService.Assignment(employee,point,post,shift,Instant.parse("2026-11-12T03:00:00Z"),Instant.parse("2026-11-12T11:00:00Z"));
            var expected=real.expected(tenant,a);assertEquals(4,expected.size());
            assertTrue(expected.findValuesAsText("id").contains(valid.toString()));assertFalse(expected.findValuesAsText("id").contains(invalid.toString()));
            em.createNativeQuery("update consignment_protocol set updated_at=current_timestamp").executeUpdate();
            assertNotEquals(expected,real.expected(tenant,a));
        });
    }
    @Test void publisherWaitsForReceiptAndDiscoversOldRecordsAfterClosureWithoutLosingCursorRange() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();prepareAndEnd();
            var before=sources.candidates(tenant,"SHIFT_PATROL_OMISSION",start,null,Instant.now(),100);assertTrue(before.isEmpty());
            em.createNativeQuery("""
                insert into consignment_compliance(id,instance_country_id,protocol_id,consignment_id,assignment_id,employee_id,
                username,result,payload_hash,payload_json,confirmed_at,created_at)
                values(:id,:t,:p,:c,:a,:e,'agent','NO_CUMPLIDA','hash','{}',:at,:at)
                """).setParameter("id",UUID.randomUUID()).setParameter("t",tenant).setParameter("p",protocol)
                .setParameter("c",consignment).setParameter("a",assignment).setParameter("e",employee).setParameter("at",start).executeUpdate();
            assertTrue(sources.candidates(tenant,"CONSIGNMENT_COMPLIANCE",start.minusSeconds(1),null,Instant.now(),100).isEmpty());
            submit(request("COMPLETE"));
            var omissions=sources.candidates(tenant,"SHIFT_PATROL_OMISSION",start,null,Instant.now(),100);assertEquals(1,omissions.size());
            assertEquals(0,omissions.getFirst().fact().value().intValue());
            var released=sources.candidates(tenant,"CONSIGNMENT_COMPLIANCE",Instant.now().minusSeconds(100),null,Instant.now(),100);
            assertEquals(1,released.size());assertTrue(released.getFirst().changedAt().isAfter(start));
        });
    }
}
