package com.cajamarca.sgi.comando.operation;

import com.cajamarca.sgi.comando.interconnections.IntegrationResult;
import com.cajamarca.sgi.comando.interconnections.InterconnectionIds;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IncidentSanctionContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void usesTheNewCoreInterconnectionWithoutChangingInboundDhoBindings() {
        assertEquals("SGI_COM_SIC_DHO_0001_v001", InterconnectionIds.DHO_SANCTION_REGISTRATION);
        assertEquals("SGI_COM_SIC_DHO_0001_IF01", InterconnectionIds.DHO_SANCTION_REGISTRATION_INTERFACE);
        assertEquals("SIC_DHO_SGI_COM_0001_v001", InterconnectionIds.DHO_MASTER_EVENTS);
    }

    @Test
    void serializesEverySelectedEmployeeWithoutLegacyIdentityOrEvidence() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID firstEmployee = UUID.randomUUID();
        UUID secondEmployee = UUID.randomUUID();
        IncidentSanctionPayload payload = new IncidentSanctionPayload(
            eventId, incidentId, tenantId, 9001L,
            Instant.parse("2026-11-09T15:30:00Z"), "Sanción propuesta",
            Instant.parse("2026-11-09T15:35:00Z"),
            List.of(
                new IncidentSanctionPayload.Employee(firstEmployee, 101L, "Punto", "Cliente", "URRACA"),
                new IncidentSanctionPayload.Employee(secondEmployee, 102L, "Punto", "Cliente", "URRACA")
            )
        );

        JsonNode json = mapper.readTree(mapper.writeValueAsString(payload));
        assertEquals(eventId.toString(), json.path("eventId").asText());
        assertEquals(9001L, json.path("emitterPersonaId").asLong());
        assertEquals(2, json.path("employees").size());
        assertEquals(firstEmployee.toString(), json.path("employees").get(0).path("employeeId").asText());
        assertEquals(101L, json.path("employees").get(0).path("personaId").asLong());
        assertEquals("Punto", json.path("employees").get(0).path("punto").asText());
        assertFalse(json.has("emitterUsername"));
        assertFalse(json.has("companyId"));
        assertFalse(json.has("evidence"));
        assertFalse(json.has("reglamentoId"));
        assertFalse(json.path("employees").get(0).has("cedula"));
    }

    @Test
    void acceptsOnlyAConfirmedDhoResponse() {
        assertNull(rejection(200, "{\"success\":true}"));
        assertNull(rejection(201, ""));
    }

    @Test
    void translatesDhoErrorsIntoUnderstandableResponses() {
        assertFailure(200, "{\"success\":false,\"message\":\"Empleado no encontrado\"}",
            422, "DHO_REJECTED", "Empleado no encontrado");
        assertFailure(400, "{\"message\":\"personaId requerido\"}",
            422, "DHO_VALIDATION_ERROR", "personaId requerido");
        assertFailure(401, "", 502, "DHO_AUTHENTICATION_ERROR", "autenticar");
        assertFailure(404, "", 502, "DHO_ENDPOINT_NOT_FOUND", "no está disponible");
        assertFailure(409, "", 409, "DHO_IDEMPOTENCY_CONFLICT", "conflicto");
        assertFailure(503, "", 503, "DHO_PROCESSING_ERROR", "Intente nuevamente");
    }

    @Test
    void rejectsDuplicateCollaboratorsBeforeBuildingTheEvent() throws Exception {
        UUID employeeId = UUID.randomUUID();
        JsonNode ids = mapper.readTree("[\"" + employeeId + "\",\"" + employeeId + "\"]");
        assertThrows(jakarta.ws.rs.BadRequestException.class,
            () -> IncidentSanctionDeliveryService.collaboratorIds(ids));
    }

    @Test
    void queuedSanctionCannotReturnToDraft() throws Exception {
        UUID employeeId = UUID.randomUUID();
        IncidentNotificationEntity incident = new IncidentNotificationEntity();
        incident.sanctionEventId = UUID.randomUUID();
        JsonNode previous = mapper.readTree("""
            {"status":"FINALIZED","sanction":true,"sanctionDescription":"Texto","pointId":"p","collaboratorIds":["%s"]}
            """.formatted(employeeId));
        JsonNode incoming = mapper.readTree("""
            {"status":"DRAFT","sanction":true,"sanctionDescription":"Texto","pointId":"p","collaboratorIds":["%s"]}
            """.formatted(employeeId));
        assertThrows(jakarta.ws.rs.BadRequestException.class,
            () -> new IncidentSanctionDeliveryService().requireQueuedSanctionUnchanged(incident, previous, incoming));
    }

    private WebApplicationException rejection(int status, String body) {
        IntegrationResult result = new IntegrationResult();
        result.statusCode = status;
        result.body = body;
        return IncidentSanctionDeliveryService.rejection(result, mapper);
    }

    private void assertFailure(int remoteStatus, String body, int localStatus, String code, String messagePart) {
        WebApplicationException failure = rejection(remoteStatus, body);
        assertNotNull(failure);
        assertEquals(localStatus, failure.getResponse().getStatus());
        Map<?, ?> entity = (Map<?, ?>) failure.getResponse().getEntity();
        assertEquals(code, entity.get("code"));
        assertTrue(entity.get("message").toString().contains(messagePart));
    }
}
