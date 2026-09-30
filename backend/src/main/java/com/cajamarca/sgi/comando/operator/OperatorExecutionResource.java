package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.execution.TaskExecution;
import com.cajamarca.sgi.comando.visint.VisualReview;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.time.Instant;
import java.util.*;

/**
 * Fase 3 · resultado para el agente: estado de la validación VISINT de cada Hito que registró y si puede tomar una nueva foto.
 * La app consulta cada pocos segundos mientras el resultado está PENDING.
 */
@Path("/api/v1/operator") @Authenticated @Produces(MediaType.APPLICATION_JSON)
public class OperatorExecutionResource {
    @Inject OperatorContext ctx;
    @Inject TenantContext tenant;

    @org.jboss.resteasy.reactive.server.ServerExceptionMapper
    public Response mapError(WebApplicationException e) { return OperatorErrors.withMessage(e); }

    public record Validation(String status, String result, String reasonCode, Instant reviewedAt) {}
    /** outcome: NOT_REQUIRED | PENDING | VALIDATED | NOT_VALIDATED | TECHNICAL_ERROR. */
    public record ExecutionResult(UUID eventId, UUID patrolRunId, UUID checkpointId, int captureNo, Instant executedAt, Instant receivedAt,
                                  Validation validation, String outcome, String message, boolean canRetake) {}

    @GET @Path("/executions/{eventId}")
    public ExecutionResult one(@PathParam("eventId") UUID eventId) {
        ctx.employee();
        TaskExecution x = TaskExecution.find("id=?1 and instanceCountryId=?2 and username=?3", eventId, tenant.instanceCountryId(), ctx.username()).firstResult();
        if (x == null) throw new NotFoundException("Ejecución no encontrada");
        return result(x);
    }

    @GET @Path("/executions")
    public List<ExecutionResult> byRound(@QueryParam("patrolRunId") UUID patrolRunId) {
        ctx.employee();
        if (patrolRunId == null) throw new BadRequestException("patrolRunId es obligatorio");
        return TaskExecution.<TaskExecution>find("instanceCountryId=?1 and patrolExecutionId=?2 and username=?3 order by executedAt, captureNo",
            tenant.instanceCountryId(), patrolRunId, ctx.username()).list().stream().map(this::result).toList();
    }

    ExecutionResult result(TaskExecution x) {
        VisualReview r = VisualReview.find("taskExecutionId", x.id).firstResult();
        String outcome = r == null ? "NOT_REQUIRED" : switch (r.status) {
            case "PASSED" -> "VALIDATED";
            case "FAILED" -> "NOT_VALIDATED";
            case "ERROR_FINAL" -> "TECHNICAL_ERROR";
            default -> "PENDING";
        };
        boolean latest = x.patrolExecutionId == null || TaskExecution.count("instanceCountryId=?1 and patrolExecutionId=?2 and targetId=?3 and captureNo>?4",
            x.instanceCountryId, x.patrolExecutionId, x.targetId, x.captureNo) == 0;
        boolean canRetake = "NOT_VALIDATED".equals(outcome) && latest;
        Validation v = r == null ? new Validation("NOT_REQUESTED", null, null, null) : new Validation(r.status, r.result, r.reasonCode, r.reviewedAt);
        return new ExecutionResult(x.id, x.patrolExecutionId, x.targetId, x.captureNo, x.executedAt, x.receivedAt, v, outcome, message(outcome, r, canRetake), canRetake);
    }

    /** Texto para mostrar al agente. Una falla técnica de VISINT no se le atribuye: el Hito queda registrado. */
    static String message(String outcome, VisualReview r, boolean canRetake) {
        return switch (outcome) {
            case "NOT_REQUIRED" -> "Hito registrado.";
            case "PENDING" -> "Validando la foto con VISINT…";
            case "VALIDATED" -> "Foto validada. Hito cumplido.";
            case "TECHNICAL_ERROR" -> "No se pudo validar la foto por un problema técnico. El Hito queda registrado.";
            default -> "Evidencia no validada: " + reason(r) + (canRetake ? ". Tome una nueva foto." : ".");
        };
    }

    static String reason(VisualReview r) {
        String code = ((r.reasonCode == null ? "" : r.reasonCode) + " " + (r.findings == null ? "" : r.findings)).toUpperCase(Locale.ROOT);
        if (code.contains("QUALITY")) return "la foto no es clara";
        if (code.contains("INVALID_IMAGE")) return "la imagen no se pudo procesar";
        return "no coincide con el lugar esperado";
    }
}
