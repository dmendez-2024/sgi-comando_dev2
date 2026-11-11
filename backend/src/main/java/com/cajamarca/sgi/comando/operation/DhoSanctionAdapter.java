package com.cajamarca.sgi.comando.operation;

import com.cajamarca.sgi.comando.interconnections.GenericInterconnectionExecutor;
import com.cajamarca.sgi.comando.interconnections.IntegrationRequest;
import com.cajamarca.sgi.comando.interconnections.IntegrationResult;
import com.cajamarca.sgi.comando.interconnections.InterconnectionIds;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

/** Salida SGI_COM -> SIC_DHO. CORE resuelve URL, método y credentialRef. */
@ApplicationScoped
public class DhoSanctionAdapter {
    @Inject GenericInterconnectionExecutor executor;

    public IntegrationResult send(JsonNode payload) {
        UUID eventId = requiredUuid(payload, "eventId");
        requiredUuid(payload, "incidentId");
        requiredUuid(payload, "instanceCountryId");
        if (payload.path("employees").isEmpty()) throw new IllegalArgumentException("employees es obligatorio");

        IntegrationRequest request = new IntegrationRequest();
        request.body = payload;
        request.idempotencyKey = eventId.toString();
        request.correlationId = eventId.toString();
        // Esta integración legado usa SERVICE_TOKEN resuelto desde SGI_CREDENTIAL_<credentialRef>.
        request.requireOpenBaoCredentials = false;
        return executor.execute(
            InterconnectionIds.DHO_SANCTION_REGISTRATION,
            InterconnectionIds.DHO_SANCTION_REGISTRATION_INTERFACE,
            request
        );
    }

    private static UUID requiredUuid(JsonNode payload, String field) {
        try { return UUID.fromString(payload.path(field).asText()); }
        catch (RuntimeException error) { throw new IllegalArgumentException(field + " inválido", error); }
    }
}
