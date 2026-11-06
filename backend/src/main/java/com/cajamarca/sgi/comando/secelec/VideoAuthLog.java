package com.cajamarca.sgi.comando.secelec;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Últimas consultas de permiso que MediaMTX le hizo a este backend (en memoria, sin tokens). Sirve al diagnóstico para
 * saber si MediaMTX de verdad le pregunta a Comando y qué se le respondió. Con varios backends, cada uno ve solo las suyas.
 */
@ApplicationScoped
public class VideoAuthLog {
    static final int MAX = 500;

    /** Una consulta: qué pidió MediaMTX, desde dónde, si traía token y qué se respondió (con el motivo si se negó). */
    public record Entry(Instant at, String action, String path, String protocol, String ip, boolean withToken, boolean allowed, String reason) {}

    private final ConcurrentLinkedDeque<Entry> entries = new ConcurrentLinkedDeque<>();

    void add(Entry e) {
        entries.addFirst(e);
        while (entries.size() > MAX) entries.pollLast();
    }

    /** Consultas de esa ruta desde el instante dado, la más reciente primero. */
    public List<Entry> since(String path, Instant from) {
        return entries.stream().filter(e -> path.equals(e.path()) && !e.at().isBefore(from)).toList();
    }
}
