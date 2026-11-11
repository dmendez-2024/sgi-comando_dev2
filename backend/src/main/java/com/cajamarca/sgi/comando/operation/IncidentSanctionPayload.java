package com.cajamarca.sgi.comando.operation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contrato v1 enviado a SIC:DHO cuando un incidente finalizado genera sanción. */
public record IncidentSanctionPayload(
    UUID eventId,
    UUID incidentId,
    UUID instanceCountryId,
    Long emitterPersonaId,
    Instant infraccionFecha,
    String observacion,
    Instant syncFecha,
    List<Employee> employees
) {
    public record Employee(
        UUID employeeId,
        Long personaId,
        String punto,
        String cliente,
        String compania
    ) {}
}
