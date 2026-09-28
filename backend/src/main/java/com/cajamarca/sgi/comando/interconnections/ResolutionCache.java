package com.cajamarca.sgi.comando.interconnections;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class ResolutionCache {
    @ConfigProperty(name="sgi.interconnections.cache-ttl-seconds", defaultValue="300") int defaultTtl;
    @ConfigProperty(name="sgi.interconnections.allow-stale-seconds", defaultValue="3600") int defaultAllowStale;

    private final Map<String, CachedResolution> cache = new ConcurrentHashMap<>();

    public ResolvedInterconnection resolve(String id, String interfaceId, UUID instanceCountryId, CoreInterconnectionResolver resolver) {
        String key = key(id, interfaceId, instanceCountryId);
        Instant now = Instant.now();
        CachedResolution current = cache.get(key);
        if (current != null && now.isBefore(current.expiresAt)) return current.value;

        try {
            ResolvedInterconnection fresh = resolver.resolve(id, interfaceId, instanceCountryId);
            int ttl = fresh.effectiveCacheTtlSeconds(defaultTtl);
            int stale = fresh.effectiveAllowStaleSeconds(defaultAllowStale);
            cache.put(key, new CachedResolution(fresh, now.plusSeconds(ttl), now.plusSeconds((long) ttl + stale)));
            return fresh;
        } catch (RuntimeException ex) {
            if (current != null && now.isBefore(current.staleUntil)) return current.value;
            throw ex;
        }
    }

    public int size() { return cache.size(); }
    public void clear() { cache.clear(); }
    public void invalidate(String id) { cache.keySet().removeIf(k -> k.startsWith(id + "|")); }

    private static String key(String id, String interfaceId, UUID instanceCountryId) {
        return id + "|" + interfaceId + "|" + instanceCountryId;
    }

    private static final class CachedResolution {
        final ResolvedInterconnection value;
        final Instant expiresAt;
        final Instant staleUntil;
        CachedResolution(ResolvedInterconnection value, Instant expiresAt, Instant staleUntil) {
            this.value = value; this.expiresAt = expiresAt; this.staleUntil = staleUntil;
        }
    }
}
