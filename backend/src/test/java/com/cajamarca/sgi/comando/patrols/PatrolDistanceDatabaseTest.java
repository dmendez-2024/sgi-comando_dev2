package com.cajamarca.sgi.comando.patrols;

import com.cajamarca.sgi.comando.smc.SmcFactSources;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PatrolDistanceDatabaseTest {
    @Inject EntityManager em;
    @Inject SmcFactSources sources;
    @Inject PatrolDistanceService configuredDistance;
    final UUID tenant=UUID.randomUUID(),run=UUID.randomUUID(),employee=UUID.randomUUID();
    final Instant start=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS).minusSeconds(3600),end=start.plusSeconds(120);
    void fixtures() {
        em.createNativeQuery("create temp table patrol_execution(id uuid,instance_country_id uuid,employee_id uuid,started_at timestamptz,finished_at timestamptz,assignment_id uuid) on commit drop").executeUpdate();
        for(String table:List.of("patrol_gps_sample","patrol_distance_measurement","smc_fact_delivery"))
            em.createNativeQuery("create temp table "+table+" (like public."+table+" including all) on commit drop").executeUpdate();
        for(String ddl:List.of("employee_operational_snapshot(employee_id uuid,instance_country_id uuid,persona_id bigint)",
            "operational_assignment(id uuid,instance_country_id uuid,shift_occurrence_id uuid)","shift_occurrence(id uuid,instance_country_id uuid,post_id uuid)","post(id uuid,instance_country_id uuid,point_id uuid)"))
            em.createNativeQuery("create temp table "+ddl+" on commit drop").executeUpdate();
        em.createNativeQuery("insert into patrol_execution values(:run,:tenant,:employee,:start,:end,null)")
            .setParameter("run",run).setParameter("tenant",tenant).setParameter("employee",employee).setParameter("start",start).setParameter("end",end).executeUpdate();
    }
    PatrolDistanceService service(boolean configured) {
        var service=new PatrolDistanceService();service.em=em;service.maxAccuracy=configured?Optional.of(25d):Optional.empty();service.maxGap=configured?Optional.of(180L):Optional.empty();return service;
    }
    com.fasterxml.jackson.databind.JsonNode samples() {
        var mapper=new ObjectMapper();var points=mapper.createArrayNode();
        points.addObject().put("capturedAt",start.toString()).put("latitude",0).put("longitude",0).put("accuracyMeters",5);
        points.addObject().put("capturedAt",end.toString()).put("latitude",0).put("longitude",0.0009).put("accuracyMeters",5);
        return points;
    }
    long count(String table){return ((Number)em.createNativeQuery("select count(*) from "+table).getSingleResult()).longValue();}

    @Test void applicationConfigurationUsesApprovedInitialGpsLimits() {
        var policy=configuredDistance.policy().orElseThrow();
        assertEquals(25d,policy.maxAccuracyM());assertEquals(180L,policy.maxGapSeconds());
    }

    @Test void missingPolicyKeepsSamplesButNeverCreatesRewardThenCanMeasureExplicitly() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();service(false).complete(tenant,run,samples(),start,end);
            assertEquals(2,count("patrol_gps_sample"));assertEquals(0,count("patrol_distance_measurement"));
            assertThrows(jakarta.ws.rs.ServiceUnavailableException.class,()->service(false).measurePending(tenant,100));
            assertEquals(1,service(true).measurePending(tenant,100));assertEquals(0,service(true).measurePending(tenant,100));
        });
    }
    @Test void immutableMeasurementProducesKilometersAndDistinctIdempotentSmcFact() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();var service=service(true);service.complete(tenant,run,samples(),start,end);service.complete(tenant,run,samples(),start,end);
            assertEquals(2,count("patrol_gps_sample"));assertEquals(1,count("patrol_distance_measurement"));
            var facts=sources.candidates(tenant,"PATROL_DISTANCE",start,null,Instant.now().plusSeconds(5),100);
            assertEquals(1,facts.size());var fact=facts.getFirst().fact();assertEquals("PATROL_KM",fact.factType());
            assertEquals(0.100075,fact.value().doubleValue(),0.000002);assertEquals(employee,fact.employeeId());assertEquals(end,fact.occurredAt());
            assertEquals(fact.eventId(),sources.candidates(tenant,"PATROL_DISTANCE",start,null,Instant.now().plusSeconds(5),100).getFirst().fact().eventId());
            assertTrue(sources.candidates(UUID.randomUUID(),"PATROL_DISTANCE",start,null,Instant.now().plusSeconds(5),100).isEmpty());
        });
    }
    @Test void rejectsInvalidTelemetryAndNeverReusesOtherInstanceSamples() {
        QuarkusTransaction.requiringNew().run(()->{
            fixtures();var invalid=samples().deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)invalid.get(0)).put("latitude",100);
            assertThrows(jakarta.ws.rs.BadRequestException.class,()->service(true).complete(tenant,run,invalid,start,end));
            assertEquals(0,count("patrol_gps_sample"));
        });
    }
}
