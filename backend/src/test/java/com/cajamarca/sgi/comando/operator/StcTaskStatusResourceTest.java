package com.cajamarca.sgi.comando.operator;

import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StcTaskStatusResourceTest {

    @Test
    void acceptsOnlyTheFinalizedState() {
        assertEquals("FINALIZED", StcTaskStatusResource.status(" finalized "));
        WebApplicationException error = assertThrows(WebApplicationException.class,
            () -> StcTaskStatusResource.status("COMPLETED"));
        assertEquals(400, error.getResponse().getStatus());
    }

    @Test
    void trimsAndValidatesTheTaskCode() {
        assertEquals("9a122c25-92b4-43ba-9889-8d8cf8483713",
            StcTaskStatusResource.taskCode(" 9a122c25-92b4-43ba-9889-8d8cf8483713 "));
        assertEquals(400, assertThrows(WebApplicationException.class,
            () -> StcTaskStatusResource.taskCode(" ")).getResponse().getStatus());
    }
}
