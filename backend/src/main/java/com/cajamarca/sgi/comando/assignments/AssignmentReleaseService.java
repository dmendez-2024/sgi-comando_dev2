package com.cajamarca.sgi.comando.assignments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.InternalServerErrorException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class AssignmentReleaseService {
    private static final String REMOVED = "REMOVED";
    private static final String EMPLOYEE_INACTIVATION_REASON = "Empleado inactivado por SIC:RRHH";

    @Inject ObjectMapper mapper;

    public int releaseForCompanyTransfer(
        UUID tenantId,
        UUID employeeId,
        UUID companyId,
        UUID transferId,
        String actorUsername
    ) {
        return releaseFutureAssignments(
            tenantId,
            employeeId,
            companyId,
            Instant.now(),
            "ASSIGNMENT_RELEASED_FOR_COMPANY_TRANSFER",
            actorUsername,
            "Transferencia de Compañía pendiente",
            Map.of("transferId", transferId),
            false
        );
    }

    public int releaseForEmployeeInactivation(
        UUID tenantId,
        UUID employeeId,
        Instant sourceUpdatedAt,
        String idempotencyKey
    ) {
        return releaseFutureAssignments(
            tenantId,
            employeeId,
            null,
            Instant.now(),
            "ASSIGNMENT_RELEASED_FOR_EMPLOYEE_INACTIVATION",
            "SIC_RRHH",
            EMPLOYEE_INACTIVATION_REASON,
            Map.of(
                "sourceUpdatedAt", sourceUpdatedAt,
                "idempotencyKey", idempotencyKey
            ),
            true
        );
    }

    private int releaseFutureAssignments(
        UUID tenantId,
        UUID employeeId,
        UUID companyId,
        Instant cutoff,
        String eventType,
        String actorUsername,
        String reason,
        Map<String, ?> payload,
        boolean updateReassignmentReason
    ) {
        List<AssignmentPlanEntity> plans = companyId == null
            ? AssignmentPlanEntity.list("instanceCountryId=?1", tenantId)
            : AssignmentPlanEntity.list("instanceCountryId=?1 and companyId=?2", tenantId, companyId);
        if (plans.isEmpty()) return 0;

        Set<UUID> planIds = plans.stream().map(plan -> plan.id).collect(Collectors.toSet());
        List<OperationalAssignmentEntity> assignments = OperationalAssignmentEntity.list(
            "instanceCountryId=?1 and assignmentPlanId in ?2 and status<>'REMOVED' "
                + "and (employeeId=?3 or actualEmployeeId=?3)",
            tenantId,
            planIds,
            employeeId
        );
        if (assignments.isEmpty()) return 0;

        Set<UUID> shiftIds = assignments.stream()
            .map(assignment -> assignment.shiftOccurrenceId)
            .collect(Collectors.toSet());
        Map<UUID, ShiftOccurrenceEntity> shifts = ShiftOccurrenceEntity.<ShiftOccurrenceEntity>list(
            "instanceCountryId=?1 and id in ?2", tenantId, shiftIds
        ).stream().collect(Collectors.toMap(shift -> shift.id, Function.identity()));
        Map<UUID, AssignmentPlanEntity> plansById = plans.stream()
            .collect(Collectors.toMap(plan -> plan.id, Function.identity()));

        int released = 0;
        for (OperationalAssignmentEntity assignment : assignments) {
            ShiftOccurrenceEntity shift = shifts.get(assignment.shiftOccurrenceId);
            AssignmentPlanEntity plan = plansById.get(assignment.assignmentPlanId);
            if (shift == null || plan == null || !shift.startsAt.isAfter(cutoff)) continue;

            UUID releasedEmployeeId = assignment.effectiveEmployeeId();
            assignment.status = REMOVED;
            if (updateReassignmentReason) assignment.reassignmentReason = reason;
            recordEvent(
                tenantId,
                plan,
                assignment,
                shift,
                eventType,
                releasedEmployeeId,
                actorUsername,
                reason,
                payload
            );
            released++;
        }
        return released;
    }

    private void recordEvent(
        UUID tenantId,
        AssignmentPlanEntity plan,
        OperationalAssignmentEntity assignment,
        ShiftOccurrenceEntity shift,
        String eventType,
        UUID originalEmployeeId,
        String actorUsername,
        String reason,
        Map<String, ?> payload
    ) {
        AssignmentEventEntity event = new AssignmentEventEntity();
        event.instanceCountryId = tenantId;
        event.assignmentPlanId = plan.id;
        event.assignmentId = assignment.id;
        event.shiftOccurrenceId = shift.id;
        event.eventType = eventType;
        event.originalEmployeeId = originalEmployeeId;
        event.newEmployeeId = null;
        event.actorUsername = actorUsername;
        event.reason = reason;
        event.payloadJson = json(payload);
        event.occurredAt = Instant.now();
        event.persist();
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new InternalServerErrorException("No se pudo serializar auditoría", e);
        }
    }
}
