package com.cajamarca.sgi.comando.operator;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OperatorAssignmentWindowTest {
    private static final Instant NOW = Instant.parse("2026-09-30T14:00:00Z");

    @Test void currentShiftWinsOverNextShiftInsideEarlyWindow() {
        var current = candidate("current", NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        var next = candidate("next", NOW.plusSeconds(1800), NOW.plusSeconds(7200));
        assertEquals(List.of("current"), OperatorAssignmentWindow.select(List.of(current,next),NOW));
    }

    @Test void allowsEntryExactlyFiftyNineMinutesBeforeStart() {
        var next = candidate("next", NOW.plusSeconds(59*60), NOW.plusSeconds(8*3600));
        assertEquals(List.of("next"), OperatorAssignmentWindow.select(List.of(next),NOW));
    }

    @Test void rejectsEntryMoreThanFiftyNineMinutesBeforeStart() {
        var next = candidate("next", NOW.plusSeconds(59*60+1), NOW.plusSeconds(8*3600));
        assertEquals(List.of(), OperatorAssignmentWindow.select(List.of(next),NOW));
    }

    @Test void excludesShiftAtItsEndBoundary() {
        var expired = candidate("expired", NOW.minusSeconds(3600), NOW);
        assertEquals(List.of(), OperatorAssignmentWindow.select(List.of(expired),NOW));
    }

    @Test void preservesAmbiguityForOverlappingCurrentAssignments() {
        var first = candidate("first", NOW.minusSeconds(3600), NOW.plusSeconds(3600));
        var second = candidate("second", NOW.minusSeconds(1800), NOW.plusSeconds(7200));
        assertEquals(List.of("first","second"), OperatorAssignmentWindow.select(List.of(first,second),NOW));
    }

    @Test void choosesNearestUpcomingShiftWhenSeveralAreInsideWindow() {
        var nearest = candidate("nearest", NOW.plusSeconds(600), NOW.plusSeconds(3600));
        var later = candidate("later", NOW.plusSeconds(2400), NOW.plusSeconds(7200));
        assertEquals(List.of("nearest"), OperatorAssignmentWindow.select(List.of(later,nearest),NOW));
    }

    private OperatorAssignmentWindow.Candidate<String> candidate(String value,Instant start,Instant end) {
        return new OperatorAssignmentWindow.Candidate<>(value,start,end);
    }
}
