package com.cajamarca.sgi.comando.operator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoveltyStcApprovalServiceTest {

    @Test
    void sundayEndOfIsoWeekIsStoredAsGuayaquilEndOfDayConvertedToUtc() {
        assertEquals(Instant.parse("2026-11-09T04:59:59.999Z"),
            NoveltyStcApprovalService.endOfIsoWeek(Instant.parse("2026-11-06T19:00:00Z")));
    }

    @Test
    void payloadContainsEveryLegacyStcFieldAndMappedValues() {
        NoveltyStcApprovalService service = new NoveltyStcApprovalService();
        service.mapper = new ObjectMapper();
        Instant start = Instant.parse("2026-11-06T19:00:00Z");
        Instant end = Instant.parse("2026-11-09T04:59:59.999Z");
        var report = new NoveltyStcApprovalService.ReportData(
            UUID.randomUUID(), "INCIDENT", "Seguridad", "Presentación personal", "Prueba SGIComando",
            "Descripción ingresada", "HIGH", Instant.parse("2026-11-05T19:00:00Z"), "Kaibil",
            "Cajamarca Protective Services Monitoreo", "PTO-CM-EC-2386", 10L, 30L);

        ObjectNode body = service.payload(report, new NoveltyStcApprovalService.Codes(224, 229, 254, 10),
            start, end, "agente@cajamarca.ec", "coordinador@cajamarca.ec", "asistente@cajamarca.ec");

        Set<String> expected = new HashSet<>(Set.of("assignedTask", "category", "code", "createDate", "description",
            "documentDescriptionList", "endDate", "name", "owner", "photoUrl", "recurrence",
            "stakeholderTask", "startDate", "taskReference", "categoriaId", "subcategoriaId",
            "indicenteId", "categoryK", "subCategoryK", "incidenteK", "severityLevel", "statusId",
            "enterprise", "requestTaskDate", "idCompany", "nameCompany", "nameClient", "point", "platform"));
        body.fieldNames().forEachRemaining(expected::remove);
        assertTrue(expected.isEmpty(), "Faltan campos en el payload: " + expected);
        assertEquals("agente@cajamarca.ec", body.path("owner").asText());
        assertEquals("coordinador@cajamarca.ec", body.path("assignedTask").path(0).asText());
        assertEquals("asistente@cajamarca.ec", body.path("stakeholderTask").path(0).asText());
        assertEquals("Kaibil", body.path("nameCompany").asText());
        assertEquals("Cajamarca Protective Services Monitoreo", body.path("nameClient").asText());
        assertEquals("2026-11-05T19:00:00Z", body.path("requestTaskDate").asText());
        assertEquals(2386, body.path("point").asInt());
        assertEquals(4, body.path("platform").asInt());
        assertEquals("2026-11-09T04:59:59.999Z", body.path("recurrence").path("finishValue").asText());
        assertEquals(224, body.path("categoryK").path("id").asInt());
        assertEquals(229, body.path("subCategoryK").path("id").asInt());
        assertEquals(254, body.path("incidenteK").path("id").asInt());
        assertEquals(10, body.path("severityLevel").path("id").asInt());
        assertEquals(1, body.path("statusId").path("id").asInt());
        assertEquals(0, body.path("categoriaId").asInt());
        assertEquals(0, body.path("indicenteId").asInt());
        assertFalse(body.has("automaticStakeholderEmail"));
    }

    @Test
    void payloadSendsEmptyPointAndStakeholdersWhenSourceDataIsUnavailable() {
        NoveltyStcApprovalService service = new NoveltyStcApprovalService();
        service.mapper = new ObjectMapper();
        var report = new NoveltyStcApprovalService.ReportData(
            UUID.randomUUID(), "INCIDENT", "Seguridad", null, "Incidente", "Descripción", "MEDIUM",
            Instant.parse("2026-11-05T19:00:00Z"), "Kaibil", "Cliente", "PTO-SIN-NUMERO", 10L, null);

        ObjectNode body = service.payload(report, new NoveltyStcApprovalService.Codes(224, 0, 0, 9),
            Instant.parse("2026-11-06T19:00:00Z"), Instant.parse("2026-11-09T04:59:59.999Z"),
            null, "asistente@cajamarca.ec", null);

        assertEquals("", body.path("point").asText());
        assertTrue(body.path("owner").isNull());
        assertTrue(body.path("stakeholderTask").isEmpty());
    }
}
