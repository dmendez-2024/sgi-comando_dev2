package com.cajamarca.sgi.comando.rrhh;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RrhhEmployeeSyncServiceContractTest {
    private final RrhhEmployeeSyncService service = new RrhhEmployeeSyncService();

    @Test
    void additiveProfileFieldsParticipateInIdempotencyHash() {
        var base = request("0102030405", "juan.paguay@empresa.com", null);
        assertNotEquals(service.contentHash(base), service.contentHash(
            request("0102030406", "juan.paguay@empresa.com", null)
        ));
        assertNotEquals(service.contentHash(base), service.contentHash(
            request("0102030405", "otro@empresa.com", null)
        ));
        assertNotEquals(service.contentHash(base), service.contentHash(
            request("0102030405", "juan.paguay@empresa.com", "/9j/4AAQ")
        ));
    }

    private static RrhhEmployeeEventResource.EmployeeEventRequest request(
        String identificationNumber, String corporateEmail, String photoBase64
    ) {
        return new RrhhEmployeeEventResource.EmployeeEventRequest(
            42L,
            42L,
            null,
            "Paguay Lasso Juan David",
            "Agente de Seguridad",
            "ACTIVE",
            Instant.parse("2026-11-12T15:00:00Z"),
            null,
            null,
            null,
            null,
            identificationNumber,
            corporateEmail,
            photoBase64
        );
    }
}
