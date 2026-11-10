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
    void payloadContainsEveryLegacyStcFieldAndKeepsMissingEmailsEmpty() {
        NoveltyStcApprovalService service = new NoveltyStcApprovalService();
        service.mapper = new ObjectMapper();
        Instant start = Instant.parse("2026-11-06T19:00:00Z");
        Instant end = Instant.parse("2026-11-09T04:59:59.999Z");
        var report = new NoveltyStcApprovalService.ReportData(
            UUID.randomUUID(), "INCIDENT", "Seguridad", "Presentación personal", "Prueba SGIComando",
            "Descripción ingresada", "HIGH", Instant.parse("2026-11-05T19:00:00Z"), "Kaibil",
            10L, 20L, 30L);

        ObjectNode body = service.payload(report, new NoveltyStcApprovalService.Codes(224, 229, 254, 10),
            start, end, null, null, null);

        Set<String> expected = new HashSet<>(Set.of("assignedTask", "category", "code", "createDate", "description",
            "documentDescriptionList", "endDate", "name", "owner", "photoUrl", "recurrence",
            "stakeholderTask", "startDate", "taskReference", "categoriaId", "subcategoriaId",
            "indicenteId", "categoryK", "subCategoryK", "incidenteK", "severityLevel", "statusId",
            "enterprise", "requestTaskDate", "idCompany", "nameCompany", "nameClient", "point", "platform"));
        body.fieldNames().forEachRemaining(expected::remove);
        assertTrue(expected.isEmpty(), "Faltan campos en el payload: " + expected);
        assertTrue(body.path("owner").isNull());
        assertTrue(body.path("assignedTask").isEmpty());
        assertTrue(body.path("stakeholderTask").isEmpty());
        assertEquals("", body.path("nameClient").asText());
        assertEquals("2026-11-05T19:00:00Z", body.path("requestTaskDate").asText());
        assertEquals("2026-11-09T04:59:59.999Z", body.path("recurrence").path("finishValue").asText());
        assertEquals(224, body.path("categoryK").path("id").asInt());
        assertEquals(229, body.path("subCategoryK").path("id").asInt());
        assertEquals(254, body.path("incidenteK").path("id").asInt());
        assertEquals(10, body.path("severityLevel").path("id").asInt());
        assertEquals(0, body.path("categoriaId").asInt());
        assertEquals(0, body.path("indicenteId").asInt());
        assertFalse(body.has("automaticStakeholderEmail"));
    }
}
