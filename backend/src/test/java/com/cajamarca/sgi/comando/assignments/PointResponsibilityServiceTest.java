package com.cajamarca.sgi.comando.assignments;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.operations.PostEntity;
import com.cajamarca.sgi.comando.operations.ClientEntity;
import com.cajamarca.sgi.comando.operations.ServiceEntity;
import com.cajamarca.sgi.comando.companies.Company;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PointResponsibilityServiceTest {
    @Inject PointResponsibilityService service;
    @Inject TenantContext tenant;
    @Inject EntityManager em;
    @Inject AssignmentReleaseService releases;

    @Test @TestTransaction
    void persistsBalancedDrawsPreservesThemOnRetryAndReplacesUnavailableWinner() {
        PointEntity point = point();
        PostEntity post = new PostEntity(); post.instanceCountryId = tenant.instanceCountryId(); post.pointId = point.id;
        post.code = "RP-" + UUID.randomUUID(); post.name = "Acceso"; post.format = "12H";
        post.fhe = BigDecimal.ONE; post.tier = "I"; post.commercialStatus = "ACTIVE"; post.configStatus = "READY"; post.persist();
        AssignmentPlanEntity plan = new AssignmentPlanEntity();
        plan.instanceCountryId = tenant.instanceCountryId(); plan.companyId = point.companyId;
        plan.weekStart = LocalDate.of(2098, 1, 6); plan.status = "PUBLISHED"; plan.persist();
        EmployeeOperationalSnapshot first = employee(point.companyId, "Primero", BigDecimal.ONE);
        EmployeeOperationalSnapshot second = employee(point.companyId, "Segundo", BigDecimal.TEN);
        // Different scores do not affect eligibility or balanced rotation.
        Instant start = Instant.parse("2098-01-06T23:00:00Z");
        List<ShiftOccurrenceEntity> shifts = new ArrayList<>();
        for (int day = 0; day < 2; day++) {
            shifts.add(shift(plan, post.id, first.employeeId, start.plusSeconds(day * 86400L)));
            // A second operational Post with the same timetable at the Point.
            PostEntity other = new PostEntity(); other.instanceCountryId = tenant.instanceCountryId(); other.pointId = point.id;
            other.code = "RP-" + UUID.randomUUID(); other.name = "Prueba responsable"; other.format = post.format;
            other.fhe = post.fhe; other.tier = post.tier; other.commercialStatus = "ACTIVE"; other.configStatus = post.configStatus; other.persist();
            shifts.add(shift(plan, other.id, second.employeeId, start.plusSeconds(day * 86400L)));
        }
        em.flush();
        service.synchronize(plan, shifts, "coord"); em.flush();
        var draws = service.list(plan.id);
        assertEquals(2, draws.size());
        assertEquals(Set.of(first.employeeId, second.employeeId), new HashSet<>(draws.stream().map(PointResponsibilityService.ResponsibilityDto::employeeId).toList()));
        service.synchronize(plan, shifts, "coord"); em.flush();
        assertEquals(draws, service.list(plan.id), "Actualizar o repetir publicación no debe volver a sortear");
        assertEquals(2, events(plan));

        var winner = draws.getFirst();
        EmployeeUnavailabilitySnapshot absence = new EmployeeUnavailabilitySnapshot();
        absence.instanceCountryId = tenant.instanceCountryId(); absence.employeeId = winner.employeeId();
        absence.type = "VACATION"; absence.sourceStatus = "ACTIVE"; absence.startsAt = winner.startsAt(); absence.endsAt = winner.endsAt();
        absence.updatedFromSourceAt = Instant.now(); absence.persist();
        em.flush(); service.synchronize(plan, shifts, "coord"); em.flush();
        var replaced = service.list(plan.id);
        assertNotEquals(winner.employeeId(), replaced.getFirst().employeeId());
        assertEquals(draws.getLast(), replaced.getLast(), "Un relevo no debe reordenar otras designaciones válidas");
        assertEquals(3, events(plan));

        AssignmentPlanEntity nextWeek = new AssignmentPlanEntity(); nextWeek.instanceCountryId = tenant.instanceCountryId();
        nextWeek.companyId = point.companyId; nextWeek.weekStart = plan.weekStart.plusDays(7); nextWeek.status = "PUBLISHED"; nextWeek.persist();
        List<ShiftOccurrenceEntity> nextShifts = List.of(
            shift(nextWeek, shifts.get(0).postId, first.employeeId, start.plusSeconds(7 * 86400L)),
            shift(nextWeek, shifts.get(1).postId, second.employeeId, start.plusSeconds(7 * 86400L)));
        em.flush(); service.synchronize(nextWeek, nextShifts, "coord"); em.flush();
        assertEquals(winner.employeeId(), service.list(nextWeek.id).getFirst().employeeId(), "El historial debe cruzar semanas");

        first.employmentStatus = "INACTIVE"; second.employmentStatus = "INACTIVE";
        em.flush();
        releases.releaseForEmployeeInactivation(tenant.instanceCountryId(), first.employeeId, Instant.now(), "test-first");
        releases.releaseForEmployeeInactivation(tenant.instanceCountryId(), second.employeeId, Instant.now(), "test-second");
        em.flush();
        assertTrue(service.list(plan.id).stream().allMatch(r -> r.employeeId() == null));
        assertTrue(service.list(nextWeek.id).stream().allMatch(r -> r.employeeId() == null));
        assertEquals(3, events(plan), "Sin elegibles no hay una nueva participación");
    }

    private long events(AssignmentPlanEntity plan) {
        return AssignmentEventEntity.count("instanceCountryId=?1 and assignmentPlanId=?2 and eventType='POINT_RESPONSIBLE_SELECTED'", tenant.instanceCountryId(), plan.id);
    }
    private PointEntity point() {
        String code = UUID.randomUUID().toString().substring(0, 20);
        Company company = new Company(); company.instanceCountryId = tenant.instanceCountryId(); company.code = code;
        company.name = "Rotación " + code; company.status = "ACTIVE"; company.sourceSystem = "TEST";
        company.companyType = "SECURITY"; company.versionNumber = 1; company.persist();
        ClientEntity client = new ClientEntity(); client.instanceCountryId = tenant.instanceCountryId(); client.code = code;
        client.name = "Cliente prueba"; client.commercialStatus = "ACTIVE"; client.sourceSystem = "TEST"; client.persist();
        ServiceEntity service = new ServiceEntity(); service.instanceCountryId = tenant.instanceCountryId(); service.code = code;
        service.name = "Servicio prueba"; service.clientId = client.id; service.clientName = client.name;
        service.commercialStatus = "ACTIVE"; service.configStatus = "READY"; service.sourceSystem = "TEST"; service.persist();
        PointEntity point = new PointEntity(); point.instanceCountryId = tenant.instanceCountryId(); point.code = code;
        point.name = "Punto prueba"; point.serviceId = service.id; point.companyId = company.id; point.clientName = client.name;
        point.province = "Guayas"; point.city = "Guayaquil"; point.status = "ACTIVE"; point.operationalAssignmentStatus = "ASSIGNED";
        point.persist(); return point;
    }
    private EmployeeOperationalSnapshot employee(UUID company, String name, BigDecimal score) {
        EmployeeOperationalSnapshot e = new EmployeeOperationalSnapshot(); e.instanceCountryId = tenant.instanceCountryId();
        e.employeeId = UUID.randomUUID(); e.companyId = company; e.fullName = name; e.roleCode = "AGENTE_SEGURIDAD";
        e.employmentStatus = "ACTIVE"; e.idScore = score; e.updatedFromSourceAt = Instant.now(); e.persist(); return e;
    }
    private ShiftOccurrenceEntity shift(AssignmentPlanEntity plan, UUID post, UUID employee, Instant start) {
        ShiftOccurrenceEntity s = new ShiftOccurrenceEntity(); s.instanceCountryId = tenant.instanceCountryId(); s.postId = post;
        s.startsAt = start; s.endsAt = start.plusSeconds(43200); s.required = true; s.shiftCode = "NIGHT";
        s.shiftName = "Nocturno"; s.localDate = LocalDate.ofInstant(start, ZoneId.of("America/Guayaquil")); s.persist();
        OperationalAssignmentEntity a = new OperationalAssignmentEntity(); a.instanceCountryId = tenant.instanceCountryId();
        a.assignmentPlanId = plan.id; a.shiftOccurrenceId = s.id; a.employeeId = employee; a.status = "PUBLISHED"; a.persist(); return s;
    }
}
