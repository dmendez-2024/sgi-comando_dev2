package com.cajamarca.sgi.comando.operator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;

/** Puertos externos utilizados por la aprobación: correo en DHO y creación de tarea en STC. */
public interface NoveltyIntegrationGateway {
    record CreatedTask(String taskCode, int attempts) {}

    String employeeEmail(UUID instanceCountryId, Long personaId);
    CreatedTask createStcTask(ObjectNode payload, UUID noveltyId, String approvingUserEmail);
}
