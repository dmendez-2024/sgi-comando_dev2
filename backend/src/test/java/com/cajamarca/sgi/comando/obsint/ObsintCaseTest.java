package com.cajamarca.sgi.comando.obsint;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Reglas del caso OBSINT sin base de datos: orden de tareas, tiempo máximo y justificación. */
class ObsintCaseTest {
    static final Instant T0 = Instant.parse("2026-10-26T15:00:00Z");

    static ObsintCase caseWith(int tasks) {
        List<ObsintConsigna> consignas = new java.util.ArrayList<>();
        for (int i = 1; i <= tasks; i++) {
            ObsintConsigna c = new ObsintConsigna();
            c.code = "OBS-00" + i; c.instruction = "Consigna " + i; c.timeLimitSeconds = 60;
            consignas.add(c);
        }
        ObsintCase c = ObsintCase.open(UUID.randomUUID(), "CAJ-OBS-001", "coord", consignas, T0);
        c.tasks.forEach(t -> t.id = UUID.randomUUID());
        return c;
    }

    @Test void copiesConsignasAsOrderedPendingTasks() {
        ObsintCase c = caseWith(3);
        assertEquals(List.of(1, 2, 3), c.tasks.stream().map(t -> t.position).toList());
        assertEquals("OBS-002", c.tasks.get(1).consignaCode);
        assertTrue(c.tasks.stream().allMatch(t -> t.status == ObsintCaseTask.Status.PENDING));
        assertSame(c.tasks.get(0), c.currentTask().orElseThrow());
    }

    @Test void scheduleCoversDayAndTimeRange() {
        ObsintConsigna c = new ObsintConsigna();
        c.schedule.add(new ObsintConsigna.Slot(1, java.time.LocalTime.of(8, 0), java.time.LocalTime.of(18, 0)));
        assertTrue(c.scheduledAt(1, java.time.LocalTime.of(8, 0)), "el inicio está incluido");
        assertTrue(c.scheduledAt(1, java.time.LocalTime.of(17, 59)));
        assertFalse(c.scheduledAt(1, java.time.LocalTime.of(18, 0)), "el fin está excluido");
        assertFalse(c.scheduledAt(2, java.time.LocalTime.of(10, 0)), "otro día");
    }

    @Test void withoutConsignasCannotOpen() {
        assertThrows(ObsintRuleException.class, () -> ObsintCase.open(UUID.randomUUID(), "X", "coord", List.of(), T0));
    }

    @Test void tasksMustBeResolvedInOrder() {
        ObsintCase c = caseWith(2);
        assertThrows(ObsintRuleException.class, () -> c.startTask(c.tasks.get(1), T0));
    }

    @Test void startingTwiceKeepsTheDeadline() {
        ObsintCase c = caseWith(1);
        ObsintCaseTask t = c.startTask(c.tasks.get(0), T0);
        c.startTask(t, T0.plusSeconds(30));
        assertEquals(T0.plusSeconds(60), t.deadline());
    }

    @Test void answerRequiresDecisionAndJustification() {
        ObsintCase c = caseWith(1);
        ObsintCaseTask t = c.startTask(c.tasks.get(0), T0);
        assertThrows(ObsintRuleException.class, () -> c.answerTask(t, null, "ok", T0));
        assertThrows(ObsintRuleException.class, () -> c.answerTask(t, ObsintCaseTask.Decision.YES, "   ", T0));
        assertThrows(ObsintRuleException.class, () -> c.answerTask(t, ObsintCaseTask.Decision.YES, "x".repeat(501), T0));
    }

    @Test void lateAnswerIsAcceptedButMarkedAndLastAnswerCompletesTheCase() {
        ObsintCase c = caseWith(2);
        ObsintCaseTask first = c.startTask(c.tasks.get(0), T0);
        c.answerTask(first, ObsintCaseTask.Decision.NO, " Hay una mochila ", T0.plusSeconds(20));
        assertEquals("Hay una mochila", first.justification);
        assertFalse(first.timedOut);
        assertEquals(20, first.secondsUsed);
        assertEquals(ObsintCase.Status.IN_PROGRESS, c.status);

        ObsintCaseTask second = c.startTask(c.tasks.get(1), T0.plusSeconds(20));
        c.answerTask(second, ObsintCaseTask.Decision.UNCERTAIN, "No se ve bien", T0.plusSeconds(90));
        assertTrue(second.timedOut);
        assertEquals(ObsintCase.Status.COMPLETED, c.status);
        assertEquals(T0.plusSeconds(90), c.completedAt);
        assertThrows(ObsintRuleException.class, () -> c.answerTask(second, ObsintCaseTask.Decision.YES, "otra", T0.plusSeconds(91)));
    }
}
