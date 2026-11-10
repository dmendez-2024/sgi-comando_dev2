package com.cajamarca.sgi.comando.assignments;

import java.util.*;
import java.util.random.RandomGenerator;

/** Equal opportunity by number of designations at the Point; performance is not an input. */
public final class PointResponsibilityPolicy {
    private PointResponsibilityPolicy() {}

    public static UUID choose(Collection<UUID> eligible, Map<UUID, Long> history, RandomGenerator random) {
        List<UUID> candidates = eligible.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (candidates.isEmpty()) return null;
        long minimum = candidates.stream().mapToLong(id -> history.getOrDefault(id, 0L)).min().orElseThrow();
        List<UUID> tied = candidates.stream().filter(id -> history.getOrDefault(id, 0L) == minimum).toList();
        return tied.get(random.nextInt(tied.size()));
    }
}
