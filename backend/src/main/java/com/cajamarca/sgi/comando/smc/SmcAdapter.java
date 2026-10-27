package com.cajamarca.sgi.comando.smc;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.integration.ExternalPorts;
import com.cajamarca.sgi.comando.interconnections.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** CORE resuelve destino y credencial; no existe URL ni token propio de SMC. */
@ApplicationScoped
public class SmcAdapter implements ExternalPorts.SmcPort {
    @Inject GenericInterconnectionExecutor executor;
    @Inject TenantContext tenant;
    @ConfigProperty(name="sgi.smc.interconnection-id") String interconnectionId;
    @ConfigProperty(name="sgi.smc.interface-id") String interfaceId;

    public IntegrationResult send(UUID instanceCountryId, Map<String,Object> payload) {
        if (!tenant.instanceCountryId().equals(instanceCountryId)
            || !instanceCountryId.toString().equals(Objects.toString(payload.get("instanceCountryId"))))
            throw new IllegalArgumentException("El hecho no corresponde a la instancia activa");
        String eventId = Objects.toString(payload.get("eventId"), "");
        if (eventId.isBlank()) throw new IllegalArgumentException("eventId es obligatorio");
        IntegrationRequest request = new IntegrationRequest();
        request.requireOpenBaoCredentials = true;
        request.body = payload;
        request.idempotencyKey = eventId;
        request.correlationId = eventId;
        return executor.execute(interconnectionId, interfaceId, request);
    }

    @Override
    public void publishFact(UUID instanceCountryId, String factType, Map<String,Object> payload) {
        if (!Objects.equals(factType, payload.get("factType"))) throw new IllegalArgumentException("factType no coincide");
        IntegrationResult result = send(instanceCountryId, payload);
        SmcDeliveryOutcome outcome = SmcDeliveryOutcome.from(result);
        if (!"SENT".equals(outcome.status()))
            throw new InterconnectionException("SMC_NOT_ACCEPTED", "SMC no confirmó la recepción del hecho");
    }
}
