package com.cajamarca.sgi.comando.ident;

import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentEmployeeQueryResourceContractTest {

    @Test
    void acceptsOnlyIdentInterconnection() {
        IdentEmployeeQueryResource resource = new IdentEmployeeQueryResource();

        assertDoesNotThrow(() -> resource.validateContractHeaders(
            "test-correlation-id",
            "IDENT_SGI_COM_0001_v001",
            "v1"
        ));

        assertThrows(BadRequestException.class, () -> resource.validateContractHeaders(
            "test-correlation-id",
            "SIC_RRHH_SGI_COM_0001_v001",
            "v1"
        ));
    }
}
