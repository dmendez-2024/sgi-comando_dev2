package com.cajamarca.sgi.comando.assignments;

import com.cajamarca.sgi.comando.outbox.OutboxEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.*;
import java.util.List;

@ApplicationScoped
public class AssignmentPlanLifecycle {
    private static final ZoneId OPERATING_ZONE = ZoneId.of("America/Guayaquil");

    @Scheduled(every="10m", delayed="30s")
    @Transactional
    void closeExpiredPublishedPlans() {
        LocalDate today = LocalDate.now(OPERATING_ZONE);
        List<AssignmentPlanEntity> plans = AssignmentPlanEntity.list("status='PUBLISHED' and weekStart<?1", today.minusDays(6));
        Instant now = Instant.now();
        for (AssignmentPlanEntity plan : plans) {
            Instant end = plan.weekStart.plusDays(7).atStartOfDay(OPERATING_ZONE).toInstant();
            if (now.isBefore(end) || !"PUBLISHED".equals(plan.status)) continue;
            plan.status = "CLOSED";
            plan.closedAt = now;

            AssignmentEventEntity event = new AssignmentEventEntity();
            event.instanceCountryId = plan.instanceCountryId;
            event.assignmentPlanId = plan.id;
            event.eventType = "ASSIGNMENT_PLAN_CLOSED";
            event.actorUsername = "SYSTEM";
            event.reason = "Cierre automático al finalizar la semana operativa";
            event.payloadJson = "{\"reason\":\"WEEK_ENDED\"}";
            event.occurredAt = now;
            event.persist();

            OutboxEvent.of(plan.instanceCountryId, "ASSIGNMENT_PLAN", plan.id, "ASSIGNMENT_PLAN_CLOSED",
                    "{\"planId\":\"" + plan.id + "\",\"reason\":\"WEEK_ENDED\"}").persist();
        }
    }
}
