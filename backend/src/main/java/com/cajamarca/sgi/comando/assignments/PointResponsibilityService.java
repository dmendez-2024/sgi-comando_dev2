package com.cajamarca.sgi.comando.assignments;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.operations.PostEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class PointResponsibilityService {
    @Inject TenantContext tenant;
    @Inject EntityManager em;
    @Inject ObjectMapper mapper;
    @Inject AssignmentRolePolicy roles;
    private final SecureRandom random = new SecureRandom();

    public record ResponsibilityDto(UUID pointId, Instant startsAt, Instant endsAt, UUID employeeId,
                                    String employeeName, Instant selectedAt, String selectedBy) {}

    public List<ResponsibilityDto> list(UUID planId) {
        List<PointResponsibilityEntity> rows = PointResponsibilityEntity.list(
            "instanceCountryId=?1 and assignmentPlanId=?2 order by startsAt,pointId", tenant.instanceCountryId(), planId);
        Set<UUID> ids = rows.stream().map(r -> r.employeeId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> names = ids.isEmpty() ? Map.of() : EmployeeOperationalSnapshot.<EmployeeOperationalSnapshot>list(
            "instanceCountryId=?1 and employeeId in ?2", tenant.instanceCountryId(), ids).stream()
            .collect(Collectors.toMap(e -> e.employeeId, e -> e.fullName));
        return rows.stream().map(r -> new ResponsibilityDto(r.pointId, r.startsAt, r.endsAt, r.employeeId,
            r.employeeId == null ? "Sin agentes elegibles" : names.getOrDefault(r.employeeId, r.employeeId.toString()),
            r.selectedAt, r.selectedBy)).toList();
    }

    /** Called only in a write transaction. Reads never trigger a draw. */
    public void synchronize(AssignmentPlanEntity plan, List<ShiftOccurrenceEntity> shifts, String actor) {
        List<PointEntity> points = PointEntity.list(
            "instanceCountryId=?1 and companyId=?2 and status='ACTIVE' order by id", tenant.instanceCountryId(), plan.companyId);
        // Serialize draws across plans for the same Point, including concurrent publications.
        for (PointEntity point : points) {
            em.lock(point, LockModeType.PESSIMISTIC_WRITE);
            synchronizePoint(plan, point, shifts, actor);
        }
    }

    public void synchronizeAfterRelease(AssignmentPlanEntity plan, String actor) {
        if (!"PUBLISHED".equals(plan.status)) return;
        List<ShiftOccurrenceEntity> shifts = ShiftOccurrenceEntity.list(
            "select distinct s from ShiftOccurrenceEntity s, OperationalAssignmentEntity a " +
            "where s.instanceCountryId=?1 and a.instanceCountryId=?1 and a.assignmentPlanId=?2 and a.shiftOccurrenceId=s.id",
            tenant.instanceCountryId(), plan.id);
        synchronize(plan, shifts, actor);
    }

    private void synchronizePoint(AssignmentPlanEntity plan, PointEntity point, List<ShiftOccurrenceEntity> shifts, String actor) {
        Set<UUID> posts = PostEntity.<PostEntity>list("instanceCountryId=?1 and pointId=?2", tenant.instanceCountryId(), point.id)
            .stream().map(p -> p.id).collect(Collectors.toSet());
        List<ShiftOccurrenceEntity> pointShifts = shifts.stream().filter(s -> posts.contains(s.postId)).toList();
        if (pointShifts.isEmpty()) return;
        Set<UUID> shiftIds = pointShifts.stream().map(s -> s.id).collect(Collectors.toSet());
        Map<UUID, UUID> assigned = new HashMap<>();
        OperationalAssignmentEntity.<OperationalAssignmentEntity>list(
            "instanceCountryId=?1 and assignmentPlanId=?2 and shiftOccurrenceId in ?3 and status<>'REMOVED'",
            tenant.instanceCountryId(), plan.id, shiftIds).forEach(a -> assigned.put(a.shiftOccurrenceId, a.effectiveEmployeeId()));
        List<EmployeeOperationalSnapshot> employees = EmployeeOperationalSnapshot.list(
            "instanceCountryId=?1 and companyId=?2 and employmentStatus='ACTIVE'", tenant.instanceCountryId(), plan.companyId);
        Set<UUID> eligibleEmployees = employees.stream()
            .filter(e -> roles.assignableRoleCodes().contains(e.roleCode) || roles.isAssignable(e.roleCode))
            .map(e -> e.employeeId).collect(Collectors.toSet());
        List<PointResponsibilityEntity> existing = PointResponsibilityEntity.list(
            "instanceCountryId=?1 and assignmentPlanId=?2 and pointId=?3", tenant.instanceCountryId(), plan.id, point.id);
        List<Instant> boundaries = java.util.stream.Stream.concat(
            pointShifts.stream().flatMap(s -> java.util.stream.Stream.of(s.startsAt, s.endsAt)),
            existing.stream().flatMap(r -> java.util.stream.Stream.of(r.startsAt, r.endsAt)))
            .distinct().sorted().toList();
        List<EmployeeUnavailabilitySnapshot> unavailable = EmployeeUnavailabilitySnapshot.list(
            "instanceCountryId=?1 and sourceStatus='ACTIVE' and startsAt<?2 and endsAt>?3",
            tenant.instanceCountryId(), boundaries.getLast(), boundaries.getFirst());
        Map<UUID, Long> counts = new HashMap<>();
        // Events retain all designations, even when a subsequent reassignment replaces a winner.
        List<AssignmentEventEntity> history = AssignmentEventEntity.list(
            "instanceCountryId=?1 and eventType='POINT_RESPONSIBLE_SELECTED' and reason=?2",
            tenant.instanceCountryId(), point.id.toString());
        history.stream().map(e -> e.newEmployeeId).filter(Objects::nonNull).forEach(id -> counts.merge(id, 1L, Long::sum));
        Instant now = Instant.now();
        for (int i = 0; i < boundaries.size() - 1; i++) {
            Instant start = boundaries.get(i), end = boundaries.get(i + 1);
            if (pointShifts.stream().noneMatch(s -> !s.startsAt.isAfter(start) && !s.endsAt.isBefore(end))) continue;
            PointResponsibilityEntity row = existing.stream().filter(r -> r.startsAt.equals(start) && r.endsAt.equals(end)).findFirst().orElse(null);
            // Never rewrite completed operational history.
            if (row != null && !end.isAfter(now)) continue;
            Set<UUID> candidates = pointShifts.stream().filter(s -> !s.startsAt.isAfter(start) && !s.endsAt.isBefore(end))
                .map(s -> assigned.get(s.id)).filter(Objects::nonNull).filter(eligibleEmployees::contains)
                .filter(id -> unavailable.stream().noneMatch(u -> id.equals(u.employeeId) && u.startsAt.isBefore(end) && u.endsAt.isAfter(start)))
                .collect(Collectors.toSet());
            if (row != null && (candidates.contains(row.employeeId) || (row.employeeId == null && candidates.isEmpty()))) continue;
            UUID before = row == null ? null : row.employeeId;
            UUID winner = PointResponsibilityPolicy.choose(candidates, counts, random);
            if (row == null) {
                row = new PointResponsibilityEntity(); row.instanceCountryId = tenant.instanceCountryId();
                row.assignmentPlanId = plan.id; row.pointId = point.id; row.startsAt = start; row.endsAt = end;
            }
            row.employeeId = winner; row.selectedAt = now; row.selectedBy = actor; row.persist();
            AssignmentEventEntity event = new AssignmentEventEntity();
            event.instanceCountryId = tenant.instanceCountryId(); event.assignmentPlanId = plan.id;
            event.eventType = winner == null ? "POINT_RESPONSIBLE_UNAVAILABLE" : "POINT_RESPONSIBLE_SELECTED";
            event.originalEmployeeId = before; event.newEmployeeId = winner; event.actorUsername = actor;
            event.reason = point.id.toString(); event.occurredAt = now;
            try {
                event.payloadJson = mapper.writeValueAsString(Map.of("pointId", point.id, "startsAt", start, "endsAt", end,
                    "eligibleEmployeeIds", candidates, "priorCounts", counts, "rule", "LEAST_DESIGNATIONS_RANDOM_TIE"));
            } catch (JsonProcessingException e) { throw new IllegalStateException("No se pudo auditar el sorteo", e); }
            event.persist();
            if (winner != null) counts.merge(winner, 1L, Long::sum);
        }
    }
}
