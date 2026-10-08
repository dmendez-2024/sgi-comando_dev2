package com.cajamarca.sgi.comando.impulses;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.operator.OperatorContext;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/** Saldo de Impulsos del Operador autenticado (SGI: Operador). También llega dentro de GET /api/v1/operator/runtime. */
@Path("/api/v1/operator/impulses") @Authenticated @Produces(MediaType.APPLICATION_JSON)
public class OperatorImpulseResource {
    @Inject TenantContext tenant;
    @Inject OperatorContext operator;
    @Inject ImpulseLedger ledger;

    @GET
    public ObjectNode mine() {
        return ledger.summary(tenant.instanceCountryId(), operator.employee());
    }
}
