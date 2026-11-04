package com.cajamarca.sgi.comando.obsint;

import com.cajamarca.sgi.comando.companies.Company;
import com.cajamarca.sgi.comando.territory.MonitorShiftScope;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.*;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Monitoristas OBSINT del Punto (pestaña OBSINT de Configuración del Punto): uno o más, de la Compañía del Punto.
 * La Compañía es la que se asigna en Servicios (la orden llega a Kaibil y desde ahí se manda a una Compañía); aquí no se cambia.
 * Mismos roles y misma regla de acceso al Punto que el resto de la configuración OBSINT.
 */
@Path("/api/obsint/monitoring/points/{pointId}")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
public class ObsintMonitoringResource {
    @Inject ObsintPlatform platform;
    @Inject EntityManager em;

    public record MonitorOption(UUID employeeId, String name, String cargo) {}
    /** monitors vacío: lo atiende cualquier monitor de turno de la Compañía del Punto. */
    public record MonitoringView(UUID companyId, String companyName, List<MonitorOption> monitors, String updatedBy, Instant updatedAt) {}
    public record SaveMonitoring(List<UUID> employeeIds) {}

    @GET @Transactional
    public MonitoringView get(@PathParam("pointId") UUID pointId) {
        platform.requirePoint(pointId);
        return view(pointId);
    }

    /** Empleados activos de la Compañía del Punto con cargo de monitoreo: los que se pueden elegir como monitoristas. */
    @GET @Path("/monitors") @Transactional
    public List<MonitorOption> monitors(@PathParam("pointId") UUID pointId) {
        platform.requirePoint(pointId);
        UUID companyId = platform.monitoring(pointId).companyId();
        if (companyId == null) return List.of();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
            select employee_id, full_name, role_code from employee_operational_snapshot
            where instance_country_id=:tenant and company_id=:company and employment_status='ACTIVE' and role_code in (:cargos)
            order by full_name""")
            .setParameter("tenant", platform.instanceCountryId()).setParameter("company", companyId)
            .setParameter("cargos", MonitorShiftScope.MONITOR_CARGOS).getResultList();
        return rows.stream().map(r -> new MonitorOption(uuid(r[0]), (String) r[1], (String) r[2])).toList();
    }

    /** Reemplaza los monitoristas del Punto por los de la lista (vacía = ninguno). */
    @PUT @Consumes(MediaType.APPLICATION_JSON) @Transactional
    public MonitoringView save(@PathParam("pointId") UUID pointId, SaveMonitoring body) {
        platform.requirePoint(pointId);
        UUID companyId = platform.monitoring(pointId).companyId();
        if (companyId == null) throw new BadRequestException("Asigne primero el Punto a una Compañía en Servicios.");
        Set<UUID> chosen = new LinkedHashSet<>(body == null || body.employeeIds() == null ? List.of() : body.employeeIds());
        chosen.remove(null);
        Set<UUID> allowed = new HashSet<>();
        monitors(pointId).forEach(m -> allowed.add(m.employeeId()));
        if (!allowed.containsAll(chosen))
            throw new BadRequestException("Los monitoristas deben ser empleados activos de " + companyName(companyId) + " con cargo de monitoreo.");
        em.createNativeQuery("delete from obsint_point_monitor where instance_country_id=:tenant and point_id=:point")
            .setParameter("tenant", platform.instanceCountryId()).setParameter("point", pointId).executeUpdate();
        for (UUID employee : chosen)
            em.createNativeQuery("""
                insert into obsint_point_monitor(instance_country_id, point_id, employee_id, updated_by, updated_at)
                values (:tenant, :point, :employee, :user, now())""")
                .setParameter("tenant", platform.instanceCountryId()).setParameter("point", pointId).setParameter("employee", employee)
                .setParameter("user", platform.username()).executeUpdate();
        return view(pointId);
    }

    @ServerExceptionMapper
    public Response error(WebApplicationException e) {
        return Response.status(e.getResponse().getStatus()).entity(Map.of("message", e.getMessage())).type(MediaType.APPLICATION_JSON).build();
    }

    private MonitoringView view(UUID pointId) {
        UUID companyId = platform.monitoring(pointId).companyId();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
            select m.employee_id, coalesce(e.full_name, 'Empleado no encontrado'), e.role_code, m.updated_by, m.updated_at
            from obsint_point_monitor m
            left join employee_operational_snapshot e on e.instance_country_id=m.instance_country_id and e.employee_id=m.employee_id
            where m.instance_country_id=:tenant and m.point_id=:point order by 2""")
            .setParameter("tenant", platform.instanceCountryId()).setParameter("point", pointId).getResultList();
        List<MonitorOption> monitors = rows.stream().map(r -> new MonitorOption(uuid(r[0]), (String) r[1], (String) r[2])).toList();
        return new MonitoringView(companyId, companyName(companyId), monitors,
            rows.isEmpty() ? null : (String) rows.get(0)[3], rows.isEmpty() ? null : instant(rows.get(0)[4]));
    }

    private static String companyName(UUID companyId) {
        Company company = companyId == null ? null : Company.findById(companyId);
        return company == null ? null : company.name;
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant i) return i;
        if (value instanceof java.time.OffsetDateTime o) return o.toInstant();
        return ((java.sql.Timestamp) value).toInstant();
    }

    private static UUID uuid(Object value) { return value == null ? null : value instanceof UUID u ? u : UUID.fromString(value.toString()); }
}
