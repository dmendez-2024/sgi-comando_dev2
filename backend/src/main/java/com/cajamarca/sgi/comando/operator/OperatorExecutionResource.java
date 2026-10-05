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

    /** quality y match: puntajes de VISINT tal como los devuelve (informativos; el veredicto es status/result). null mientras no hay respuesta. */
    public record Validation(String status, String result, String reasonCode, Instant reviewedAt, Quality quality, Match match) {}
    public record Quality(Boolean valid, Double score) {}
    public record Match(Boolean compatible, Double score) {}
    /** outcome: NOT_REQUIRED | PENDING | VALIDATED | NOT_VALIDATED | TECHNICAL_ERROR. station: foto del puesto del relevo (station_0..2), null en las demás tareas. */
    public record ExecutionResult(UUID eventId, String targetType, UUID targetId, UUID patrolRunId, UUID groupId, UUID checkpointId, int captureNo, Instant executedAt, Instant receivedAt,
                                  Validation validation, String outcome, String message, boolean canRetake, String station) {}

    @GET @Path("/executions/{eventId}")
    public ExecutionResult one(@PathParam("eventId") UUID eventId) {
        ctx.employee();
        TaskExecution x = TaskExecution.find("id=?1 and instanceCountryId=?2 and username=?3", eventId, tenant.instanceCountryId(), ctx.username()).firstResult();
        if (x == null) throw new NotFoundException("Ejecución no encontrada");
        return result(x);
    }

    @GET @Path("/executions")
    public List<ExecutionResult> byRound(@QueryParam("patrolRunId") UUID patrolRunId, @QueryParam("groupId") UUID groupId) {
        ctx.employee();
        if (patrolRunId == null && groupId == null) throw new BadRequestException("patrolRunId o groupId es obligatorio");
        String by = patrolRunId != null ? "patrolExecutionId" : "groupId";
        return TaskExecution.<TaskExecution>find("instanceCountryId=?1 and " + by + "=?2 and username=?3 order by executedAt, captureNo",
            tenant.instanceCountryId(), patrolRunId != null ? patrolRunId : groupId, ctx.username()).list().stream().map(this::result)
            .sorted(Comparator.comparing((ExecutionResult r) -> r.station() == null ? "" : r.station())).toList();
    }

    ExecutionResult result(TaskExecution x) {
        VisualReview r = VisualReview.find("taskExecutionId", x.id).firstResult();
        String outcome = r == null ? "NOT_REQUIRED" : switch (r.status) {
            case "PASSED" -> "VALIDATED";
            case "FAILED" -> "NOT_VALIDATED";
            case "ERROR_FINAL" -> "TECHNICAL_ERROR";
            default -> "PENDING";
        };
        boolean latest = x.patrolExecutionId != null
            ? TaskExecution.count("instanceCountryId=?1 and patrolExecutionId=?2 and targetId=?3 and captureNo>?4", x.instanceCountryId, x.patrolExecutionId, x.targetId, x.captureNo) == 0
            : x.groupId == null || TaskExecution.count("instanceCountryId=?1 and groupId=?2 and targetId=?3 and captureNo>?4", x.instanceCountryId, x.groupId, x.targetId, x.captureNo) == 0;
        // El relevo se recibe una sola vez por asignación: sus fotos del puesto no se vuelven a tomar.
        boolean canRetake = "NOT_VALIDATED".equals(outcome) && latest && !ReliefStationReviews.TYPE.equals(x.executionType);
        Validation v = r == null ? new Validation("NOT_REQUESTED", null, null, null, null, null)
            : new Validation(r.status, r.result, r.reasonCode, r.reviewedAt,
                r.qualityValid == null && r.qualityScore == null ? null : new Quality(r.qualityValid, r.qualityScore),
                r.matchCompatible == null && r.matchScore == null ? null : new Match(r.matchCompatible, r.matchScore));
        return new ExecutionResult(x.id, x.targetType, x.targetId, x.patrolExecutionId, x.groupId, x.targetId, x.captureNo, x.executedAt, x.receivedAt, v, outcome, message(outcome, r, canRetake, "PATROL_CHECKPOINT".equals(x.targetType)), canRetake,
            ReliefStationReviews.TYPE.equals(x.executionType) ? ReliefStationReviews.purpose(x) : null);
    }

    /** Texto para mostrar al agente. Una falla técnica de VISINT no se le atribuye: el Hito queda registrado. */
    static String message(String outcome, VisualReview r, boolean canRetake, boolean patrol) {
        return switch (outcome) {
            case "NOT_REQUIRED" -> patrol ? "Hito registrado." : "Foto registrada.";
            case "PENDING" -> "Validando la foto con VISINT…";
            case "VALIDATED" -> patrol ? "Foto validada. Hito cumplido." : "Foto validada.";
            case "TECHNICAL_ERROR" -> "No se pudo validar la foto por un problema técnico. " + (patrol ? "El Hito queda registrado." : "La foto queda registrada.");
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
