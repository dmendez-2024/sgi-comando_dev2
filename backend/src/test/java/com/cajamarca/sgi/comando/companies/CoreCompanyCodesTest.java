package com.cajamarca.sgi.comando.companies;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreCompanyCodesTest {
    private static final UUID GALVARINO = UUID.fromString("a2000000-0000-0000-0000-000000000001");

    @Test
    void generatesTheNextFreeSequentialCodeAndReservesIt() {
        Set<String> used = new HashSet<>(Set.of("COM-001", "COM-005", "KAIBIL"));

        assertEquals("COM-006", CoreCompanyCodes.next(used));
        assertEquals("COM-007", CoreCompanyCodes.next(used));
        assertTrue(used.contains("COM-006"));
    }

    @Test
    void startsAtOneWhenTheTenantHasNoSequentialCodes() {
        assertEquals("COM-001", CoreCompanyCodes.next(new HashSet<>()));
    }

    @Test
    void sourceVersionIsStableAndChangesOnlyWithTheContent() {
        String first = CoreCompanyCodes.sourceVersion(GALVARINO, "Galvarino", "Seguridad física", null, "SECURITY", "ACTIVE");
        String again = CoreCompanyCodes.sourceVersion(GALVARINO, "Galvarino", "Seguridad física", null, "SECURITY", "ACTIVE");
        String renamed = CoreCompanyCodes.sourceVersion(GALVARINO, "Galvarino S.A.", "Seguridad física", null, "SECURITY", "ACTIVE");
        String newLogo = CoreCompanyCodes.sourceVersion(GALVARINO, "Galvarino", "Seguridad física", "/api/v1/companies/x/logo?v=2", "SECURITY", "ACTIVE");

        assertEquals(first, again);
        assertNotEquals(first, renamed);
        assertNotEquals(first, newLogo);
        assertTrue(first.startsWith("CORE-") && first.length() <= 80);
    }
}
