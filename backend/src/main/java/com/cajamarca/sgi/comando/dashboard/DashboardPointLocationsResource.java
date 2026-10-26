package com.cajamarca.sgi.comando.dashboard;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.hibernate.query.NativeQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Path("/api/dashboard/point-locations")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
public class DashboardPointLocationsResource {
    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;
    @Inject EntityManager em;

    public record PointLocation(UUID id, String province, String city) {}

    @GET
    public List<PointLocation> locations() {
        Set<UUID> allowed = scope.allowedCompanyIds();
        if (allowed.isEmpty()) return List.of();
        NativeQuery<?> query = em.createNativeQuery("""
            select id, province, city from point
            where instance_country_id=:tenant and company_id in (:companies)
            order by id
            """).unwrap(NativeQuery.class);
        query.setParameter("tenant", tenant.instanceCountryId());
        query.setParameterList("companies", allowed);
        List<PointLocation> locations = new ArrayList<>();
        for (Object result : query.getResultList()) {
            Object[] row = (Object[]) result;
            locations.add(new PointLocation((UUID) row[0], (String) row[1], (String) row[2]));
        }
        return locations;
    }
}
