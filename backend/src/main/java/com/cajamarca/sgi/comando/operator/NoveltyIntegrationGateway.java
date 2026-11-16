package com.cajamarca.sgi.comando.operator;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;

/** Puerto externo utilizado por la aprobación para crear la tarea en STC. */
public interface NoveltyIntegrationGateway {
    record CreatedTask(String taskCode, int attempts) {}

    CreatedTask createStcTask(ObjectNode payload, UUID noveltyId, String approvingUserEmail);
}
