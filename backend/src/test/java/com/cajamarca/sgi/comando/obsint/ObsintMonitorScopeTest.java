package com.cajamarca.sgi.comando.obsint;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Alcance del rol MONITOR en la consola: las Compañías de los puestos de Monitoreo (Coordinación) donde la persona
 * tiene turno en curso, con el plan de la semana publicado.
 */
@QuarkusTest
class ObsintMonitorScopeTest {
    static final UUID PE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID GALVARINO = UUID.fromString("20000000-0000-0000-0000-000000000001");
    static final long MONITOR_PERSONA_ID = 990010L;
    static final UUID MONITOR_EMPLOYEE_ID = UUID.fromString("e0b51000-0000-0000-0000-000000000010");

    @Inject ObsintPlatform platform;
    @Inject EntityManager em;

    /** Monitor de pruebas de Galvarino (persona 990010) asignado al turno en curso del puesto de Monitoreo MON-T01, con el plan publicado. */
    @BeforeEach
    void monitorOnShift() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("""
                insert into employee_operational_snapshot(id,instance_country_id,employee_id,company_id,full_name,role_code,employment_status,persona_id,updated_from_source_at,created_at,updated_at)
                values (gen_random_uuid(),:pe,:employee,:company,'Monitor Pruebas OBSINT','Monitor','ACTIVE',:persona,now(),now(),now())
                on conflict (instance_country_id,employee_id) do nothing""")
                .setParameter("pe", PE).setParameter("employee", MONITOR_EMPLOYEE_ID).setParameter("company", GALVARINO)
                .setParameter("persona", MONITOR_PERSONA_ID).executeUpdate();
            em.createNativeQuery("""
                insert into coordination_post(id,instance_country_id,company_id,code,name,post_type,format,rotation,shift_start_time,day_mask,status,updated_by_username,created_at,updated_at)
                values (gen_random_uuid(),:pe,:company,'MON-T01','Monitoreo pruebas OBSINT','MONITORING','24/7','6-2','06:00',127,'ACTIVE','test',now(),now())
                on conflict (instance_country_id,company_id,code) do nothing""")
                .setParameter("pe", PE).setParameter("company", GALVARINO).executeUpdate();
            em.createNativeQuery("""
                insert into shift_occurrence(id,instance_country_id,coordination_post_id,starts_at,ends_at,required,created_at,updated_at)
                select gen_random_uuid(),:pe,cp.id,now()-interval '2 hours',now()+interval '10 hours',true,now(),now()
                from coordination_post cp where cp.instance_country_id=:pe and cp.company_id=:company and cp.code='MON-T01'
                and not exists (select 1 from shift_occurrence s where s.coordination_post_id=cp.id and s.starts_at<=now() and s.ends_at>now()+interval '1 hour')""")
                .setParameter("pe", PE).setParameter("company", GALVARINO).executeUpdate();
            Object shift = em.createNativeQuery("""
                select s.id from shift_occurrence s join coordination_post cp on cp.id=s.coordination_post_id
                where cp.instance_country_id=:pe and cp.company_id=:company and cp.code='MON-T01' and s.starts_at<=now() and s.ends_at>now()+interval '1 hour'
                order by s.starts_at limit 1""").setParameter("pe", PE).setParameter("company", GALVARINO).getSingleResult();
            em.createNativeQuery("""
                insert into assignment_plan(id,instance_country_id,company_id,week_start,status,created_at,updated_at)
                select gen_random_uuid(),:pe,:company,date_trunc('week',s.starts_at)::date,'PUBLISHED',now(),now() from shift_occurrence s where s.id=:s
                on conflict (instance_country_id,company_id,week_start) do update set status='PUBLISHED'""")
                .setParameter("pe", PE).setParameter("company", GALVARINO).setParameter("s", shift).executeUpdate();
            em.createNativeQuery("""
                insert into operational_assignment(id,instance_country_id,assignment_plan_id,shift_occurrence_id,employee_id,status,created_at,updated_at)
                select gen_random_uuid(),s.instance_country_id,ap.id,s.id,e.employee_id,'PUBLISHED',now(),now()
                from shift_occurrence s
                join assignment_plan ap on ap.instance_country_id=s.instance_country_id and ap.company_id=:company and ap.week_start=date_trunc('week',s.starts_at)::date
                join employee_operational_snapshot e on e.instance_country_id=s.instance_country_id and e.persona_id=:persona
                where s.id=:s and not exists (select 1 from operational_assignment a where a.shift_occurrence_id=s.id and a.employee_id=e.employee_id and a.status<>'REMOVED')""")
                .setParameter("company", GALVARINO).setParameter("persona", MONITOR_PERSONA_ID).setParameter("s", shift).executeUpdate();
        });
    }

    Set<UUID> companies(Long personaId, Instant at) {
        return QuarkusTransaction.requiringNew().call(() -> platform.monitorCompanies(PE, personaId, at));
    }

    @Test void monitorOnShiftSeesTheCompanyOfHisMonitoringPost() {
        assertTrue(companies(MONITOR_PERSONA_ID, Instant.now()).contains(GALVARINO));
    }

    @Test void outsideTheShiftOrWithoutAssignmentSeesNothing() {
        assertFalse(companies(MONITOR_PERSONA_ID, Instant.now().plus(Duration.ofDays(30))).contains(GALVARINO), "fuera del turno");
        assertTrue(companies(990099L, Instant.now()).isEmpty(), "persona sin asignación");
        assertTrue(companies(null, Instant.now()).isEmpty(), "sin persona_id de IDENT");
    }

    @Test void draftPlanDoesNotCount() {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
            update assignment_plan set status='DRAFT' where instance_country_id=:pe and company_id=:company and week_start=date_trunc('week',now())::date""")
            .setParameter("pe", PE).setParameter("company", GALVARINO).executeUpdate());
        try {
            assertFalse(companies(MONITOR_PERSONA_ID, Instant.now()).contains(GALVARINO), "la asignación en borrador aún no vale");
        } finally {
            QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                update assignment_plan set status='PUBLISHED' where instance_country_id=:pe and company_id=:company and week_start=date_trunc('week',now())::date""")
                .setParameter("pe", PE).setParameter("company", GALVARINO).executeUpdate());
        }
    }
}
