package com.cajamarca.sgi.comando.assignments;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssignmentCoverageCountTest {
    @Test
    void countsDistinctOperationalPointsWithUnassignedShifts() {
        UUID firstPost = UUID.randomUUID(), secondPost = UUID.randomUUID();
        UUID firstPoint = UUID.randomUUID(), secondPoint = UUID.randomUUID();
        ShiftOccurrenceEntity missingMorning = shift(firstPost);
        ShiftOccurrenceEntity missingEvening = shift(firstPost);
        ShiftOccurrenceEntity covered = shift(secondPost);
        ShiftOccurrenceEntity coordination = shift(null);

        Set<UUID> uncovered = AssignmentResource.uncoveredPointIds(
            List.of(missingMorning, missingEvening, covered, coordination),
            Set.of(covered.id), Map.of(firstPost, firstPoint, secondPost, secondPoint));

        assertEquals(Set.of(firstPoint), uncovered);
    }

    private static ShiftOccurrenceEntity shift(UUID postId) {
        ShiftOccurrenceEntity shift = new ShiftOccurrenceEntity();
        shift.id = UUID.randomUUID();
        shift.postId = postId;
        return shift;
    }
}
