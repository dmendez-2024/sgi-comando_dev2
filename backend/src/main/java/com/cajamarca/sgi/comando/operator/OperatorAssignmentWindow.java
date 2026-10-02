package com.cajamarca.sgi.comando.operator;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

final class OperatorAssignmentWindow {
    static final Duration EARLY_ENTRY = Duration.ofMinutes(59);

    record Candidate<T>(T value, Instant startsAt, Instant endsAt) {}

    private OperatorAssignmentWindow() {}

    static <T> List<T> select(List<Candidate<T>> candidates, Instant now) {
        List<Candidate<T>> current = candidates.stream()
            .filter(candidate -> !now.isBefore(candidate.startsAt()) && now.isBefore(candidate.endsAt()))
            .toList();
        if (!current.isEmpty()) return current.stream().map(Candidate::value).toList();

        List<Candidate<T>> early = candidates.stream()
            .filter(candidate -> now.isBefore(candidate.startsAt()))
            .filter(candidate -> !now.isBefore(candidate.startsAt().minus(EARLY_ENTRY)))
            .toList();
        if (early.isEmpty()) return List.of();

        Instant nearestStart = early.stream().map(Candidate::startsAt).min(Comparator.naturalOrder()).orElseThrow();
        return early.stream()
            .filter(candidate -> candidate.startsAt().equals(nearestStart))
            .map(Candidate::value)
            .toList();
    }
}
