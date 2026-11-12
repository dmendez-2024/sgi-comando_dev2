package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/operator/shift-sync") @Authenticated
@Consumes(MediaType.APPLICATION_JSON) @Produces(MediaType.APPLICATION_JSON)
public class ShiftSyncResource {
    @Inject OperatorContext operator;
    @Inject TenantContext tenant;
    @Inject ShiftSyncService service;
    @Inject io.quarkus.security.identity.SecurityIdentity identity;
    @POST public JsonNode submit(JsonNode request) {
        var tokenTenant=com.cajamarca.sgi.comando.security.IdentIdentity.instanceCountryId(identity);
        if(tokenTenant!=null&&!tokenTenant.equals(tenant.instanceCountryId()))throw new ForbiddenException("Instancia fuera del alcance del token");
        var employee=operator.employee();
        if(!operator.isAgent())throw new ForbiddenException("Solo el Agente puede finalizar su turno");
        return service.submit(tenant.instanceCountryId(),employee,operator.username(),request);
    }
}
