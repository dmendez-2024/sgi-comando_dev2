package com.cajamarca.sgi.comando.push;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/** Configuración del push FCM (HTTP v1) a SGI Operador. */
@ApplicationScoped
public class PushConfig {
    @ConfigProperty(name="sgi.push.enabled",defaultValue="false") boolean enabled;
    @ConfigProperty(name="sgi.push.credentials-path") Optional<String> credentialsPath;
    @ConfigProperty(name="sgi.push.credentials-json") Optional<String> credentialsJson;
    @ConfigProperty(name="sgi.push.project-id") Optional<String> projectId;
    @ConfigProperty(name="sgi.push.fcm-url",defaultValue="https://fcm.googleapis.com") String fcmUrl;
    @ConfigProperty(name="sgi.push.http-timeout-seconds",defaultValue="5") long httpSeconds;

    void start(@Observes StartupEvent event) {
        var log=Logger.getLogger(PushConfig.class);
        if(!enabled) log.info("Push FCM a SGI Operador apagado (SGI_PUSH_ENABLED=false)");
        else if(credentialsPath().isEmpty() && credentialsJson().isEmpty())
            log.warn("Push FCM encendido sin cuenta de servicio; definir SGI_PUSH_CREDENTIALS_PATH o SGI_PUSH_CREDENTIALS_JSON");
    }

    public boolean enabled() { return enabled; }
    public Optional<String> credentialsPath() { return credentialsPath.map(String::trim).filter(s->!s.isEmpty()); }
    public Optional<String> credentialsJson() { return credentialsJson.map(String::trim).filter(s->!s.isEmpty()); }
    public Optional<String> projectId() { return projectId.map(String::trim).filter(s->!s.isEmpty()); }
    public Duration httpTimeout() { return Duration.ofSeconds(Math.max(1,httpSeconds)); }
    public URI sendUri(String project) {
        String base=fcmUrl.trim().replaceAll("/+$","");
        if(!project.matches("[a-z0-9-]+")) throw new IllegalStateException("project_id de Firebase inválido");
        return URI.create(base+"/v1/projects/"+project+"/messages:send");
    }
}
