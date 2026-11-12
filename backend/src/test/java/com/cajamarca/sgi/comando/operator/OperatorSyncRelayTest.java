package com.cajamarca.sgi.comando.operator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Un solo dispositivo por punto: el agente en sesión envía los registros que el agente del turno anterior dejó sin
 * sincronizar. Comando los deja a nombre de quien los hizo y guarda quién los sincronizó.
 */
@QuarkusTest
class OperatorSyncRelayTest {
    static final long PREVIOUS_AGENT_PERSONA_ID = 990003L;
    static final String TEST_PE = "11111111-1111-1111-1111-111111111111";
    static final String COMPANY = "20000000-0000-0000-0000-000000000001";

    @Inject EntityManager em;
    final ObjectMapper m = new ObjectMapper();

    @BeforeEach void setUp() { ensureAgentAssignment(em); }

    @Test void relayedReportStaysWithItsAuthorAndRecordsWhoSynced() {
        UUID previous = previousAgentAssignment(Duration.ofHours(3));
        UUID reportId = UUID.randomUUID();
        String body = report(reportId, previous, Instant.now().minus(Duration.ofHours(5)));

        relay("ident-agente-anterior", reportId).body(body).post("/api/v1/operator/reports").then().statusCode(200);
        // Un reintento del mismo registro es idempotente.
        relay("ident-agente-anterior", reportId).body(body).post("/api/v1/operator/reports").then().statusCode(200);

        Object[] row = (Object[]) em.createNativeQuery("select employee_id,username from operator_operational_report where id=:id")
            .setParameter("id", reportId).getSingleResult();
        assertEquals(employeeOf(PREVIOUS_AGENT_PERSONA_ID), UUID.fromString(row[0].toString()));
        assertEquals("ident-agente-anterior", row[1].toString());

        Object[] audit = (Object[]) em.createNativeQuery("""
            select author_employee_id,author_username,relayed_by_employee_id,relayed_by_username from operator_sync_relay
            where operation='OPERATIONAL_REPORT' and reference_id=:ref""").setParameter("ref", reportId.toString()).getSingleResult();
        assertEquals(employeeOf(PREVIOUS_AGENT_PERSONA_ID), UUID.fromString(audit[0].toString()));
        assertEquals("ident-agente-anterior", audit[1].toString());
        assertEquals(employeeOf(AGENT_PERSONA_ID), UUID.fromString(audit[2].toString()));
        assertEquals("ident-agente", audit[3].toString());
    }

    @Test void otherAgentsAssignmentNeedsTheRelayHeader() {
        UUID previous = previousAgentAssignment(Duration.ofHours(3));
        UUID reportId = UUID.randomUUID();
        as("agente").header("Idempotency-Key", reportId.toString()).contentType(ContentType.JSON)
            .body(report(reportId, previous, Instant.now().minus(Duration.ofHours(5))))
            .post("/api/v1/operator/reports").then().statusCode(403);
    }

    @Test void shiftsOlderThanSevenDaysAreNotRelayed() {
        UUID old = previousAgentAssignment(Duration.ofDays(8));
        UUID reportId = UUID.randomUUID();
        relay("ident-agente-anterior", reportId).body(report(reportId, old, Instant.now().minus(Duration.ofDays(8)).minus(Duration.ofHours(2))))
            .post("/api/v1/operator/reports").then().statusCode(403);
        assertTrue(em.createNativeQuery("select 1 from operator_operational_report where id=:id").setParameter("id", reportId).getResultList().isEmpty());
    }

    RequestSpecification relay(String author, UUID idempotencyKey) {
        return as("agente").header(OperatorContext.RELAY_HEADER, "true").header(OperatorContext.RELAY_AUTHOR_HEADER, author)
            .header("Idempotency-Key", idempotencyKey.toString()).contentType(ContentType.JSON);
    }

    String report(UUID reportId, UUID assignment, Instant occurredAt) {
        ObjectNode body = m.createObjectNode().put("reportId", reportId.toString()).put("assignmentId", assignment.toString())
            .put("type", "FINDING").put("category", "Limpieza").put("title", "Hallazgo del turno anterior").put("description", "Registrado sin conexión")
            .put("severity", "MEDIUM").put("occurredAt", occurredAt.toString()).put("photoCount", 0);
        return body.toString();
    }

    UUID employeeOf(long personaId) {
        return UUID.fromString(em.createNativeQuery("select employee_id from employee_operational_snapshot where instance_country_id=cast(:t as uuid) and persona_id=:p")
            .setParameter("t", TEST_PE).setParameter("p", personaId).getSingleResult().toString());
    }

    /** Asignación publicada en GGTT01 de otro agente, en un turno de 12 horas que terminó hace [endedAgo]. */
    UUID previousAgentAssignment(Duration endedAgo) {
        return QuarkusTransaction.requiringNew().call(() -> {
            em.createNativeQuery("""
                update employee_operational_snapshot set persona_id=:persona
                where employee_id=(select e.employee_id from employee_operational_snapshot e
                  where e.company_id=cast(:company as uuid)
                    and (e.persona_id is null or e.persona_id not in (:agent,:supervisor)) order by e.full_name desc limit 1)
                and not exists (select 1 from employee_operational_snapshot x where x.instance_country_id=cast(:t as uuid) and x.persona_id=:persona)""")
                .setParameter("persona", PREVIOUS_AGENT_PERSONA_ID).setParameter("company", COMPANY).setParameter("t", TEST_PE)
                .setParameter("agent", AGENT_PERSONA_ID).setParameter("supervisor", SUPERVISOR_PERSONA_ID).executeUpdate();
            Instant endsAt = Instant.now().minus(endedAgo), startsAt = endsAt.minus(Duration.ofHours(12));
            UUID shift = UUID.randomUUID();
            em.createNativeQuery("""
                insert into shift_occurrence(id,instance_country_id,post_id,starts_at,ends_at,required,created_at,updated_at)
                values(:id,cast(:t as uuid),:post,:starts,:ends,true,now(),now())""")
                .setParameter("id", shift).setParameter("t", TEST_PE).setParameter("post", POST_GGTT01)
                .setParameter("starts", startsAt).setParameter("ends", endsAt).executeUpdate();
            em.createNativeQuery("""
                insert into assignment_plan(id,instance_country_id,company_id,week_start,status,created_at,updated_at)
                select gen_random_uuid(),s.instance_country_id,cast(:company as uuid),date_trunc('week',s.starts_at)::date,'PUBLISHED',now(),now()
                from shift_occurrence s where s.id=:s
                and not exists (select 1 from assignment_plan ap where ap.instance_country_id=s.instance_country_id
                  and ap.company_id=cast(:company as uuid) and ap.week_start=date_trunc('week',s.starts_at)::date)""")
                .setParameter("s", shift).setParameter("company", COMPANY).executeUpdate();
            em.createNativeQuery("""
                update assignment_plan ap set status='PUBLISHED' from shift_occurrence s
                where s.id=:s and ap.instance_country_id=s.instance_country_id and ap.company_id=cast(:company as uuid)
                  and ap.week_start=date_trunc('week',s.starts_at)::date and ap.status='DRAFT'""")
                .setParameter("s", shift).setParameter("company", COMPANY).executeUpdate();
            UUID id = UUID.randomUUID();
            int inserted = em.createNativeQuery("""
                insert into operational_assignment(id,instance_country_id,assignment_plan_id,shift_occurrence_id,employee_id,status,created_at,updated_at)
                select :id, s.instance_country_id, ap.id, s.id, b.employee_id, 'PUBLISHED', now(), now()
                from shift_occurrence s
                join assignment_plan ap on ap.instance_country_id=s.instance_country_id and ap.company_id=cast(:company as uuid)
                  and ap.week_start=date_trunc('week',s.starts_at)::date
                join employee_operational_snapshot b on b.persona_id=:persona and b.instance_country_id=s.instance_country_id
                where s.id=:s""")
                .setParameter("id", id).setParameter("s", shift).setParameter("company", COMPANY)
                .setParameter("persona", PREVIOUS_AGENT_PERSONA_ID).executeUpdate();
            if (inserted != 1) throw new IllegalStateException("No se pudo crear la asignación del agente anterior");
            return id;
        });
    }
}
