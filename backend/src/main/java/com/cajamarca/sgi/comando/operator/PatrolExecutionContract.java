package com.cajamarca.sgi.comando.operator;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.*;
import java.time.Instant;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.ReliefContract.*;

/** Forma del lote PATROL_CHECKPOINT_COMPLETED enviado por SGI: Operador. Solo valida estructura e identidad. */
public final class PatrolExecutionContract {
    private PatrolExecutionContract() {}
    public static final String TYPE = "PATROL_CHECKPOINT_COMPLETED";

    public static void validate(JsonNode batch, UUID employee, UUID country, Instant now) {
        uuid(batch, "batchId"); uuid(batch, "correlationId");
        if (!employee.equals(uuid(batch, "employeeId")) || !country.equals(uuid(batch, "instanceCountryId"))) throw new ForbiddenException("Identidad o instancia incorrecta");
        if (text(batch, "deviceId").length() > 120) throw new BadRequestException("deviceId demasiado largo");
        Instant captured = time(batch, "capturedAt");
        JsonNode events = batch.path("events");
        if (!events.isArray() || events.size() != 1) throw new BadRequestException("Se admite un evento por lote");
        JsonNode e = events.get(0);
        if (!TYPE.equals(text(e, "type"))) throw new BadRequestException("Tipo de evento no soportado");
        for (String k : new String[]{"eventId", "assignmentId", "patrolRunId", "patrolId", "checkpointId"}) uuid(e, k);
        Instant executed = time(e, "executedAt");
        if (executed.isAfter(now.plusSeconds(300)) || executed.isAfter(captured.plusSeconds(300))) throw new BadRequestException("Fecha de ejecución futura");
        if (e.hasNonNull("observation") && e.path("observation").asText().length() > 1000) throw new BadRequestException("Observación demasiado larga");
        JsonNode ids = e.path("evidenceIds");
        if (!ids.isArray()) throw new BadRequestException("evidenceIds debe ser una lista");
        Set<String> seen = new HashSet<>();
        for (JsonNode id : ids) {
            try { UUID.fromString(id.asText()); } catch (IllegalArgumentException x) { throw new BadRequestException("evidenceId inválido"); }
            if (!seen.add(id.asText())) throw new BadRequestException("Foto repetida en la ejecución");
        }
    }
}
