package com.cajamarca.sgi.comando.rrhh;

import com.cajamarca.sgi.comando.assignments.AssignmentPlanEntity;
import com.cajamarca.sgi.comando.assignments.EmployeeOperationalSnapshot;
import com.cajamarca.sgi.comando.assignments.OperationalAssignmentEntity;
import com.cajamarca.sgi.comando.assignments.PostPlanningCycleSnapshot;
import com.cajamarca.sgi.comando.assignments.ShiftOccurrenceEntity;
import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.operations.ClientEntity;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.operations.PostEntity;
import com.cajamarca.sgi.comando.operations.ServiceEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.InternalServerErrorException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class RrhhPayrollShiftService {
    @Inject TenantContext tenant;

    public List<RrhhPayrollShiftResource.PayrollShift> findPublishedShifts(
        RrhhPayrollShiftResource.PayrollShiftQuery request
    ) {
        RrhhPayrollShiftResource.validateContract(request);
        UUID tenantId = tenant.instanceCountryId();

        EmployeeOperationalSnapshot employee = EmployeeOperationalSnapshot.find(
            "instanceCountryId=?1 and personaId=?2", tenantId, request.personaId()
        ).firstResult();
        if (employee == null) return List.of();

        List<OperationalAssignmentEntity> assignments = OperationalAssignmentEntity.list(
            "instanceCountryId=?1 and status<>'REMOVED' and (employeeId=?2 or actualEmployeeId=?2)",
            tenantId,
            employee.employeeId
        );
        assignments = assignments.stream()
            .filter(row -> employee.employeeId.equals(row.effectiveEmployeeId()))
            .toList();
        if (assignments.isEmpty()) return List.of();

        Map<UUID, AssignmentPlanEntity> plans = mapById(
            AssignmentPlanEntity.list(
                "instanceCountryId=?1 and id in ?2 and status in ('PUBLISHED','CLOSED')",
                tenantId,
                assignments.stream().map(row -> row.assignmentPlanId).collect(Collectors.toSet())
            )
        );
        assignments = assignments.stream()
            .filter(row -> plans.containsKey(row.assignmentPlanId))
            .toList();
        if (assignments.isEmpty()) return List.of();

        Map<UUID, ShiftOccurrenceEntity> shifts = mapById(
            ShiftOccurrenceEntity.list(
                "instanceCountryId=?1 and id in ?2 and endsAt>=?3 and endsAt<?4",
                tenantId,
                assignments.stream().map(row -> row.shiftOccurrenceId).collect(Collectors.toSet()),
                request.startsAt(),
                request.endsAt()
            )
        );
        assignments = assignments.stream()
            .filter(row -> shifts.containsKey(row.shiftOccurrenceId))
            .toList();
        if (assignments.isEmpty()) return List.of();

        Map<UUID, PostEntity> posts = mapById(PostEntity.list(
            "instanceCountryId=?1 and id in ?2",
            tenantId,
            shifts.values().stream().map(row -> row.postId).collect(Collectors.toSet())
        ));
        Map<UUID, PointEntity> points = mapById(PointEntity.list(
            "instanceCountryId=?1 and id in ?2",
            tenantId,
            posts.values().stream().map(row -> row.pointId).collect(Collectors.toSet())
        ));
        Map<UUID, ServiceEntity> services = mapById(ServiceEntity.list(
            "instanceCountryId=?1 and id in ?2",
            tenantId,
            points.values().stream().map(row -> row.serviceId).collect(Collectors.toSet())
        ));
        Map<UUID, ClientEntity> clients = mapById(ClientEntity.list(
            "instanceCountryId=?1 and id in ?2",
            tenantId,
            services.values().stream().map(row -> row.clientId).collect(Collectors.toSet())
        ));
        Map<UUID, PostPlanningCycleSnapshot> cyclesByPost = PostPlanningCycleSnapshot
            .<PostPlanningCycleSnapshot>list(
                "instanceCountryId=?1 and postId in ?2",
                tenantId,
                posts.keySet()
            )
            .stream()
            .collect(Collectors.toMap(row -> row.postId, Function.identity()));

        List<RrhhPayrollShiftResource.PayrollShift> result = new ArrayList<>();
        for (OperationalAssignmentEntity assignment : assignments) {
            ShiftOccurrenceEntity shift = shifts.get(assignment.shiftOccurrenceId);
            PostEntity post = shift == null ? null : posts.get(shift.postId);
            PointEntity point = post == null ? null : points.get(post.pointId);
            ServiceEntity service = point == null ? null : services.get(point.serviceId);
            ClientEntity client = service == null ? null : clients.get(service.clientId);
            PostPlanningCycleSnapshot cycle = post == null ? null : cyclesByPost.get(post.id);
            if (shift == null || post == null || point == null || service == null || client == null || cycle == null) {
                throw new InternalServerErrorException(
                    "La asignación publicada no tiene completa su relación puesto/punto/servicio/cliente/rotación."
                );
            }
            if (blank(client.taxIdentifier)) {
                throw new InternalServerErrorException(
                    "El cliente " + client.name + " no tiene tax_identifier para la integración de nómina."
                );
            }
            result.add(new RrhhPayrollShiftResource.PayrollShift(
                assignment.id,
                shift.id,
                employee.personaId,
                client.taxIdentifier.trim(),
                client.name,
                post.tier,
                cycle.rotationCode,
                shift.startsAt,
                shift.endsAt
            ));
        }
        result.sort(Comparator.comparing(RrhhPayrollShiftResource.PayrollShift::endsAt)
            .thenComparing(RrhhPayrollShiftResource.PayrollShift::assignmentId));
        return result;
    }

    private static <T extends com.cajamarca.sgi.comando.common.BaseEntity> Map<UUID, T> mapById(List<T> rows) {
        return rows.stream().collect(Collectors.toMap(row -> row.id, Function.identity()));
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
