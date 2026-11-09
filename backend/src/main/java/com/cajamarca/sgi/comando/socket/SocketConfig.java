package com.cajamarca.sgi.comando.socket;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import io.quarkus.runtime.StartupEvent;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** Configuración REST del gateway y controles de origen para sus recibos. */
@ApplicationScoped
public class SocketConfig {
    @ConfigProperty(name="sgi.socket.enabled",defaultValue="false") boolean enabled;
    @ConfigProperty(name="sgi.socket.url",defaultValue="https://apps.cajamarca.ec/dev.socket") String url;
    @ConfigProperty(name="sgi.socket.source",defaultValue="sgi-comando") String source;
    @ConfigProperty(name="sgi.socket.event",defaultValue="obsint.mensaje") String event;
    @ConfigProperty(name="sgi.socket.channel-prefix",defaultValue="persona.") String channelPrefix;
    @ConfigProperty(name="sgi.socket.http-timeout-seconds",defaultValue="5") long httpSeconds;
    @ConfigProperty(name="sgi.socket.dispatch-batch-size",defaultValue="50") int batchSize;
    @ConfigProperty(name="sgi.socket.message-ttl-minutes",defaultValue="30") long ttlMinutes;
    @ConfigProperty(name="sgi.socket.operator-sync-hours",defaultValue="24") long syncHours;
    @ConfigProperty(name="sgi.socket.presence-enabled",defaultValue="true") boolean presenceEnabled;
    @ConfigProperty(name="sgi.socket.ack.allowed-ips") Optional<String> allowedIps;
    @ConfigProperty(name="sgi.socket.ack.token") Optional<String> ackToken;
    void start(@Observes StartupEvent event) {
        var log=Logger.getLogger(SocketConfig.class);
        if(!enabled) log.info("Socket general apagado (SGI_SOCKET_ENABLED=false)");
        else if(ackAllowedIps().isEmpty() && ackToken().isEmpty())
            log.warn("El endpoint de ACK del socket acepta cualquier origen; definir SGI_SOCKET_ACK_ALLOWED_IPS");
    }
    public boolean enabled() { return enabled; }
    public String baseUrl() {
        try {
            URI u=URI.create(url.trim());
            if(!Set.of("http","https").contains(u.getScheme()) || u.getHost()==null || u.getUserInfo()!=null || u.getQuery()!=null || u.getFragment()!=null)
                throw new IllegalArgumentException();
            return u.toString().replaceAll("/+$","");
        } catch(Exception e) { throw new IllegalStateException("sgi.socket.url debe ser una base http/https sin credenciales ni parámetros"); }
    }
    public URI publishUri() { return URI.create(baseUrl()+"/api/v1/publish"); }
    public URI presenceUri(String channel) { return URI.create(baseUrl()+"/api/v1/presence/"+URLEncoder.encode(channel,StandardCharsets.UTF_8)); }
    public URI healthUri() { return URI.create(baseUrl()+"/q/health/ready"); }
    public String source() { return source; }
    public String event() { return event; }
    public String channelFor(long personaId) { return channelPrefix+personaId; }
    public Duration httpTimeout() { return Duration.ofSeconds(Math.max(1,httpSeconds)); }
    public int dispatchBatchSize() { return Math.max(1,Math.min(500,batchSize)); }
    public Duration messageTtl() { return Duration.ofMinutes(Math.max(1,ttlMinutes)); }
    public Duration operatorSyncWindow() { return Duration.ofHours(Math.max(1,syncHours)); }
    public boolean presenceEnabled() { return presenceEnabled; }
    public Optional<String> ackToken() { return ackToken.filter(s->!s.isBlank()); }
    public List<String> ackAllowedIps() { return allowedIps.stream().flatMap(s->Arrays.stream(s.split(","))).map(String::trim).filter(s->!s.isEmpty()).toList(); }
    public boolean allowsRemote(String address) { return ackAllowedIps().isEmpty() || ackAllowedIps().stream().anyMatch(rule->matchesAddress(address,rule)); }
    /** IP literal: no resolver nombres ni confiar en cabeceras reenviadas. Configuración inválida falla cerrada. */
    public static boolean matchesAddress(String address,String rule) {
        try {
            String[] parts=rule.split("/",-1);
            if(parts.length>2 || !address.matches("[0-9a-fA-F:.]+") || !parts[0].matches("[0-9a-fA-F:.]+")) return false;
            byte[] ip=InetAddress.getByName(address).getAddress(),net=InetAddress.getByName(parts[0]).getAddress();
            if(ip.length!=net.length) return false;
            int bits=parts.length==2?Integer.parseInt(parts[1]):net.length*8;
            if(bits<0 || bits>net.length*8) return false;
            for(int i=0;i<net.length;i++) { int used=Math.min(8,Math.max(0,bits-i*8)); int mask=(0xff<<(8-used))&0xff;
                if((ip[i]&mask)!=(net[i]&mask)) return false; }
            return true;
        } catch(Exception e) { return false; }
    }
}
