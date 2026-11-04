package com.cajamarca.sgi.comando.obsint;

import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.persistence.EntityManager;
import java.util.UUID;

/**
 * (Otro puesto que ObsintMonitorScopeTest: solo puede haber una persona asignada por turno.)
 * Pone de turno a un monitor en el puesto de Monitoreo MON-T02 de Galvarino (turno en curso, plan publicado).
 * Solo los monitoristas ejecutan casos en la consola (SEN-03), así que las pruebas de la consola lo usan.
 */
final class ObsintMonitorShift {
    static final UUID PE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID GALVARINO = UUID.fromString("20000000-0000-0000-0000-000000000001");
    /** Usuario web "monitor" (rol MONITOR), vinculado en operator_employee_binding a esta persona de Galvarino. */
    static final long MONITOR_USER_PERSONA_ID = 990020L;

    private ObsintMonitorShift() {}

    static void onShift(EntityManager em, long personaId) {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("""
                insert into coordination_post(id,instance_country_id,company_id,code,name,post_type,format,rotation,shift_start_time,day_mask,status,updated_by_username,created_at,updated_at)
                values (gen_random_uuid(),:pe,:company,'MON-T02','Monitoreo pruebas OBSINT 2','MONITORING','24/7','6-2','06:00',127,'ACTIVE','test',now(),now())
                on conflict (instance_country_id,company_id,code) do nothing""")
                .setParameter("pe", PE).setParameter("company", GALVARINO).executeUpdate();
            em.createNativeQuery("""
                insert into shift_occurrence(id,instance_country_id,coordination_post_id,starts_at,ends_at,required,created_at,updated_at)
                select gen_random_uuid(),:pe,cp.id,now()-interval '2 hours',now()+interval '10 hours',true,now(),now()
                from coordination_post cp where cp.instance_country_id=:pe and cp.company_id=:company and cp.code='MON-T02'
                and not exists (select 1 from shift_occurrence s where s.coordination_post_id=cp.id and s.starts_at<=now() and s.ends_at>now()+interval '1 hour')""")
                .setParameter("pe", PE).setParameter("company", GALVARINO).executeUpdate();
            Object shift = em.createNativeQuery("""
                select s.id from shift_occurrence s join coordination_post cp on cp.id=s.coordination_post_id
                where cp.instance_country_id=:pe and cp.company_id=:company and cp.code='MON-T02' and s.starts_at<=now() and s.ends_at>now()+interval '1 hour'
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
                .setParameter("company", GALVARINO).setParameter("persona", personaId).setParameter("s", shift).executeUpdate();
        });
    }

    /** El usuario web "monitor" de turno. */
    static void monitorUserOnShift() {
        onShift(io.quarkus.arc.Arc.container().instance(EntityManager.class).get(), MONITOR_USER_PERSONA_ID);
    }
}
