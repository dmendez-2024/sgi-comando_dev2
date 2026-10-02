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
    final List<UUID> shifts = new ArrayList<>();
    final List<UUID> reliefs = new ArrayList<>();

    @AfterEach void cleanup() {
        QuarkusTransaction.requiringNew().run(() -> {
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
        UUID company = (UUID)point[1], client = (UUID)point[2], post = (UUID)point[3];
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
    }

    @Test void validatesDatesAndCompanyScope() {
        as("presidente").queryParam("weekStart", "invalid").get("/api/dashboard/metrics").then().statusCode(400);
        as("coord").queryParam("companyId", UUID.randomUUID()).get("/api/dashboard/metrics").then().statusCode(403);
        as("agente").get("/api/dashboard/metrics").then().statusCode(403);
        as("coord").queryParam("companyId", UUID.randomUUID()).get("/api/assignments/coverage").then().statusCode(403);
    }

    private void score(UUID country, UUID post, int hours, int id, int ic, String status) {
        UUID shift = UUID.randomUUID(); shifts.add(shift);
        em.createNativeQuery("""
            insert into shift_occurrence(id,instance_country_id,post_id,starts_at,ends_at,required,created_at,updated_at)
            values(:id,:tenant,:post,'2001-01-02 05:00:00+00',timestamptz '2001-01-02 05:00:00+00'+:hours*interval '1 hour',true,now(),now())
            """).setParameter("id", shift).setParameter("tenant", country).setParameter("post", post).setParameter("hours", hours).executeUpdate();
        em.createNativeQuery("""
            insert into operational_assignment(id,instance_country_id,assignment_plan_id,shift_occurrence_id,id_score,compatibility_index,status,created_at,updated_at)
            values(:id,:tenant,:plan,:shift,:score,:ic,:status,now(),now())
            """).setParameter("id", UUID.randomUUID()).setParameter("tenant", country).setParameter("plan", planId)
            .setParameter("shift", shift).setParameter("score", id).setParameter("ic", ic).setParameter("status", status).executeUpdate();
    }
    private void relief(UUID country, UUID post, String executedAt) {
        UUID id = UUID.randomUUID(); reliefs.add(id);
        em.createNativeQuery("""
            insert into relief_event(id,instance_country_id,post_id,status,planned_at,executed_at,created_at,updated_at)
            values(:id,:tenant,:post,'PENDIENTE','2001-01-02 05:00:00+00',cast(:executed as timestamptz),now(),now())
            """).setParameter("id", id).setParameter("tenant", country).setParameter("post", post).setParameter("executed", executedAt).executeUpdate();
    }
}
