package com.cajamarca.sgi.comando.interconnections;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

@Path("/api/internal/interconnections")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL"})
public class InterconnectionCatalogResource {
    @Inject InterconnectionCatalogService catalog;
    @Inject ResolutionCache cache;

    @GET
    @Path("/catalog")
    public Object catalog() { return catalog.all(); }

    @GET
    @Path("/catalog/{id}")
    public Object catalogItem(@PathParam("id") String id) { return catalog.byId(id); }

    @GET
    @Path("/cache")
    public Map<String,Object> cacheStatus() { return Map.of("entries", cache.size()); }

    @DELETE
    @Path("/cache")
    public Map<String,Object> clearCache() { cache.clear(); return Map.of("cleared", true); }

    @DELETE
    @Path("/cache/{id}")
    public Map<String,Object> invalidate(@PathParam("id") String id) { cache.invalidate(id); return Map.of("invalidated", id); }
}
