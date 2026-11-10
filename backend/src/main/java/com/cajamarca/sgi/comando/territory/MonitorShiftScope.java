package com.cajamarca.sgi.comando.territory;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.security.IdentIdentity;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Alcance del Monitor (rol MONITOR): no tiene zona, región ni Compañía fija; ve las Compañías de los puestos de Monitoreo
 * (Coordinación) donde tiene turno en curso según Asignaciones, con el plan de la semana publicado. Mismo criterio que SGI Operador.
 */
@ApplicationScoped
public class MonitorShiftScope {
    public static final String MONITOR_ROLE = "MONITOR";
    /** Cargos de monitoreo (role_code del empleado), los mismos a los que IDENT asigna el rol MONITOR. */
    public static final Set<String> MONITOR_CARGOS = Set.of("Monitor", "Agente de Consola de Monitoreo Senior");

    @Inject TenantContext tenant;
    @Inject SecurityIdentity identity;
    @Inject EntityManager em;

    /** Compañías del turno en curso del usuario; vacío si no es Monitor o no está de turno. */
    public Set<UUID> companiesNow() {
        if (identity.isAnonymous() || !identity.hasRole(MONITOR_ROLE)) return Set.of();
        return companies(tenant.instanceCountryId(), persona(), Instant.now());
    }

    /**
     * persona_id del monitor: el del token de IDENT; si entró con usuario UAT (sin IDENT), el del empleado vinculado a ese usuario
     * en operator_employee_binding, igual que hace SGI Operador con sus usuarios de prueba.
     */
    Long persona() {
        Long fromToken = IdentIdentity.personaId(identity);
        if (fromToken != null) return fromToken;
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery("""
                select e.persona_id from operator_employee_binding b
                join employee_operational_snapshot e on e.instance_country_id=b.instance_country_id and e.employee_id=b.employee_id
                where b.instance_country_id=:tenant and b.username=:username and b.active and e.persona_id is not null""")
            .setParameter("tenant", tenant.instanceCountryId()).setParameter("username", identity.getPrincipal().getName())
            .setMaxResults(1).getResultList();
        return rows.isEmpty() ? null : ((Number) rows.get(0)).longValue();
    }

    /** Compañías donde esa persona está de turno en ese momento en un puesto de Monitoreo (cuenta también a quien lo cubrió). */
    public Set<UUID> companies(UUID tenantId, Long personaId, Instant now) {
        if (personaId == null) return Set.of();
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery("select distinct cp.company_id " + ON_SHIFT + " and e.persona_id=:persona")
            .setParameter("tenant", tenantId).setParameter("persona", personaId).setParameter("now", now).getResultList();
        return uuids(rows);
    }

    /** employee_id del Monitor que está usando la consola; null si no es Monitor o no se le conoce la persona. */
    public UUID employeeNow() {
        if (identity.isAnonymous() || !identity.hasRole(MONITOR_ROLE)) return null;
        Long persona = persona();
        if (persona == null) return null;
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery("select employee_id from employee_operational_snapshot where instance_country_id=:tenant and persona_id=:persona")
            .setParameter("tenant", tenant.instanceCountryId()).setParameter("persona", persona).setMaxResults(1).getResultList();
        return rows.isEmpty() ? null : uuids(rows).iterator().next();
    }

    /**
     * Cargo (role_code del empleado) de quien usa la consola, para firmar los mensajes al agente; null si no se le conoce
     * la persona o el empleado. Sirve también para los cargos altos, que envían mensajes sin rol MONITOR.
     */
    public String cargoNow() {
        if (identity.isAnonymous()) return null;
        Long persona = persona();
        if (persona == null) return null;
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery("select role_code from employee_operational_snapshot where instance_country_id=:tenant and persona_id=:persona and role_code is not null")
            .setParameter("tenant", tenant.instanceCountryId()).setParameter("persona", persona).setMaxResults(1).getResultList();
        return rows.isEmpty() ? null : rows.get(0).toString().trim();
    }

    /** De esos empleados, los que están de turno en ese momento en algún puesto de Monitoreo. */
    public Set<UUID> employeesOnShift(UUID tenantId, Collection<UUID> employeeIds, Instant now) {
        if (employeeIds.isEmpty()) return Set.of();
        @SuppressWarnings("unchecked")
        List<Object> rows = em.createNativeQuery("select distinct e.employee_id " + ON_SHIFT + " and e.employee_id in (:employees)")
            .setParameter("tenant", tenantId).setParameter("employees", employeeIds).setParameter("now", now).getResultList();
        return uuids(rows);
    }

    /** Asignaciones vigentes en un puesto de Monitoreo en :now, con el plan publicado o cerrado; e es quien hace el turno. */
    private static final String ON_SHIFT = """
        from operational_assignment a
        join shift_occurrence s on s.id=a.shift_occurrence_id and s.instance_country_id=a.instance_country_id
        join assignment_plan p on p.id=a.assignment_plan_id and p.instance_country_id=a.instance_country_id
        join coordination_post cp on cp.id=s.coordination_post_id and cp.instance_country_id=a.instance_country_id
        join employee_operational_snapshot e on e.employee_id=coalesce(a.actual_employee_id,a.employee_id) and e.instance_country_id=a.instance_country_id
        where a.instance_country_id=:tenant and a.status<>'REMOVED' and p.status in ('PUBLISHED','CLOSED')
          and cp.post_type='MONITORING' and cp.company_id is not null and s.starts_at<=:now and s.ends_at>:now""";

    private static Set<UUID> uuids(List<Object> rows) {
        return rows.stream().map(r -> r instanceof UUID u ? u : UUID.fromString(r.toString())).collect(Collectors.toSet());
    }
}
