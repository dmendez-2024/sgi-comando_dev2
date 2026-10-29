package com.cajamarca.sgi.comando.dashboard;

import com.cajamarca.sgi.comando.common.TenantContext;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class DashboardResourceTest {
    @Inject EntityManager em;
    @Inject TenantContext tenant;
    UUID planId;
    UUID createdConsignmentProtocolId;
    UUID createdConsignmentId;
    UUID coordinationPostId;
    UUID supervisionRouteId;
    UUID supervisionRoutePointId;
    UUID supervisionShiftId;
    UUID supervisionAssignmentId;
    UUID supervisionEvidenceId;
    UUID supervisionEvaluationId;
    final List<UUID> shifts = new ArrayList<>();
    final List<UUID> assignments = new ArrayList<>();
    final List<UUID> reliefs = new ArrayList<>();
    final List<UUID> reports = new ArrayList<>();

    @AfterEach void cleanup() {
        QuarkusTransaction.requiringNew().run(() -> {
            if (supervisionEvaluationId != null) em.createNativeQuery("delete from supervisor_evaluation where id=:id").setParameter("id", supervisionEvaluationId).executeUpdate();
            if (supervisionEvidenceId != null) em.createNativeQuery("delete from evidence_object where id=:id").setParameter("id", supervisionEvidenceId).executeUpdate();
            if (supervisionAssignmentId != null) em.createNativeQuery("delete from operational_assignment where id=:id").setParameter("id", supervisionAssignmentId).executeUpdate();
            if (supervisionShiftId != null) em.createNativeQuery("delete from shift_occurrence where id=:id").setParameter("id", supervisionShiftId).executeUpdate();
            if (supervisionRoutePointId != null) em.createNativeQuery("delete from supervision_route_point where id=:id").setParameter("id", supervisionRoutePointId).executeUpdate();
            if (supervisionRouteId != null) em.createNativeQuery("delete from supervision_route where id=:id").setParameter("id", supervisionRouteId).executeUpdate();
            if (coordinationPostId != null) em.createNativeQuery("delete from coordination_post where id=:id").setParameter("id", coordinationPostId).executeUpdate();
            for (UUID id : reports) em.createNativeQuery("delete from operator_operational_report where id=:id").setParameter("id", id).executeUpdate();
            for (UUID id : assignments) em.createNativeQuery("delete from consignment_compliance where assignment_id=:id").setParameter("id", id).executeUpdate();
            if (createdConsignmentId != null) em.createNativeQuery("delete from consignment where id=:id").setParameter("id", createdConsignmentId).executeUpdate();
            if (createdConsignmentProtocolId != null) em.createNativeQuery("delete from consignment_protocol where id=:id").setParameter("id", createdConsignmentProtocolId).executeUpdate();
            for (UUID id : reliefs) em.createNativeQuery("delete from relief_event where id=:id").setParameter("id", id).executeUpdate();
            for (UUID id : shifts) {
                em.createNativeQuery("delete from operational_assignment where shift_occurrence_id=:id").setParameter("id", id).executeUpdate();
                em.createNativeQuery("delete from shift_occurrence where id=:id").setParameter("id", id).executeUpdate();
            }
            if (planId != null) em.createNativeQuery("delete from assignment_plan where id=:id").setParameter("id", planId).executeUpdate();
        });
    }

    @Test void weightsScoresAndCountsRecordedLateReliefsWithFilters() {
        UUID country = tenant.instanceCountryId();
        Object[] point = (Object[]) em.createNativeQuery("""
            select p.id, p.company_id, s.client_id, po.id from point p
            join service s on s.id=p.service_id join post po on po.point_id=p.id
            where p.instance_country_id=:tenant and p.company_id is not null order by p.code limit 1
            """).setParameter("tenant", country).getSingleResult();
        UUID pointId = (UUID)point[0], company = (UUID)point[1], client = (UUID)point[2], post = (UUID)point[3];
        planId = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("""
                insert into assignment_plan(id,instance_country_id,company_id,week_start,status,created_at,updated_at)
                values(:id,:tenant,:company,'2001-01-01','DRAFT',now(),now())
                """).setParameter("id", planId).setParameter("tenant", country).setParameter("company", company).executeUpdate();
            score(country, post, 1, 2, 50, "ASSIGNED");
            score(country, post, 3, 4, 100, "ASSIGNED");
            score(country, post, 10, 5, 100, "REMOVED");
            relief(country, post, "2001-01-02 05:01:00+00");
            relief(country, post, "2001-01-02 05:00:00+00");
            relief(country, post, null);
        });
        as("presidente").queryParam("weekStart", "2001-01-03").queryParam("companyId", company).queryParam("clientId", client)
            .get("/api/dashboard/metrics").then().statusCode(200).body("weekStart", is("2001-01-01"))
            .body("idAverage", is(3.5f)).body("icAverage", is(87.5f))
            .body("idSamples", is(2)).body("lateReliefs", is(1)).body("recordedReliefs", is(2));
        as("presidente").queryParam("weekStart", "2001-01-01").queryParam("clientId", UUID.randomUUID())
            .get("/api/dashboard/metrics").then().statusCode(200).body("idAverage", nullValue())
            .body("icAverage", nullValue()).body("lateReliefs", is(0));
        as("presidente").queryParam("weekStart", "2001-01-08").queryParam("companyId", company)
            .get("/api/dashboard/metrics").then().statusCode(200).body("idAverage", nullValue()).body("recordedReliefs", is(0));
        as("presidente").queryParam("weekStart", "2001-01-01").queryParam("clientId", UUID.randomUUID())
            .get("/api/assignments/coverage").then().statusCode(200).body("totalUncoveredPoints", is(0)).body("totalRequiredShifts", is(0));
        as("presidente").queryParam("weekStart", "2001-01-03").queryParam("companyId", company).queryParam("clientId", client)
            .get("/api/assignments/coverage").then().statusCode(200)
            .body("totalRequiredShifts", is(3)).body("totalAssignedShifts", is(2)).body("totalUncoveredPoints", is(1));
        as("presidente").queryParam("weekStart", "2001-01-03").queryParam("companyId", company).queryParam("clientId", client)
            .get("/api/dashboard/suitability").then().statusCode(200)
            .body("pointsBelowIdMinimum", is(1)).body("idEvaluatedPoints", is(1)).body("idGapPct", is(100.0f))
            .body("pointsBelowFullCompatibility", is(1)).body("icEvaluatedPoints", is(1)).body("icGapPct", is(100.0f));
        as("presidente").queryParam("weekStart", "2001-01-03").queryParam("clientId", UUID.randomUUID())
            .get("/api/dashboard/suitability").then().statusCode(200)
            .body("idEvaluatedPoints", is(0)).body("idGapPct", nullValue());
        int previousActive = as("presidente").queryParam("weekStart", "2001-01-03").queryParam("companyId", company).queryParam("clientId", client)
            .get("/api/dashboard/consignment-compliance").then().statusCode(200).extract().path("activeConsignments");
        int previousNonCompliant = as("presidente").queryParam("weekStart", "2001-01-03").queryParam("companyId", company).queryParam("clientId", client)
            .get("/api/dashboard/consignment-compliance").then().statusCode(200).extract().path("nonCompliant");
        QuarkusTransaction.requiringNew().run(() -> {
            consignment(country, pointId);
            compliance(country, assignments.get(0));
            compliance(country, assignments.get(1));
        });
        as("presidente").queryParam("weekStart", "2001-01-03").queryParam("companyId", company).queryParam("clientId", client)
            .get("/api/dashboard/consignment-compliance").then().statusCode(200)
            .body("activeConsignments", is(previousActive + 1)).body("nonCompliant", is(previousNonCompliant + 1))
            .body("nonCompliancePct", is(Math.round(1000f * (previousNonCompliant + 1) / (previousActive + 1)) / 10f));
        as("presidente").queryParam("weekStart", "2001-01-03").queryParam("companyId", company).queryParam("clientId", client)
            .get("/api/dashboard/consignment-compliance").then().statusCode(200)
            .body("attention.find { it.pointId == '" + pointId + "' }.nonCompliant", greaterThanOrEqualTo(1))
            .body("attention.find { it.pointId == '" + pointId + "' }.date", is("2001-01-02"));
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("update consignment set validity_from='2002-01-01 00:00:00+00' where id=:id")
            .setParameter("id", createdConsignmentId).executeUpdate());
        as("presidente").queryParam("weekStart", "2001-01-03").queryParam("companyId", company).queryParam("clientId", client)
            .get("/api/dashboard/consignment-compliance").then().statusCode(200)
            .body("activeConsignments", is(previousActive)).body("nonCompliant", is(previousNonCompliant));
        as("presidente").queryParam("weekStart", "2001-01-03").queryParam("clientId", UUID.randomUUID())
            .get("/api/dashboard/consignment-compliance").then().statusCode(200)
            .body("activeConsignments", is(0)).body("nonCompliancePct", nullValue()).body("attention", empty());
        as("presidente").queryParam("weekStart", "2001-01-03").queryParam("companyId", company).queryParam("clientId", client)
            .get("/api/dashboard/operational-risk-trend").then().statusCode(200)
            .body("find { it.date == '2001-01-02' }.scheduledPoints", is(1))
            .body("find { it.date == '2001-01-02' }.uncoveredPoints", is(1))
            .body("find { it.date == '2001-01-02' }.riskIndex", is(100.0f));
        as("presidente").queryParam("weekStart", "2001-01-03").queryParam("period", "7d")
            .get("/api/dashboard/operational-risk-trend").then().statusCode(200).body("size()", is(7));
        as("presidente").queryParam("weekStart", "2001-01-03").queryParam("period", "6m")
            .get("/api/dashboard/operational-risk-trend").then().statusCode(200).body("size()", greaterThan(170));
        as("presidente").queryParam("weekStart", "2001-01-03").queryParam("companyId", company).queryParam("clientId", client)
            .get("/api/dashboard/supervision-route-compliance").then().statusCode(200)
            .body("expectedVisits", is(0)).body("nonCompliancePct", nullValue());
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("update assignment_plan set status='PUBLISHED' where id=:id")
                .setParameter("id", planId).executeUpdate();
            for (int index = 0; index < 2; index++) em.createNativeQuery("update operational_assignment set employee_id=:employee where id=:id")
                .setParameter("employee", UUID.randomUUID()).setParameter("id", assignments.get(index)).executeUpdate();
            em.createNativeQuery("update relief_event set shift_occurrence_id=:shift where id=:id")
                .setParameter("shift", shifts.get(0)).setParameter("id", reliefs.get(0)).executeUpdate();
            em.createNativeQuery("update relief_event set executed_at=null where id=:id")
                .setParameter("id", reliefs.get(1)).executeUpdate();
            report(country, pointId, post, assignments.get(0), "INCIDENT", "CRITICAL");
            report(country, pointId, post, assignments.get(0), "FINDING", "CRITICAL");
        });
        as("presidente").queryParam("weekStart", "2001-01-03").queryParam("companyId", company).queryParam("clientId", client)
            .get("/api/dashboard/missed-reliefs").then().statusCode(200)
            .body("assignedShifts", is(2)).body("missedShifts", is(1));
        as("presidente").queryParam("weekStart", "2001-01-03").queryParam("companyId", company).queryParam("clientId", client)
            .get("/api/dashboard/critical-incidents").then().statusCode(200)
            .body("reportedCriticalIncidents", is(1));
        as("presidente").queryParam("weekStart", "2001-01-03").queryParam("clientId", UUID.randomUUID())
            .get("/api/dashboard/critical-incidents").then().statusCode(200)
            .body("reportedCriticalIncidents", is(0));
        QuarkusTransaction.requiringNew().run(() -> route(country, company, pointId));
        as("presidente").queryParam("weekStart", "2001-01-03").queryParam("companyId", company).queryParam("clientId", client)
            .get("/api/dashboard/supervision-route-compliance").then().statusCode(200)
            .body("expectedVisits", is(1)).body("missedVisits", is(1)).body("nonCompliancePct", is(100.0f));
        QuarkusTransaction.requiringNew().run(() -> completedVisit(country, pointId, post, assignments.get(0)));
        as("presidente").queryParam("weekStart", "2001-01-03").queryParam("companyId", company).queryParam("clientId", client)
            .get("/api/dashboard/supervision-route-compliance").then().statusCode(200)
            .body("expectedVisits", is(1)).body("missedVisits", is(0)).body("nonCompliancePct", is(0.0f));
    }

    @Test void validatesDatesAndCompanyScope() {
        as("presidente").queryParam("weekStart", "invalid").get("/api/dashboard/metrics").then().statusCode(400);
        as("presidente").queryParam("period", "invalid").get("/api/dashboard/operational-risk-trend").then().statusCode(400);
        as("coord").queryParam("companyId", UUID.randomUUID()).get("/api/dashboard/metrics").then().statusCode(403);
        as("coord").queryParam("companyId", UUID.randomUUID()).get("/api/dashboard/operational-risk-trend").then().statusCode(403);
        as("agente").get("/api/dashboard/metrics").then().statusCode(403);
        as("coord").queryParam("companyId", UUID.randomUUID()).get("/api/assignments/coverage").then().statusCode(403);
    }

    private void score(UUID country, UUID post, int hours, int id, int ic, String status) {
        UUID shift = UUID.randomUUID(); shifts.add(shift);
        em.createNativeQuery("""
            insert into shift_occurrence(id,instance_country_id,post_id,starts_at,ends_at,required,created_at,updated_at)
            values(:id,:tenant,:post,'2001-01-02 05:00:00+00',timestamptz '2001-01-02 05:00:00+00'+:hours*interval '1 hour',true,now(),now())
            """).setParameter("id", shift).setParameter("tenant", country).setParameter("post", post).setParameter("hours", hours).executeUpdate();
        UUID assignment = UUID.randomUUID(); assignments.add(assignment);
        em.createNativeQuery("""
            insert into operational_assignment(id,instance_country_id,assignment_plan_id,shift_occurrence_id,id_score,compatibility_index,status,created_at,updated_at)
            values(:id,:tenant,:plan,:shift,:score,:ic,:status,now(),now())
            """).setParameter("id", assignment).setParameter("tenant", country).setParameter("plan", planId)
            .setParameter("shift", shift).setParameter("score", id).setParameter("ic", ic).setParameter("status", status).executeUpdate();
    }
    private void consignment(UUID country, UUID point) {
        List<?> existing = em.createNativeQuery("select id from consignment_protocol where instance_country_id=:tenant and point_id=:point and status='ACTIVO' limit 1")
            .setParameter("tenant", country).setParameter("point", point).getResultList();
        UUID protocol;
        if (existing.isEmpty()) {
            protocol = UUID.randomUUID(); createdConsignmentProtocolId = protocol;
            em.createNativeQuery("""
                insert into consignment_protocol(id,instance_country_id,series_id,point_id,code,name,status,version_no,updated_by_username,created_at,updated_at)
                values(:id,:tenant,:id,:point,:code,'Test dashboard','ACTIVO',1,'test',now(),now())
                """).setParameter("id", protocol).setParameter("tenant", country).setParameter("point", point)
                .setParameter("code", "TEST-" + protocol.toString().substring(0, 8)).executeUpdate();
        } else protocol = (UUID) existing.get(0);
        createdConsignmentId = UUID.randomUUID();
        em.createNativeQuery("""
            insert into consignment(id,instance_country_id,protocol_id,code,point_id,title,instruction,priority,status,validity_type,application_type,created_at,updated_at)
            values(:id,:tenant,:protocol,:code,:point,'Test dashboard','Test dashboard','LOW','VIGENTE','PERMANENT','ALL_TIME',now(),now())
            """).setParameter("id", createdConsignmentId).setParameter("tenant", country).setParameter("protocol", protocol)
            .setParameter("code", "TEST-" + createdConsignmentId.toString().substring(0, 8)).setParameter("point", point).executeUpdate();
    }
    private void compliance(UUID country, UUID assignment) {
        UUID protocol = createdConsignmentProtocolId;
        if (protocol == null) protocol = (UUID) em.createNativeQuery("select protocol_id from consignment where id=:id")
            .setParameter("id", createdConsignmentId).getSingleResult();
        em.createNativeQuery("""
            insert into consignment_compliance(id,instance_country_id,protocol_id,consignment_id,assignment_id,employee_id,username,result,payload_hash,payload_json,confirmed_at)
            values(:id,:tenant,:protocol,:consignment,:assignment,:employee,'test','NO_CUMPLIDA',:hash,'{}','2001-01-02 05:00:00+00')
            """).setParameter("id", UUID.randomUUID()).setParameter("tenant", country).setParameter("protocol", protocol)
            .setParameter("consignment", createdConsignmentId).setParameter("assignment", assignment)
            .setParameter("employee", UUID.randomUUID()).setParameter("hash", UUID.randomUUID().toString().replace("-", "")).executeUpdate();
    }
    private void relief(UUID country, UUID post, String executedAt) {
        UUID id = UUID.randomUUID(); reliefs.add(id);
        em.createNativeQuery("""
            insert into relief_event(id,instance_country_id,post_id,status,planned_at,executed_at,created_at,updated_at)
            values(:id,:tenant,:post,'PENDIENTE','2001-01-02 05:00:00+00',cast(:executed as timestamptz),now(),now())
            """).setParameter("id", id).setParameter("tenant", country).setParameter("post", post).setParameter("executed", executedAt).executeUpdate();
    }
    private void report(UUID country, UUID point, UUID post, UUID assignment, String type, String severity) {
        UUID id = UUID.randomUUID(); reports.add(id);
        em.createNativeQuery("""
            insert into operator_operational_report(id,instance_country_id,assignment_id,employee_id,username,point_id,post_id,
                report_type,category,title,description,severity,status,occurred_at,source,created_at,updated_at)
            values(:id,:tenant,:assignment,:employee,'test',:point,:post,:type,'Test','Test dashboard','Test dashboard',
                :severity,'REPORTED','2001-01-02 05:00:00+00','SGI_OPERADOR_LOCAL',now(),now())
            """).setParameter("id", id).setParameter("tenant", country).setParameter("assignment", assignment)
            .setParameter("employee", UUID.randomUUID()).setParameter("point", point).setParameter("post", post)
            .setParameter("type", type).setParameter("severity", severity).executeUpdate();
    }
    private void route(UUID country, UUID company, UUID point) {
        coordinationPostId = UUID.randomUUID(); supervisionRouteId = UUID.randomUUID();
        supervisionRoutePointId = UUID.randomUUID(); supervisionShiftId = UUID.randomUUID(); supervisionAssignmentId = UUID.randomUUID();
        em.createNativeQuery("""
            insert into coordination_post(id,instance_country_id,company_id,code,name,post_type,format,rotation,shift_start_time,
                day_mask,status,updated_by_username,created_at,updated_at)
            values(:id,:tenant,:company,:code,'Test supervision','SUPERVISION','24/7','6-2','00:00:00',127,'ACTIVE','test',now(),now())
            """).setParameter("id", coordinationPostId).setParameter("tenant", country).setParameter("company", company)
            .setParameter("code", "TEST-" + coordinationPostId.toString().substring(0, 8)).executeUpdate();
        em.createNativeQuery("""
            insert into supervision_route(id,instance_country_id,series_id,coordination_post_id,code,name,version_no,status,
                published_at,updated_by_username,created_at,updated_at)
            values(:id,:tenant,:id,:post,:code,'Test route',1,'ACTIVE',now(),'test',now(),now())
            """).setParameter("id", supervisionRouteId).setParameter("tenant", country).setParameter("post", coordinationPostId)
            .setParameter("code", "TEST-" + supervisionRouteId.toString().substring(0, 8)).executeUpdate();
        em.createNativeQuery("""
            insert into supervision_route_point(id,instance_country_id,route_id,point_id,sort_order,point_code_snapshot,
                point_name_snapshot,created_at,updated_at)
            values(:id,:tenant,:route,:point,1,'TEST','Test point',now(),now())
            """).setParameter("id", supervisionRoutePointId).setParameter("tenant", country)
            .setParameter("route", supervisionRouteId).setParameter("point", point).executeUpdate();
        em.createNativeQuery("""
            insert into shift_occurrence(id,instance_country_id,coordination_post_id,supervision_route_id,starts_at,ends_at,
                required,created_at,updated_at)
            values(:id,:tenant,:post,:route,'2001-01-02 05:00:00+00','2001-01-02 09:00:00+00',true,now(),now())
            """).setParameter("id", supervisionShiftId).setParameter("tenant", country)
            .setParameter("post", coordinationPostId).setParameter("route", supervisionRouteId).executeUpdate();
        em.createNativeQuery("""
            insert into operational_assignment(id,instance_country_id,assignment_plan_id,shift_occurrence_id,employee_id,
                status,created_at,updated_at)
            values(:id,:tenant,:plan,:shift,:employee,'PUBLISHED',now(),now())
            """).setParameter("id", supervisionAssignmentId).setParameter("tenant", country)
            .setParameter("plan", planId).setParameter("shift", supervisionShiftId)
            .setParameter("employee", UUID.randomUUID()).executeUpdate();
    }
    private void completedVisit(UUID country, UUID point, UUID post, UUID agentAssignment) {
        supervisionEvidenceId = UUID.randomUUID(); supervisionEvaluationId = UUID.randomUUID();
        UUID supervisor = UUID.randomUUID(), agent = UUID.randomUUID();
        em.createNativeQuery("""
            insert into evidence_object(id,instance_country_id,client_evidence_id,upload_batch_id,event_id,assignment_id,
                employee_id,username,target_type,target_id,bucket,object_key,content_type,size_bytes,sha256,captured_at,
                source,status,received_at)
            values(:id,:tenant,:client,:batch,:event,:assignment,:employee,'test','SUPERVISOR_AGENT_PHOTO',:post,
                'test-evidence','test/photo.jpg','image/jpeg',1,:hash,'2001-01-02 06:00:00+00','CAMERA','STORED',now())
            """).setParameter("id", supervisionEvidenceId).setParameter("tenant", country)
            .setParameter("client", UUID.randomUUID()).setParameter("batch", supervisionEvaluationId)
            .setParameter("event", supervisionEvaluationId).setParameter("assignment", supervisionAssignmentId)
            .setParameter("employee", supervisor).setParameter("post", post)
            .setParameter("hash", "a".repeat(64)).executeUpdate();
        em.createNativeQuery("""
            insert into supervisor_evaluation(id,instance_country_id,supervisor_assignment_id,agent_assignment_id,
                supervisor_employee_id,agent_employee_id,point_id,post_id,captured_at,vigilance_level,cleanliness_level,
                consignment_knowledge_level,situational_level,agent_photo_evidence_id,inventory_overall_state,status,
                correlation_id,created_by,created_at,payload_hash)
            values(:id,:tenant,:supervisorAssignment,:agentAssignment,:supervisor,:agent,:point,:post,
                '2001-01-02 06:00:00+00','LOW','LOW','LOW','LOW',:evidence,'COMPLETE','COMPLETED',:correlation,
                'test',now(),:hash)
            """).setParameter("id", supervisionEvaluationId).setParameter("tenant", country)
            .setParameter("supervisorAssignment", supervisionAssignmentId).setParameter("agentAssignment", agentAssignment)
            .setParameter("supervisor", supervisor).setParameter("agent", agent).setParameter("point", point)
            .setParameter("post", post).setParameter("evidence", supervisionEvidenceId)
            .setParameter("correlation", UUID.randomUUID()).setParameter("hash", "b".repeat(64)).executeUpdate();
    }
}
