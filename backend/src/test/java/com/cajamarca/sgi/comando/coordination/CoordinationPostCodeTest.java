package com.cajamarca.sgi.comando.coordination;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoordinationPostCodeTest {
    @Test
    void buildsCountryCompanyAndTypePrefix() {
        assertEquals("ECU-KAI-MON-001",
            CoordinationResource.formatPostCode("ecu", "KAI-001", "MONITORING", List.of()));
        assertEquals("ECU-KAI-SUP-001",
            CoordinationResource.formatPostCode("ECU", "KAI-001", "SUPERVISION", List.of()));
    }

    @Test
    void continuesNumberingAcrossLegacyAndNewCodes() {
        assertEquals("ECU-KAI-MON-003",
            CoordinationResource.formatPostCode("ECU", "KAI-001", "MONITORING",
                List.of("MON-001", "ECU-KAI-MON-002")));
        assertEquals("ECU-COM-SUP-004",
            CoordinationResource.formatPostCode("ECU", "COM-001", "SUPERVISION",
                List.of("SUP-001", "SUP-002", "ECU-COM-SUP-003")));
    }
}
