package com.cajamarca.sgi.comando.socket;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Consultas breves con caché: un error de presencia nunca bloquea el envío. */
@ApplicationScoped
public class SocketPresence {
    @Inject SocketConfig config;
    @Inject ObjectMapper mapper;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private record Entry(Optional<Boolean> value,Instant until) {}
    private final Map<String,Entry> cache=new ConcurrentHashMap<>();
    public Optional<Boolean> online(long personaId) {
        if(!config.enabled() || !config.presenceEnabled()) return Optional.empty();
        try {
            String channel=config.channelFor(personaId);
            return cached(channel,config.presenceUri(channel),15,true);
        } catch(Exception e) { return Optional.empty(); }
    }
    public boolean reachable() {
        if(!config.enabled()) return false;
        try { return cached("gateway-health",config.healthUri(),30,false).orElse(false); }
        catch(Exception e) { return false; }
    }
    private Optional<Boolean> cached(String key,URI uri,int seconds,boolean presence) {
        Entry old=cache.get(key); Instant now=Instant.now();
        if(old!=null && old.until().isAfter(now)) return old.value();
        // Acotar la memoria incluso cuando se consultan muchas personas diferentes.
        if(cache.size()>2000) cache.entrySet().removeIf(e->e.getValue().until().isBefore(now));
        Optional<Boolean> value;
        try {
            var response=http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(2)).GET().build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()<200 || response.statusCode()>=300) value=presence?Optional.empty():Optional.of(false);
            else if(!presence) value=Optional.of(true);
            else { var n=mapper.readTree(response.body()).path("online"); value=n.isBoolean()?Optional.of(n.booleanValue()):Optional.empty(); }
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); value=Optional.empty(); }
        catch(Exception e) { value=Optional.empty(); }
        cache.put(key,new Entry(value,now.plusSeconds(seconds))); return value;
    }
    @PreDestroy void close() { http.shutdownNow(); }
}
