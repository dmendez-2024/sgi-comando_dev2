package com.cajamarca.sgi.comando.interconnections;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class CircuitRegistry {
    @ConfigProperty(name="sgi.interconnections.circuit-failure-threshold", defaultValue="5") int failureThreshold;
    @ConfigProperty(name="sgi.interconnections.circuit-open-seconds", defaultValue="30") int openSeconds;
    private final Map<String,State> states = new ConcurrentHashMap<>();

    public void assertAvailable(String id) {
        State s = states.get(id);
        if (s != null && s.openUntil != null && Instant.now().isBefore(s.openUntil)) {
            throw new InterconnectionException("CIRCUIT_OPEN", "Interconnection circuit is temporarily open");
        }
    }
    public void success(String id) { states.remove(id); }
    public void failure(String id) {
        states.compute(id, (k,s) -> {
            State n = s == null ? new State() : s;
            n.failures++;
            if (n.failures >= Math.max(failureThreshold, 1)) n.openUntil = Instant.now().plusSeconds(Math.max(openSeconds, 1));
            return n;
        });
    }
    private static final class State { int failures; Instant openUntil; }
}
