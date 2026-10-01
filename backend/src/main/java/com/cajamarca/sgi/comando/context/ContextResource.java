package com.cajamarca.sgi.comando.context;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.integration.CoreCatalogAdapter;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import io.quarkus.security.identity.SecurityIdentity;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("/api/context")
@Produces(MediaType.APPLICATION_JSON)
public class ContextResource {
    @Inject TenantContext tenant;
    @Inject SecurityIdentity identity;
    @Inject CoreCatalogAdapter coreAdapter;
    @Inject CoreCatalogSyncService coreCatalog;

    @GET
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
    public Map<String,Object> get() {
        CoreInstanceCountrySnapshot snapshot;
        String syncStatus = "CURRENT";
        String syncMessage = null;
        try {
            snapshot = coreCatalog.synchronize(coreAdapter.fetchCatalog());
        } catch (RuntimeException error) {
            snapshot = coreCatalog.current();
            if (snapshot == null) throw new ServiceUnavailableException("CORE no está disponible y todavía no existe un catálogo sincronizado para este país. " + error.getMessage());
            syncStatus = "STALE";
            syncMessage = error.getMessage();
        }

        Map<String,Object> context = new LinkedHashMap<>();
        context.put("instanceCountryId", tenant.instanceCountryId());
        context.put("coreInstanceCountryId", snapshot.coreInstanceCountryId);
        context.put("country", snapshot.countryName);
        context.put("countryCode", snapshot.countryCode);
        context.put("locale", snapshot.locale);
        context.put("timezone", snapshot.timezone);
        context.put("currency", snapshot.currency);
        context.put("subdivisionType", snapshot.subdivisionType);
        context.put("subdivisionSingular", snapshot.subdivisionSingular);
        context.put("subdivisionPlural", snapshot.subdivisionPlural);
        context.put("territoryCatalogSource", "CORE");
        context.put("territorialDatasetVersion", snapshot.territorialDatasetVersion);
        context.put("coreCatalogSyncedAt", snapshot.syncedAt);
        context.put("coreSyncStatus", syncStatus);
        context.put("coreSyncMessage", syncMessage);
        context.put("username", identity.getPrincipal().getName());
        context.put("roles", identity.getRoles());
        return context;
    }
}
