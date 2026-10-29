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
 * Monitoreo OBSINT del Punto (pestaña OBSINT de Configuración del Punto): qué Compañía lo monitorea y quién es el
 * monitorista responsable y su suplente. La orden OBSINT llega a Kaibil; desde aquí se puede pasar a otra Compañía.
 * Mismos roles y misma regla de acceso al Punto que el resto de la configuración OBSINT.
 */
@Path("/api/obsint/monitoring/points/{pointId}")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
public class ObsintMonitoringResource {
    @Inject ObsintPlatform platform;
    @Inject EntityManager em;

    public record MonitorOption(UUID employeeId, String name, String cargo) {}
    public record CompanyOption(UUID id, String name, boolean kaibil) {}
    /** configured=false: el Punto sigue con lo que trae la orden (Kaibil, sin responsable). */
    public record MonitoringView(UUID companyId, String companyName, boolean kaibil, boolean configured,
                                 MonitorOption responsible, MonitorOption backup, String updatedBy, Instant updatedAt) {}
    public record SaveMonitoring(UUID companyId, UUID responsibleEmployeeId, UUID backupEmployeeId) {}

    @GET @Transactional
    public MonitoringView get(@PathParam("pointId") UUID pointId) {
        platform.requirePoint(pointId);
        return view(pointId);
    }

    /** Compañías activas que pueden monitorear el Punto; Kaibil primero. */
    @GET @Path("/companies") @Transactional
    public List<CompanyOption> companies(@PathParam("pointId") UUID pointId) {
        platform.requirePoint(pointId);
        UUID kaibil = platform.kaibilCompanyId();
        return Company.<Company>list("instanceCountryId=?1 and status='ACTIVE' order by name", platform.instanceCountryId()).stream()
            .map(c -> new CompanyOption(c.id, c.name, c.id.equals(kaibil)))
            .sorted(Comparator.comparing((CompanyOption c) -> !c.kaibil())).toList();
    }

    /** Empleados activos de esa Compañía con cargo de monitoreo: los que se pueden elegir como responsable o suplente. */
    @GET @Path("/monitors") @Transactional
    public List<MonitorOption> monitors(@PathParam("pointId") UUID pointId, @QueryParam("companyId") UUID companyId) {
        platform.requirePoint(pointId);
        if (companyId == null) throw new BadRequestException("companyId obligatorio.");
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
        if (body == null || body.companyId() == null) throw new BadRequestException("Seleccione la Compañía que monitorea el Punto.");
        Company company = Company.find("id=?1 and instanceCountryId=?2 and status='ACTIVE'", body.companyId(), platform.instanceCountryId()).firstResult();
        if (company == null) throw new BadRequestException("La Compañía seleccionada no existe o no está activa.");
        if (body.backupEmployeeId() != null && body.responsibleEmployeeId() == null)
            throw new BadRequestException("Elija primero el monitorista responsable; el suplente es opcional.");
        if (body.backupEmployeeId() != null && body.backupEmployeeId().equals(body.responsibleEmployeeId()))
            throw new BadRequestException("El suplente debe ser otra persona.");
        Set<UUID> allowed = new HashSet<>();
        monitors(pointId, company.id).forEach(m -> allowed.add(m.employeeId()));
        for (UUID employee : Arrays.asList(body.responsibleEmployeeId(), body.backupEmployeeId()))
            if (employee != null && !allowed.contains(employee))
                throw new BadRequestException("El monitorista debe ser un empleado activo de " + company.name + " con cargo de monitoreo.");
        em.createNativeQuery("""
            insert into obsint_point_monitoring(instance_country_id, point_id, company_id, responsible_employee_id, backup_employee_id, updated_by, updated_at)
            values (:tenant, :point, :company, :responsible, :backup, :user, now())
            on conflict (instance_country_id, point_id) do update set company_id=excluded.company_id,
              responsible_employee_id=excluded.responsible_employee_id, backup_employee_id=excluded.backup_employee_id,
              updated_by=excluded.updated_by, updated_at=excluded.updated_at""")
            .setParameter("tenant", platform.instanceCountryId()).setParameter("point", pointId).setParameter("company", company.id)
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
        Company company = m.companyId() == null ? null : Company.findById(m.companyId());
        @SuppressWarnings("unchecked")
        List<Object[]> meta = em.createNativeQuery("select updated_by, updated_at from obsint_point_monitoring where instance_country_id=:tenant and point_id=:point")
            .setParameter("tenant", platform.instanceCountryId()).setParameter("point", pointId).getResultList();
        return new MonitoringView(m.companyId(), company == null ? null : company.name, m.companyId() != null && m.companyId().equals(platform.kaibilCompanyId()),
            !meta.isEmpty(), person(m.responsibleEmployeeId()), person(m.backupEmployeeId()),
            meta.isEmpty() ? null : (String) meta.get(0)[0], meta.isEmpty() ? null : instant(meta.get(0)[1]));
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
