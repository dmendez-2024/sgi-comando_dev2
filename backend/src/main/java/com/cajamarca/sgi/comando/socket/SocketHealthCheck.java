package com.cajamarca.sgi.comando.socket;

import io.smallrye.health.api.HealthGroup;
import org.eclipse.microprofile.health.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Grupo separado: la disponibilidad del gateway no condiciona readiness. */
@HealthGroup("socket")
@ApplicationScoped
public class SocketHealthCheck implements HealthCheck {
    @Inject SocketConfig config;
    @Inject SocketPresence presence;
    @Inject SocketPublisher publisher;
    public HealthCheckResponse call() {
        boolean reachable=presence.reachable(); String url;
        try { url=config.baseUrl(); } catch(Exception e) { url="URL inválida"; }
        return HealthCheckResponse.named("socket").status(!config.enabled()||reachable)
            .withData("enabled",config.enabled()).withData("reachable",reachable).withData("url",url)
            .withData("lastPublishOk",publisher.lastPublishOk()).build();
    }
}
