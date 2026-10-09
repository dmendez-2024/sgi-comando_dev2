package com.cajamarca.sgi.comando.ident;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ServiceUnavailableException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

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

    @Test
    void skipsBearerValidationOnlyWhenAuthenticationIsExplicitlyDisabled() {
        IdentEmployeeQueryResource resource = new IdentEmployeeQueryResource();
        resource.authenticationEnabled = false;
        resource.interconnectionsEnvironment = "DEVELOPMENT";

        assertDoesNotThrow(() -> resource.requireServiceCredential(null));
    }

    @Test
    void requiresCredentialByDefault() {
        IdentEmployeeQueryResource resource = new IdentEmployeeQueryResource();
        resource.credentialRef = Optional.empty();

        assertThrows(ServiceUnavailableException.class, () -> resource.requireServiceCredential(null));
    }

    @Test
    void cannotDisableBearerValidationOutsideDevelopment() {
        IdentEmployeeQueryResource resource = new IdentEmployeeQueryResource();
        resource.authenticationEnabled = false;
        resource.interconnectionsEnvironment = "PRODUCTION";
        resource.credentialRef = Optional.empty();

        assertThrows(ServiceUnavailableException.class, () -> resource.requireServiceCredential(null));
    }
}
