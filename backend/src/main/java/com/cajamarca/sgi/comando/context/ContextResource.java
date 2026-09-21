package com.cajamarca.sgi.comando.context;

import com.cajamarca.sgi.comando.common.TenantContext;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import io.quarkus.security.identity.SecurityIdentity;
import java.util.Map;

@Path("/api/context")
@Produces(MediaType.APPLICATION_JSON)
public class ContextResource {
    @Inject TenantContext tenant;
    @Inject SecurityIdentity identity;

    @GET
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
    public Map<String,Object> get() {
        return Map.ofEntries(
            Map.entry("instanceCountryId", tenant.instanceCountryId()),
            Map.entry("country", "Ecuador"),
            Map.entry("countryCode", "EC"),
            Map.entry("locale", "es-EC"),
            Map.entry("timezone", "America/Guayaquil"),
            Map.entry("currency", "USD"),
            Map.entry("subdivisionType", "PROVINCE"),
            Map.entry("subdivisionSingular", "Provincia"),
            Map.entry("subdivisionPlural", "Provincias"),
            Map.entry("territoryCatalogSource", "CORE LOCAL · UAT"),
            Map.entry("username", identity.getPrincipal().getName()),
            Map.entry("roles", identity.getRoles())
        );
    }
}
