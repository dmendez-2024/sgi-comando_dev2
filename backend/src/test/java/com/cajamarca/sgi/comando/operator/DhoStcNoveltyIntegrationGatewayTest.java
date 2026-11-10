package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.interconnections.GenericInterconnectionExecutor;
import com.cajamarca.sgi.comando.interconnections.IntegrationRequest;
import com.cajamarca.sgi.comando.interconnections.IntegrationResult;
import com.cajamarca.sgi.comando.interconnections.InterconnectionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DhoStcNoveltyIntegrationGatewayTest {

    @Test
    void dhoSendsTenantAndPersonaAndUsesThreeTimeoutOnlyRetries() {
        CapturingExecutor executor = new CapturingExecutor(result(200, "{\"correo\":\"persona@cajamarca.ec\"}", 1));
        DhoStcNoveltyIntegrationGateway gateway = gateway(executor, "token");
        UUID tenant = UUID.randomUUID();

        assertEquals("persona@cajamarca.ec", gateway.employeeEmail(tenant, 6125L));
        assertEquals("SGI_COM_SIC_DHO_0002_v001", executor.interconnectionId);
        assertEquals("SGI_COM_SIC_DHO_0002_IF01", executor.interfaceId);
        assertEquals(3, executor.request.maxRetriesOverride);
        assertTrue(executor.request.retryOnlyTimeouts);
        assertFalse(executor.request.retryServerErrors);
        @SuppressWarnings("unchecked") Map<String,Object> body = (Map<String,Object>) executor.request.body;
        assertEquals(tenant.toString(), body.get("instanceCountryId"));
        assertEquals(6125L, body.get("personaId"));
    }

    @Test
    void dhoFailureIsConvertedToNull() {
        CapturingExecutor executor = new CapturingExecutor(new InterconnectionException("TRANSPORT_TIMEOUT", "timeout"));
        assertNull(gateway(executor, "token").employeeEmail(UUID.randomUUID(), 6125L));
    }

    @Test
    void stcSendsRequiredHeadersRetriesOnceAndReturnsFirstTaskCode() {
        CapturingExecutor executor = new CapturingExecutor(result(200,
            "{\"code\":100,\"message\":\"Created\",\"taskCodeList\":[\"9a122c25-92b4-43ba-9889-8d8cf8483713\"]}", 2));
        DhoStcNoveltyIntegrationGateway gateway = gateway(executor, "secret-token");

        var created = gateway.createStcTask(new ObjectMapper().createObjectNode().put("name", "Prueba"),
            UUID.randomUUID(), "aprobador@cajamarca.ec");

        assertEquals("9a122c25-92b4-43ba-9889-8d8cf8483713", created.taskCode());
        assertEquals(2, created.attempts());
        assertEquals(1, executor.request.maxRetriesOverride);
        assertEquals("KTASK", executor.request.headers.get("app"));
        assertEquals("WEB", executor.request.headers.get("channel"));
        assertEquals("aprobador@cajamarca.ec", executor.request.headers.get("user"));
        assertEquals("secret-token", executor.request.headers.get("ktoken"));
    }

    @Test
    void ambiguousStcTimeoutReturnsClearError() {
        CapturingExecutor executor = new CapturingExecutor(new InterconnectionException("TRANSPORT_TIMEOUT", "timeout"));
        NoveltyIntegrationException error = assertThrows(NoveltyIntegrationException.class, () ->
            gateway(executor, "token").createStcTask(new ObjectMapper().createObjectNode(),
                UUID.randomUUID(), "aprobador@cajamarca.ec"));
        assertEquals("STC_RESULT_UNKNOWN", error.code());
        assertEquals(504, error.status());
    }

    private static DhoStcNoveltyIntegrationGateway gateway(CapturingExecutor executor, String token) {
        DhoStcNoveltyIntegrationGateway gateway = new DhoStcNoveltyIntegrationGateway();
        gateway.executor = executor;
        gateway.mapper = new ObjectMapper();
        gateway.stcToken = Optional.ofNullable(token);
        return gateway;
    }

    private static IntegrationResult result(int status, String body, int attempts) {
        IntegrationResult result = new IntegrationResult();
        result.statusCode = status;
        result.body = body;
        result.attempts = attempts;
        return result;
    }

    static class CapturingExecutor extends GenericInterconnectionExecutor {
        final IntegrationResult result;
        final RuntimeException error;
        String interconnectionId;
        String interfaceId;
        IntegrationRequest request;

        CapturingExecutor(IntegrationResult result) { this.result = result; this.error = null; }
        CapturingExecutor(RuntimeException error) { this.result = null; this.error = error; }

        @Override
        public IntegrationResult execute(String interconnectionId, String interfaceId, IntegrationRequest request) {
            this.interconnectionId = interconnectionId;
            this.interfaceId = interfaceId;
            this.request = request;
            if (error != null) throw error;
            return result;
        }
    }
}
