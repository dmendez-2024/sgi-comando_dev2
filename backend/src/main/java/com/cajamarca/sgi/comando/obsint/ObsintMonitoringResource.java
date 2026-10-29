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
 * Monitoreo OBSINT del Punto (pestaña OBSINT de Configuración del Punto): el monitorista responsable y su suplente.
 * La Compañía es la del Punto, la que se asigna en Servicios (la orden llega a Kaibil y desde ahí se manda a una Compañía);
 * aquí no se cambia. Mismos roles y misma regla de acceso al Punto que el resto de la configuración OBSINT.
 */
@Path("/api/obsint/monitoring/points/{pointId}")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
public class ObsintMonitoringResource {
    @Inject ObsintPlatform platform;
    @Inject EntityManager em;

    public record MonitorOption(UUID employeeId, String name, String cargo) {}
    /** configured=false: aún no se eligió responsable; lo atiende cualquier monitor de turno de la Compañía del Punto. */
    public record MonitoringView(UUID companyId, String companyName, boolean configured,
                                 MonitorOption responsible, MonitorOption backup, String updatedBy, Instant updatedAt) {}
    public record SaveMonitoring(UUID responsibleEmployeeId, UUID backupEmployeeId) {}

    @GET @Transactional
    public MonitoringView get(@PathParam("pointId") UUID pointId) {
        platform.requirePoint(pointId);
        return view(pointId);
    }

    /** Empleados activos de la Compañía del Punto con cargo de monitoreo: los que se pueden elegir como responsable o suplente. */
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

    @PUT @Consumes(MediaType.APPLICATION_JSON) @Transactional
    public MonitoringView save(@PathParam("pointId") UUID pointId, SaveMonitoring body) {
        platform.requirePoint(pointId);
        if (body == null) throw new BadRequestException("Solicitud obligatoria.");
        ObsintPlatform.Monitoring current = platform.monitoring(pointId);
        if (current.companyId() == null) throw new BadRequestException("Asigne primero el Punto a una Compañía en Servicios.");
        if (body.backupEmployeeId() != null && body.responsibleEmployeeId() == null)
            throw new BadRequestException("Elija primero el monitorista responsable; el suplente es opcional.");
        if (body.backupEmployeeId() != null && body.backupEmployeeId().equals(body.responsibleEmployeeId()))
            throw new BadRequestException("El suplente debe ser otra persona.");
        Set<UUID> allowed = new HashSet<>();
        monitors(pointId).forEach(m -> allowed.add(m.employeeId()));
        for (UUID employee : Arrays.asList(body.responsibleEmployeeId(), body.backupEmployeeId()))
            if (employee != null && !allowed.contains(employee))
                throw new BadRequestException("El monitorista debe ser un empleado activo de " + companyName(current.companyId()) + " con cargo de monitoreo.");
        em.createNativeQuery("""
            insert into obsint_point_monitoring(instance_country_id, point_id, responsible_employee_id, backup_employee_id, updated_by, updated_at)
            values (:tenant, :point, :responsible, :backup, :user, now())
            on conflict (instance_country_id, point_id) do update set responsible_employee_id=excluded.responsible_employee_id,
              backup_employee_id=excluded.backup_employee_id, updated_by=excluded.updated_by, updated_at=excluded.updated_at""")
            .setParameter("tenant", platform.instanceCountryId()).setParameter("point", pointId)
            .setParameter("responsible", body.responsibleEmployeeId()).setParameter("backup", body.backupEmployeeId())
            .setParameter("user", platform.username()).executeUpdate();
        return view(pointId);
    }

    @ServerExceptionMapper
    public Response error(WebApplicationException e) {
        return Response.status(e.getResponse().getStatus()).entity(Map.of("message", e.getMessage())).type(MediaType.APPLICATION_JSON).build();
    }

    private MonitoringView view(UUID pointId) {
        ObsintPlatform.Monitoring m = platform.monitoring(pointId);
        @SuppressWarnings("unchecked")
        List<Object[]> meta = em.createNativeQuery("select updated_by, updated_at from obsint_point_monitoring where instance_country_id=:tenant and point_id=:point")
            .setParameter("tenant", platform.instanceCountryId()).setParameter("point", pointId).getResultList();
        boolean configured = !meta.isEmpty() && m.responsibleEmployeeId() != null;
        return new MonitoringView(m.companyId(), companyName(m.companyId()), configured, person(m.responsibleEmployeeId()), person(m.backupEmployeeId()),
            meta.isEmpty() ? null : (String) meta.get(0)[0], meta.isEmpty() ? null : instant(meta.get(0)[1]));
    }

    private static String companyName(UUID companyId) {
        Company company = companyId == null ? null : Company.findById(companyId);
        return company == null ? null : company.name;
    }

    private MonitorOption person(UUID employeeId) {
        if (employeeId == null) return null;
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("select full_name, role_code from employee_operational_snapshot where instance_country_id=:tenant and employee_id=:employee")
            .setParameter("tenant", platform.instanceCountryId()).setParameter("employee", employeeId).getResultList();
        return rows.isEmpty() ? new MonitorOption(employeeId, "Empleado no encontrado", null) : new MonitorOption(employeeId, (String) rows.get(0)[0], (String) rows.get(0)[1]);
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant i) return i;
        if (value instanceof java.time.OffsetDateTime o) return o.toInstant();
        return ((java.sql.Timestamp) value).toInstant();
    }

    private static UUID uuid(Object value) { return value == null ? null : value instanceof UUID u ? u : UUID.fromString(value.toString()); }
}
