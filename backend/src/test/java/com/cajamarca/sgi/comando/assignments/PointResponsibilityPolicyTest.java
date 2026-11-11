package com.cajamarca.sgi.comando.assignments;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PointResponsibilityPolicyTest {
    @Test void everyoneParticipatesBeforeRepeatingAndRemainsBalanced() {
        List<UUID> agents = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        Map<UUID, Long> history = new HashMap<>();
        Random random = new Random(37);
        for (int round = 0; round < 20; round++) {
            Set<UUID> selected = new HashSet<>();
            for (int i = 0; i < agents.size(); i++) {
                UUID winner = PointResponsibilityPolicy.choose(agents, history, random);
                assertTrue(selected.add(winner), "No debe repetir dentro de la ronda");
                history.merge(winner, 1L, Long::sum);
            }
            assertEquals(new HashSet<>(agents), selected);
        }
        assertTrue(history.values().stream().allMatch(count -> count == 20));
    }

    @Test void unavailableAgentDoesNotPreventAnotherEligibleAgentFromRepeating() {
        UUID available = UUID.randomUUID(), absent = UUID.randomUUID();
        assertEquals(available, PointResponsibilityPolicy.choose(List.of(available), Map.of(available, 5L, absent, 0L), new Random(1)));
    }

    @Test void newcomerAndReturningAgentWithFewerDesignationsHavePriority() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertEquals(second, PointResponsibilityPolicy.choose(List.of(first, second), Map.of(first, 3L), new Random(2)));
    }

    @Test void tieIsRandomRatherThanFixedToOneAgent() {
        List<UUID> agents = List.of(UUID.randomUUID(), UUID.randomUUID());
        Random random = new Random(9);
        Set<UUID> winners = new HashSet<>();
        for (int i = 0; i < 100; i++) winners.add(PointResponsibilityPolicy.choose(agents, Map.of(), random));
        assertEquals(new HashSet<>(agents), winners);
    }

    @Test void noCandidateHasNoWinnerAndSingleCandidateIsAllowed() {
        UUID only = UUID.randomUUID();
        assertNull(PointResponsibilityPolicy.choose(List.of(), Map.of(), new Random()));
        assertEquals(only, PointResponsibilityPolicy.choose(List.of(only, only), Map.of(), new Random()));
    }
}
