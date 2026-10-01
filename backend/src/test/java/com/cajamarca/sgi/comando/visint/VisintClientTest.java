package com.cajamarca.sgi.comando.visint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisintClientTest {
    @Test void mockRefusedOutsideUat() {
        VisintClient c = new VisintClient();
        c.mode = "MOCK"; c.uatEnabled = false; c.mock = new MockVisintAdapter();
        assertFalse(c.simulated());
        VisintUnavailableException e = assertThrows(VisintUnavailableException.class, () -> c.review(MockVisintAdapterTest.request("a".repeat(64))));
        assertTrue(e.getMessage().contains("fuera de UAT"));
    }
}
