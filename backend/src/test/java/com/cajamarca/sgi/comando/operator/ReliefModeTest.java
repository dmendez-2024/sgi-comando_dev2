package com.cajamarca.sgi.comando.operator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.*;
import static org.hamcrest.Matchers.*;

/**
 * Saliente del relevo: solo el agente de otro turno del mismo Puesto que termina exactamente al empezar el turno actual.
 * Sin turno anterior inmediato, el agente inicia el puesto sin saliente (OPENING_WITHOUT_PREVIOUS_SHIFT).
 */
@QuarkusTest
class ReliefModeTest {
    @Inject EntityManager em;
    final ObjectMapper m = new ObjectMapper();
    static final Path JPG = Path.of("src/test/resources/fixtures/sample.jpg");
    static final String TENANT = "11111111-1111-1111-1111-111111111111";
    static final String COMPANY = "20000000-0000-0000-0000-000000000001";
    UUID agent, otherAgent;
    final List<UUID> created = new ArrayList<>();

    @BeforeEach void setUp() {
        ensureAgentAssignment(em);
        QuarkusTransaction.requiringNew().run(() -> {
            agent = (UUID) em.createNativeQuery("select employee_id from employee_operational_snapshot where instance_country_id=cast(:t as uuid) and persona_id=:p")
                .setParameter("t", TENANT).setParameter("p", AGENT_PERSONA_ID).getSingleResult();
            otherAgent = (UUID) em.createNativeQuery("select employee_id from employee_operational_snapshot where instance_country_id=cast(:t as uuid) and company_id=cast(:c as uuid) and employee_id<>:a order by full_name limit 1")
                .setParameter("t", TENANT).setParameter("c", COMPANY).setParameter("a", agent).getSingleResult();
        });
    }

    /** Las asignaciones de la prueba no quedan vigentes: no las toma el runtime de otras pruebas ni la evaluación de la siguiente ejecución. */
    @AfterEach void removeCreatedAssignments() {
        if (created.isEmpty()) return;
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("update operational_assignment set status='REMOVED' where id in (:ids)")
            .setParameter("ids", created).executeUpdate());
    }

    /** Turno en GGTT01 con milisegundos aleatorios: ningún turno de otra prueba termina justo cuando empieza. */
    UUID shift(Instant start, Instant end) {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
            insert into shift_occurrence(id,instance_country_id,post_id,starts_at,ends_at,required,created_at,updated_at)
            values(:id,cast(:t as uuid),:post,:s,:e,true,now(),now())""")
            .setParameter("id", id).setParameter("t", TENANT).setParameter("post", POST_GGTT01).setParameter("s", start).setParameter("e", end).executeUpdate());
        return id;
    }

    UUID assign(UUID shift, UUID employee) {
        UUID id = UUID.randomUUID();
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("""
                insert into assignment_plan(id,instance_country_id,company_id,week_start,status,created_at,updated_at)
                select gen_random_uuid(),s.instance_country_id,cast(:c as uuid),date_trunc('week',s.starts_at)::date,'PUBLISHED',now(),now()
                from shift_occurrence s where s.id=:s and not exists (select 1 from assignment_plan ap where ap.instance_country_id=s.instance_country_id
                  and ap.company_id=cast(:c as uuid) and ap.week_start=date_trunc('week',s.starts_at)::date)""")
                .setParameter("c", COMPANY).setParameter("s", shift).executeUpdate();
            em.createNativeQuery("""
                update assignment_plan ap set status='PUBLISHED' from shift_occurrence s where s.id=:s and ap.instance_country_id=s.instance_country_id
                  and ap.company_id=cast(:c as uuid) and ap.week_start=date_trunc('week',s.starts_at)::date and ap.status='DRAFT'""")
                .setParameter("c", COMPANY).setParameter("s", shift).executeUpdate();
            em.createNativeQuery("""
                insert into operational_assignment(id,instance_country_id,assignment_plan_id,shift_occurrence_id,employee_id,status,created_at,updated_at)
                select :id,s.instance_country_id,ap.id,s.id,:e,'PUBLISHED',now(),now() from shift_occurrence s
                join assignment_plan ap on ap.instance_country_id=s.instance_country_id and ap.company_id=cast(:c as uuid) and ap.week_start=date_trunc('week',s.starts_at)::date
                where s.id=:s""")
                .setParameter("id", id).setParameter("e", employee).setParameter("c", COMPANY).setParameter("s", shift).executeUpdate();
        });
        created.add(id);
        return id;
    }

    /** Turno del agente que empieza en 30 min (ventana de ingreso anticipado de la app) y dura 12 h. */
    Instant currentStart() { return Instant.now().plus(30, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS).plusMillis(1 + new Random().nextInt(998)); }

    JsonNode context(UUID assignment) {
        return as("agente").get("/api/v1/operator/runtime?assignmentId=" + assignment).then().statusCode(200).extract().as(JsonNode.class).path("relief");
    }

    ObjectNode batch(UUID assignment, JsonNode context, String mode, UUID outgoing) throws Exception {
        UUID eventId = UUID.randomUUID();
        List<String> purposes = new ArrayList<>(List.of("entrant_face", "entrant_full", "station_0", "station_1", "station_2"));
        if ("NORMAL".equals(mode)) purposes.addAll(List.of("outgoing_face", "outgoing_full"));
        ObjectNode b = m.createObjectNode().put("batchId", UUID.randomUUID().toString()).put("correlationId", UUID.randomUUID().toString())
            .put("employeeId", agent.toString()).put("instanceCountryId", TENANT).put("deviceId", "relief-mode-test").put("capturedAt", Instant.now().toString());
        ObjectNode e = b.putArray("events").addObject().put("eventId", eventId.toString()).put("type", "RELIEF_SUBMITTED")
            .put("incomingEmployeeId", agent.toString()).put("assignmentId", assignment.toString()).put("postId", context.path("postId").asText())
            .put("shiftOccurrenceId", context.path("shiftOccurrenceId").asText()).put("configurationVersion", context.path("configurationVersion").asText())
            .put("reliefMode", mode).put("unilateral", false).put("inventoryStatus", "PENDING_SOURCE").put("executedAt", Instant.now().toString());
        if (outgoing != null) e.put("outgoingEmployeeId", outgoing.toString());
        if ("OPENING_WITHOUT_PREVIOUS_SHIFT".equals(mode)) e.put("openingCause", context.path("openingCause").asText("NO_PREVIOUS_SHIFT"));
        ArrayNode readings = e.putArray("consignmentReadings");
        for (JsonNode item : context.path("consignments"))
            readings.addObject().put("consignmentId", item.path("consignmentId").asText()).put("version", item.path("version").asText()).put("confirmedAt", Instant.now().toString());
        ArrayNode evidence = e.putArray("evidence");
        for (String purpose : purposes) {
            String evidenceId = as("agente").contentType("image/jpeg").body(Files.readAllBytes(JPG))
                .put("/api/v1/operator/relief-evidence/" + eventId + "/" + purpose + "?assignmentId=" + assignment)
                .then().statusCode(200).extract().path("evidenceId");
            evidence.addObject().put("purpose", purpose).put("evidenceId", evidenceId);
        }
        return b;
    }

    io.restassured.response.ValidatableResponse submit(ObjectNode batch) {
        return as("agente").contentType(ContentType.JSON).body(batch.toString()).post("/api/v1/operator/executions").then();
    }

    Object[] storedRelief(ObjectNode batch) {
        String id = batch.path("events").get(0).path("eventId").asText();
        return QuarkusTransaction.requiringNew().call(() -> (Object[]) em.createNativeQuery("select relief_mode,opening_cause,outgoing_employee_id,unilateral from relief_event where id=cast(:id as uuid)")
            .setParameter("id", id).getSingleResult());
    }

    /** Criterio 1: 07:00–19:00 del mismo agente y vuelve al día siguiente a las 07:00 (sin turno nocturno). */
    @Test void sameAgentNextDayOpensThePostWithoutOutgoing() throws Exception {
        Instant start = currentStart();
        assign(shift(start.minus(24, ChronoUnit.HOURS), start.minus(12, ChronoUnit.HOURS)), agent);
        UUID assignment = assign(shift(start, start.plus(12, ChronoUnit.HOURS)), agent);

        JsonNode context = context(assignment);
        Assertions.assertEquals("OPENING_WITHOUT_PREVIOUS_SHIFT", context.path("reliefMode").asText());
        Assertions.assertEquals("PREVIOUS_SHIFT_NOT_CONSECUTIVE", context.path("openingCause").asText());
        Assertions.assertTrue(context.path("expectedOutgoingEmployeeId").isMissingNode(), "No se informa saliente");
        Assertions.assertTrue(context.path("expectedOutgoingEmployeeName").isMissingNode(), "No se informa saliente");

        // Un relevo normal ya no tiene saliente que validar: se pide actualizar el contexto.
        submit(batch(assignment, context, "NORMAL", agent)).statusCode(400).body(containsString("auto relevo"));
        submit(batch(assignment, context, "NORMAL", otherAgent)).statusCode(409);

        // El inicio sin saliente se completa con las fotos del entrante y del puesto, sin motivo manual.
        ObjectNode opening = batch(assignment, context, "OPENING_WITHOUT_PREVIOUS_SHIFT", null);
        submit(opening).statusCode(200).body("acknowledgedEventIds", hasSize(1));
        Object[] stored = storedRelief(opening);
        Assertions.assertEquals("OPENING_WITHOUT_PREVIOUS_SHIFT", stored[0]);
        Assertions.assertEquals("PREVIOUS_SHIFT_NOT_CONSECUTIVE", stored[1]);
        Assertions.assertNull(stored[2]);
        Assertions.assertEquals(false, stored[3]);
        // Reintento offline del mismo lote: idempotente.
        submit(opening).statusCode(200).body("acknowledgedEventIds", hasSize(1));
        JsonNode after = context(assignment);
        Assertions.assertTrue(after.path("reliefAlreadyRegistered").asBoolean());
        Assertions.assertEquals("OPENING_WITHOUT_PREVIOUS_SHIFT", after.path("reliefRegisteredMode").asText());
    }

    @Test void postWithoutAnyPreviousShiftOpensWithItsOwnCause() {
        // Un turno en un instante que ningún otro termina, con el historial más antiguo que él (nunca anterior).
        Instant start = currentStart();
        UUID assignment = assign(shift(start, start.plus(12, ChronoUnit.HOURS)), agent);
        JsonNode context = context(assignment);
        Assertions.assertEquals("OPENING_WITHOUT_PREVIOUS_SHIFT", context.path("reliefMode").asText());
        Assertions.assertTrue(Set.of("NO_PREVIOUS_SHIFT", "PREVIOUS_SHIFT_NOT_CONSECUTIVE").contains(context.path("openingCause").asText()));
        Assertions.assertTrue(context.path("expectedOutgoingEmployeeId").isMissingNode());
    }

    /** Criterio 2: 19:00–07:00 de otro agente y turno actual 07:00–19:00. */
    @Test void consecutiveShiftOfAnotherAgentIsANormalRelief() throws Exception {
        Instant start = currentStart();
        assign(shift(start.minus(12, ChronoUnit.HOURS), start), otherAgent);
        UUID assignment = assign(shift(start, start.plus(12, ChronoUnit.HOURS)), agent);

        JsonNode context = context(assignment);
        Assertions.assertEquals("NORMAL", context.path("reliefMode").asText());
        Assertions.assertEquals(otherAgent.toString(), context.path("expectedOutgoingEmployeeId").asText());
        Assertions.assertFalse(context.path("expectedOutgoingEmployeeName").asText().isBlank());
        Assertions.assertTrue(context.path("openingCause").isMissingNode());

        // Con saliente real no se admite iniciar el puesto sin él, ni un saliente distinto.
        submit(batch(assignment, context, "OPENING_WITHOUT_PREVIOUS_SHIFT", null)).statusCode(409);
        submit(batch(assignment, context, "NORMAL", UUID.randomUUID())).statusCode(400);

        ObjectNode normal = batch(assignment, context, "NORMAL", otherAgent);
        submit(normal).statusCode(200).body("acknowledgedEventIds", hasSize(1));
        Object[] stored = storedRelief(normal);
        Assertions.assertEquals("NORMAL", stored[0]);
        Assertions.assertNull(stored[1]);
        Assertions.assertEquals(otherAgent, stored[2]);
    }

    /** Las apps anteriores sin reliefMode siguen enviando el relevo unilateral con motivo. */
    @Test void legacyUnilateralWithoutReliefModeIsStillAccepted() throws Exception {
        Instant start = currentStart();
        assign(shift(start.minus(12, ChronoUnit.HOURS), start), otherAgent);
        UUID assignment = assign(shift(start, start.plus(12, ChronoUnit.HOURS)), agent);
        ObjectNode legacy = batch(assignment, context(assignment), "UNILATERAL", null);
        ObjectNode event = (ObjectNode) legacy.path("events").get(0);
        event.remove("reliefMode");
        event.put("unilateral", true).put("unilateralReason", "Saliente no se presentó");
        submit(legacy).statusCode(200);
        Object[] stored = storedRelief(legacy);
        Assertions.assertEquals("UNILATERAL", stored[0]);
        Assertions.assertEquals(true, stored[3]);
    }

    /** Criterio 3: dos turnos consecutivos del mismo agente siguen bloqueados al planificar (AUTO_RELEVO); con intervalo no. */
    @Test void consecutiveShiftsOfTheSameAgentStayBlockedInPlanning() {
        // Miércoles 12:00 UTC de esta semana: los tres turnos caen en el mismo plan semanal.
        Instant base = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).with(java.time.DayOfWeek.MONDAY).truncatedTo(ChronoUnit.DAYS)
            .plusDays(2).plusHours(12).toInstant().plusMillis(1 + new Random().nextInt(998));
        assign(shift(base.minus(12, ChronoUnit.HOURS), base), agent);
        UUID consecutive = shift(base, base.plus(12, ChronoUnit.HOURS));
        UUID withGap = shift(base.plus(24, ChronoUnit.HOURS), base.plus(36, ChronoUnit.HOURS));
        UUID plan = QuarkusTransaction.requiringNew().call(() -> (UUID) em.createNativeQuery(
            "select id from assignment_plan where instance_country_id=cast(:t as uuid) and company_id=cast(:c as uuid) and week_start=date_trunc('week',cast(:b as timestamptz))::date")
            .setParameter("t", TENANT).setParameter("c", COMPANY).setParameter("b", base).getSingleResult());

        JsonNode evaluation = asBasic("asistente").get("/api/assignments/evaluate?planId=" + plan + "&employeeId=" + agent)
            .then().statusCode(200).extract().as(JsonNode.class);
        Assertions.assertTrue(blockers(evaluation, consecutive).contains("AUTO_RELEVO"), "Turno consecutivo bloqueado por AUTO_RELEVO");
        Assertions.assertFalse(blockers(evaluation, withGap).contains("AUTO_RELEVO"), "Con intervalo no es auto-relevo");
    }

    static Set<String> blockers(JsonNode evaluation, UUID shift) {
        Set<String> codes = new HashSet<>();
        for (JsonNode item : evaluation) if (shift.toString().equals(item.path("shiftOccurrenceId").asText()))
            item.path("blockers").forEach(b -> codes.add(b.path("code").asText()));
        return codes;
    }
}
