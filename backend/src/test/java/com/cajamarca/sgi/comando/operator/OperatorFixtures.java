package com.cajamarca.sgi.comando.operator;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.util.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/** Datos de prueba creados a través de la API, como lo haría un usuario UAT. */
public final class OperatorFixtures {
    private OperatorFixtures() {}
    public static final String PASSWORD = "CajamarcaUAT!2026";
    public static final UUID POST_GGTT01 = UUID.fromString("50000000-0000-0000-0000-000000000001");

    /** persona_id de DHO con el que IDENT identifica al agente de pruebas. */
    public static final long AGENT_PERSONA_ID = 990001L;
    public static final long SUPERVISOR_PERSONA_ID = 990002L;
    private static final String TEST_PE = "11111111-1111-1111-1111-111111111111";

    /** Usuarios de la web con usuario y contrasena UAT; "agente" y "supervisor" entran como SGI Operador, con IDENT. */
    public static RequestSpecification as(String user) {
        return switch (user) {
            case "agente" -> given().header("Authorization", "Bearer " + operatorToken("AGENTE", AGENT_PERSONA_ID));
            case "supervisor" -> given().header("Authorization", "Bearer " + operatorToken("SUPERVISOR", SUPERVISOR_PERSONA_ID));
            default -> given().auth().preemptive().basic(user, PASSWORD);
        };
    }

    /** Usuario y contrasena de SGI, aunque sea un Rol de SGI Operador (para probar que ya no se aceptan). */
    public static RequestSpecification asBasic(String user) { return given().auth().preemptive().basic(user, PASSWORD); }

    /** Access token de IDENT para SGI Operador (aud=SGI_OPR), en el formato de pruebas de TestIdentTokenVerifier. */
    public static String operatorToken(String role, long personaId) {
        Map<String,Object> claims = new LinkedHashMap<>();
        claims.put("sub", "ident-" + role.toLowerCase(Locale.ROOT));
        claims.put("aud", "SGI_OPR");
        claims.put("token_use", "access");
        claims.put("exp", java.time.Instant.now().plusSeconds(600).getEpochSecond());
        claims.put("groups", List.of(role));
        claims.put("persona_id", String.valueOf(personaId));
        claims.put("active_instance_country_id", TEST_PE);
        claims.put("email", role.toLowerCase(Locale.ROOT) + "@cajamarca.test");
        return com.cajamarca.sgi.comando.security.TestIdentTokenVerifier.token(claims);
    }

    public static Map<String,Object> createDraftPatrolWithCheckpoint(double lat, double lon) {
        String protocolId = as("coord").contentType(ContentType.JSON).body(Map.of("postId", POST_GGTT01, "name", "Prueba evidencias " + UUID.randomUUID()))
            .post("/api/patrols/protocols").then().statusCode(200).extract().path("id");
        String patrolId = as("coord").contentType(ContentType.JSON).body(Map.of("name", "Ronda prueba", "structureType", "CLOSED", "scheduleType", "UNPROGRAMMED"))
            .post("/api/patrols/protocols/" + protocolId + "/patrols").then().statusCode(200).extract().path("id");
        Map<String,Object> cp = new HashMap<>(Map.of("name", "Portón prueba", "description", "", "originMode", "FIELD", "latitude", lat, "longitude", lon,
            "gpsAccuracyM", 5.0, "controlType", "FOTOGRAFIA", "requiresEvidence", true));
        String checkpointId = as("coord").contentType(ContentType.JSON).body(cp)
            .post("/api/patrols/patrols/" + patrolId + "/checkpoints").then().statusCode(200).extract().path("id");
        return Map.of("protocolId", protocolId, "patrolId", patrolId, "checkpointId", checkpointId);
    }

    /** Protocolo publicado y ACTIVO en GGTT01, con un Hito que exige la foto del agente, tiene una foto estándar y VISINT desactivado. */
    public static Map<String,Object> publishPatrolWithCheckpoint(double lat, double lon) {
        Map<String,Object> ids = createDraftPatrolWithCheckpoint(lat, lon);
        Map<String,Object> save = new HashMap<>(Map.of("name", "Portón prueba", "description", "", "originMode", "FIELD", "latitude", lat, "longitude", lon,
            "gpsAccuracyM", 5.0, "controlType", "FOTOGRAFIA", "requiresEvidence", true, "standardImageNotes", "", "visintEnabled", false));
        as("coord").contentType(ContentType.JSON).body(save).put("/api/patrols/checkpoints/" + ids.get("checkpointId")).then().statusCode(200);
        uploadStandard(ids.get("checkpointId"), Path.of("src/test/resources/fixtures/sample.jpg"));
        as("coord").post("/api/patrols/protocols/" + ids.get("protocolId") + "/publish").then().statusCode(200).body("status", is("ACTIVO"));
        return ids;
    }

    /** Agrega una foto estándar al Hito (hasta 5) y devuelve el Hito actualizado. */
    public static io.restassured.response.ValidatableResponse uploadStandard(Object checkpointId, Path file) {
        return as("coord").multiPart("file", file.toFile(), "image/jpeg").post("/api/patrols/checkpoints/" + checkpointId + "/standard-images").then();
    }

    /** Vincula el agente de IDENT (persona_id AGENT_PERSONA_ID) a un empleado de Galvarino y le asigna el turno de GGTT01 más cercano a ahora. */
    public static UUID ensureAgentAssignment(EntityManager em) {
        return QuarkusTransaction.requiringNew().call(() -> {
            em.createNativeQuery("""
                update employee_operational_snapshot set persona_id=:persona
                where employee_id=(select e.employee_id from employee_operational_snapshot e
                  where e.company_id='20000000-0000-0000-0000-000000000001' order by e.full_name limit 1)
                and not exists (select 1 from employee_operational_snapshot x
                  where x.instance_country_id='11111111-1111-1111-1111-111111111111' and x.persona_id=:persona)""")
                .setParameter("persona", AGENT_PERSONA_ID).executeUpdate();
            // La base de pruebas no trae turnos ni planes: se crea un turno de GGTT01 que cubre "ahora" y el plan de su semana.
            em.createNativeQuery("""
                insert into shift_occurrence(id,instance_country_id,post_id,starts_at,ends_at,required,created_at,updated_at)
                select gen_random_uuid(),'11111111-1111-1111-1111-111111111111',:post,now()-interval '2 hours',now()+interval '10 hours',true,now(),now()
                where not exists (select 1 from shift_occurrence s where s.post_id=:post and s.starts_at<=now() and s.ends_at>=now())""")
                .setParameter("post", POST_GGTT01).executeUpdate();
            Object shift = em.createNativeQuery("""
                select s.id from shift_occurrence s where s.post_id=:post and s.starts_at<=now() and s.ends_at>=now() order by s.starts_at limit 1""")
                .setParameter("post", POST_GGTT01).getSingleResult();
            em.createNativeQuery("""
                insert into assignment_plan(id,instance_country_id,company_id,week_start,status,created_at,updated_at)
                select gen_random_uuid(),s.instance_country_id,'20000000-0000-0000-0000-000000000001',date_trunc('week',s.starts_at)::date,'PUBLISHED',now(),now()
                from shift_occurrence s where s.id=:s
                and not exists (select 1 from assignment_plan ap where ap.instance_country_id=s.instance_country_id
                  and ap.company_id='20000000-0000-0000-0000-000000000001' and ap.week_start=date_trunc('week',s.starts_at)::date)""")
                .setParameter("s", shift).executeUpdate();
            // Otra prueba pudo crear antes el plan de esa semana en borrador: el agente solo opera con el plan publicado.
            em.createNativeQuery("""
                update assignment_plan ap set status='PUBLISHED' from shift_occurrence s
                where s.id=:s and ap.instance_country_id=s.instance_country_id and ap.company_id='20000000-0000-0000-0000-000000000001'
                  and ap.week_start=date_trunc('week',s.starts_at)::date and ap.status='DRAFT'""")
                .setParameter("s", shift).executeUpdate();
            List<?> existing = em.createNativeQuery("""
                select a.id from operational_assignment a join employee_operational_snapshot b on b.employee_id=a.employee_id and b.persona_id=:persona
                where a.shift_occurrence_id=:s and a.status<>'REMOVED'""").setParameter("s", shift).setParameter("persona", AGENT_PERSONA_ID).getResultList();
            if (!existing.isEmpty()) return (UUID) existing.get(0);
            em.createNativeQuery("update operational_assignment set status='REMOVED' where shift_occurrence_id=:s and status<>'REMOVED'")
                .setParameter("s", shift).executeUpdate();
            UUID id = UUID.randomUUID();
            int inserted = em.createNativeQuery("""
                insert into operational_assignment(id,instance_country_id,assignment_plan_id,shift_occurrence_id,employee_id,status,created_at,updated_at)
                select :id, s.instance_country_id, ap.id, s.id, b.employee_id, 'PUBLISHED', now(), now()
                from shift_occurrence s
                join assignment_plan ap on ap.instance_country_id=s.instance_country_id and ap.company_id='20000000-0000-0000-0000-000000000001'
                join employee_operational_snapshot b on b.persona_id=:persona and b.instance_country_id=s.instance_country_id
                where s.id=:s order by abs(ap.week_start - date_trunc('week', s.starts_at)::date) limit 1""")
                .setParameter("id", id).setParameter("s", shift).setParameter("persona", AGENT_PERSONA_ID).executeUpdate();
            if (inserted != 1) throw new IllegalStateException("No se pudo crear la asignación de prueba del agente");
            return id;
        });
    }
}
