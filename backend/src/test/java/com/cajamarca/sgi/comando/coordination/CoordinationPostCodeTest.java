package com.cajamarca.sgi.comando.coordination;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoordinationPostCodeTest {
    @Test
    void buildsShortTypePrefix() {
        assertEquals("MON-001",
            CoordinationResource.formatPostCode("MONITORING", List.of()));
        assertEquals("SUP-001",
            CoordinationResource.formatPostCode("SUPERVISION", List.of()));
    }

    @Test
    void continuesNumberingAcrossLegacyAndNewCodes() {
        assertEquals("MON-003",
            CoordinationResource.formatPostCode("MONITORING",
                List.of("MON-001", "ECU-KAI-MON-002")));
        assertEquals("SUP-004",
            CoordinationResource.formatPostCode("SUPERVISION",
                List.of("SUP-001", "SUP-002", "ECU-COM-SUP-003")));
    }
}
