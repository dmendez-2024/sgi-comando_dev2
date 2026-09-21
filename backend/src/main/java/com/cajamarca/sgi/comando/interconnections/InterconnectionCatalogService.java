package com.cajamarca.sgi.comando.interconnections;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
public class InterconnectionCatalogService {
    @Inject ObjectMapper objectMapper;
    private List<InterconnectionCatalogEntry> entries = Collections.emptyList();

    @PostConstruct
    void load() {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("interconnections/sgi-comando-catalog.json")) {
            if (in == null) throw new IllegalStateException("Missing interconnection catalog resource");
            entries = List.copyOf(objectMapper.readValue(in, new TypeReference<List<InterconnectionCatalogEntry>>() {}));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load interconnection catalog", e);
        }
    }

    public List<InterconnectionCatalogEntry> all() { return entries; }
    public InterconnectionCatalogEntry byId(String id) {
        return entries.stream().filter(e -> id.equals(e.interconnectionId) || id.equals(e.interconnectionRef)).findFirst()
            .orElseThrow(() -> new InterconnectionException("UNKNOWN_INTERCONNECTION", "Interconnection ID is not registered in SGI: Comando"));
    }
}
