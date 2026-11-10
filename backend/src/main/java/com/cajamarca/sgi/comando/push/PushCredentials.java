package com.cajamarca.sgi.comando.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Cuenta de servicio de Firebase y su token OAuth para FCM HTTP v1. Se carga al primer envío (no al arrancar) y el token
 * se renueva solo cuando está por vencer.
 */
@ApplicationScoped
public class PushCredentials {
    static final String SCOPE="https://www.googleapis.com/auth/firebase.messaging";
    @Inject PushConfig config;
    private GoogleCredentials credentials;
    private String project;

    /** project_id de Firebase: el configurado o el de la cuenta de servicio. */
    public synchronized String projectId() {
        load();
        return project;
    }

    /** Token de acceso vigente (Bearer) para FCM. */
    public synchronized String accessToken() throws IOException {
        load();
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }

    private void load() {
        if(credentials!=null) return;
        try(InputStream in=open()) {
            ServiceAccountCredentials account=ServiceAccountCredentials.fromStream(in);
            String id=config.projectId().orElse(account.getProjectId());
            if(id==null || id.isBlank()) throw new IllegalStateException("La cuenta de servicio de Firebase no trae project_id; definir SGI_PUSH_PROJECT_ID");
            credentials=account.createScoped(List.of(SCOPE));
            project=id.trim();
        } catch(IOException e) {
            throw new IllegalStateException("No fue posible leer la cuenta de servicio de Firebase",e);
        }
    }

    private InputStream open() throws IOException {
        var json=config.credentialsJson();
        if(json.isPresent()) return new ByteArrayInputStream(json.get().getBytes(StandardCharsets.UTF_8));
        var path=config.credentialsPath().orElseThrow(()->new IllegalStateException("Push FCM sin cuenta de servicio configurada"));
        return Files.newInputStream(Path.of(path));
    }
}
