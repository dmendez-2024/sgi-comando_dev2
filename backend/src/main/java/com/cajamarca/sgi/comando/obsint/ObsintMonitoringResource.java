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
 * Monitoristas OBSINT del Punto (pestaña OBSINT de Configuración del Punto): uno o más.
 * La Compañía del Punto es la que se asigna en Servicios (la orden llega a Kaibil y desde ahí se manda a una Compañía); aquí no se cambia.
 * Mismos roles y misma regla de acceso al Punto que el resto de la configuración OBSINT.
 *
 * <ul>
 *   <li>Cargos altos (nivel Kaibil): eligen monitores de todas las Compañías de su alcance (SEN-02).</li>
 *   <li>Demás roles: solo monitores de la Compañía del Punto. No pueden agregar ni quitar monitoristas de otra Compañía.</li>
 *   <li>Coordinación de Compañía: los monitoristas de otra Compañía llegan con {@code confidential=true}; la pantalla los
 *       muestra como "Confidencial". Es una medida visual: los datos reales viajan igual (SEN-04/05).</li>
 * </ul>
 */
@Path("/api/obsint/monitoring/points/{pointId}")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
public class ObsintMonitoringResource {
    @Inject ObsintPlatform platform;
    @Inject EntityManager em;

    /** confidential: bandera visual para Coordinación de Compañía (monitorista de otra Compañía). */
    public record MonitorOption(UUID employeeId, String name, String cargo, UUID companyId, String companyName, boolean confidential) {}
    /** monitors vacío: lo atiende cualquier monitor de turno de la Compañía del Punto. */
    public record MonitoringView(UUID companyId, String companyName, List<MonitorOption> monitors, String updatedBy, Instant updatedAt) {}
    public record SaveMonitoring(List<UUID> employeeIds) {}

    @GET @Transactional
    public MonitoringView get(@PathParam("pointId") UUID pointId) {
        platform.requirePoint(pointId);
        return view(pointId);
    }

    /**
     * Empleados activos con cargo de monitoreo que se pueden elegir como monitoristas:
     * de la Compañía del Punto, y para cargos altos además los de todas las Compañías de su alcance.
     */
    @GET @Path("/monitors") @Transactional
    public List<MonitorOption> monitors(@PathParam("pointId") UUID pointId) {
        platform.requirePoint(pointId);
        UUID companyId = platform.monitoring(pointId).companyId();
        if (companyId == null) return List.of();
        Set<UUID> companies = platform.monitorCompanies(companyId);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
            select e.employee_id, e.full_name, e.role_code, e.company_id, c.name
            from employee_operational_snapshot e
            left join company c on c.id=e.company_id
            where e.instance_country_id=:tenant and e.company_id in (:companies) and e.employment_status='ACTIVE' and e.role_code in (:cargos)
            order by case when e.company_id=:pointCompany then 0 else 1 end, c.name, e.full_name""")
            .setParameter("tenant", platform.instanceCountryId()).setParameter("companies", companies)
            .setParameter("pointCompany", companyId).setParameter("cargos", MonitorShiftScope.MONITOR_CARGOS).getResultList();
        return options(rows);
    }

    /** Reemplaza los monitoristas del Punto por los de la lista (vacía = ninguno). */
    @PUT @Consumes(MediaType.APPLICATION_JSON) @Transactional
    public MonitoringView save(@PathParam("pointId") UUID pointId, SaveMonitoring body) {
        platform.requirePoint(pointId);
        UUID companyId = platform.monitoring(pointId).companyId();
        if (companyId == null) throw new BadRequestException("Asigne primero el Punto a una Compañía en Servicios.");
        Set<UUID> chosen = new LinkedHashSet<>(body == null || body.employeeIds() == null ? List.of() : body.employeeIds());
        chosen.remove(null);

        Map<UUID, MonitorOption> current = new LinkedHashMap<>();
        view(pointId).monitors().forEach(m -> current.put(m.employeeId(), m));
        boolean high = platform.highRole();

        // Quitar: solo cargos altos pueden quitar monitoristas de otra Compañía (los que la Coordinación ve como "Confidencial").
        if (!high)
            for (MonitorOption m : current.values())
                if (!chosen.contains(m.employeeId()) && m.companyId() != null && !companyId.equals(m.companyId()))
                    throw new ForbiddenException("No puede quitar monitoristas de otra Compañía.");

        // Agregar: los nuevos deben estar entre los elegibles (los ya asignados se pueden mantener tal cual).
        Set<UUID> allowed = new HashSet<>();
        monitors(pointId).forEach(m -> allowed.add(m.employeeId()));
        for (UUID employee : chosen) {
            if (current.containsKey(employee) || allowed.contains(employee)) continue;
            UUID employeeCompany = employeeCompany(employee);
            if (!high && employeeCompany != null && !companyId.equals(employeeCompany))
                throw new ForbiddenException("No puede asignar monitoristas de otra Compañía.");
            throw new BadRequestException(high
                ? "Los monitoristas deben ser empleados activos con cargo de monitoreo de una Compañía de su alcance."
                : "Los monitoristas deben ser empleados activos de " + companyName(companyId) + " con cargo de monitoreo.");
        }

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
        return ObsintPlatform.errorResponse(e);
    }

    private MonitoringView view(UUID pointId) {
        UUID companyId = platform.monitoring(pointId).companyId();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
            select m.employee_id, coalesce(e.full_name, 'Empleado no encontrado'), e.role_code, e.company_id, c.name, m.updated_by, m.updated_at
            from obsint_point_monitor m
            left join employee_operational_snapshot e on e.instance_country_id=m.instance_country_id and e.employee_id=m.employee_id
            left join company c on c.id=e.company_id
            where m.instance_country_id=:tenant and m.point_id=:point order by 2""")
            .setParameter("tenant", platform.instanceCountryId()).setParameter("point", pointId).getResultList();
        return new MonitoringView(companyId, companyName(companyId), options(rows),
            rows.isEmpty() ? null : (String) rows.get(0)[5], rows.isEmpty() ? null : instant(rows.get(0)[6]));
    }

    /** Filas (employee_id, nombre, cargo, company_id, nombre Compañía, ...) con la bandera "Confidencial" para Coordinación. */
    private List<MonitorOption> options(List<Object[]> rows) {
        Set<UUID> own = platform.companyRole() ? platform.ownCompanies() : null;
        return rows.stream().map(r -> {
            UUID company = uuid(r[3]);
            boolean confidential = own != null && company != null && !own.contains(company);
            return new MonitorOption(uuid(r[0]), (String) r[1], (String) r[2], company, (String) r[4], confidential);
        }).toList();
    }

    private UUID employeeCompany(UUID employeeId) {
        List<?> rows = em.createNativeQuery("select company_id from employee_operational_snapshot where instance_country_id=:tenant and employee_id=:employee")
            .setParameter("tenant", platform.instanceCountryId()).setParameter("employee", employeeId).getResultList();
        return rows.isEmpty() ? null : uuid(rows.get(0));
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
