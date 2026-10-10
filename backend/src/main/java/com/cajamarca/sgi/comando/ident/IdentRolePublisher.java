package com.cajamarca.sgi.comando.ident;

import com.cajamarca.sgi.comando.security.InstanceCountrySource;
import com.cajamarca.sgi.comando.interconnections.CoreInterconnectionResolver;
import com.cajamarca.sgi.comando.interconnections.InterconnectionIds;
import com.cajamarca.sgi.comando.interconnections.ResolvedInterconnection;
import com.cajamarca.sgi.comando.security.IdentTokenVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/**
 * SGI: Comando publica su catalogo de Roles (rbac/roles.json) en IDENT (SGI_COM_IDENT_0002_v001; cada Sistema es dueno
 * de sus Roles), igual que SIC_RRMM y CORE. Al arrancar, en segundo plano y con reintentos:
 *   1. direcciones desde CORE (login para el token, roles para publicar), sin URL fija;
 *   2. clave M2M de SGI (perfil SGI_COM_PUBLISH) desde OpenBao con AppRole propio;
 *   3. token client_credentials en IDENT y PUT de cada Rol. role_version = huella de la definicion (cambia solo si cambia).
 * IDENT responde 202 si un cambio de alcance requiere aprobacion de su administrador.
 */
@ApplicationScoped
public class IdentRolePublisher {
    private static final Logger LOG = Logger.getLogger(IdentRolePublisher.class);

    @ConfigProperty(name = "sgi.ident.publish-roles.enabled", defaultValue = "false") boolean enabled;
    @ConfigProperty(name = "sgi.ident.publisher.client-id") Optional<String> clientId;
    @ConfigProperty(name = "sgi.ident.publisher.secret-ref") Optional<String> secretRef;
    @Inject CoreInterconnectionResolver core;
    @Inject InstanceCountrySource instances;
    @Inject IdentTokenVerifier ident;
    @Inject OpenBaoSecretReader secrets;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    void onStart(@Observes StartupEvent ignored) {
        if (!enabled) return;
        Thread.ofVirtual().name("ident-role-publisher").start(() -> {
            long[] waits = {5, 10, 30, 60, 120};
            for (int i = 0; i < waits.length; i++) {
                try {
                    Thread.sleep(waits[i] * 1000);
                    LOG.infof("Roles de SGI_COM publicados en IDENT: %s", publish());
                    return;
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                catch (Exception e) { LOG.warnf("Publicacion de Roles en IDENT fallo (intento %d/%d): %s", i + 1, waits.length, e.getMessage()); }
            }
            LOG.error("No se pudieron publicar los Roles de SGI_COM en IDENT; se reintenta en el proximo arranque");
        });
    }

    Map<String, Integer> publish() throws IOException, InterruptedException {
        List<Map<String, Object>> roles = payloads(rbac());
        String token = token();   // primero: resuelve la direccion de IDENT y deja lista la Instancia PE de SGI
        String rolesUrl = ident.internal(rolesUrl().replaceAll("/+$", ""));
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map<String, Object> role : roles) {
            String code = (String) role.get("role_code");
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(rolesUrl + "/" + URLEncoder.encode(code, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json").header("Authorization", "Bearer " + token)
                    .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(role))).build(), HttpResponse.BodyHandlers.ofString());
            out.put(code, r.statusCode());
            if (r.statusCode() / 100 != 2) throw new IOException("IDENT rechazo el Rol " + code + " (HTTP " + r.statusCode() + "): " + r.body());
        }
        return out;
    }

    @ActivateRequestContext
    String rolesUrl() {
        ResolvedInterconnection r = core.resolve(InterconnectionIds.IDENT_ROLES, null, instances.any());   // sin persona: Instancia PE de SGI_COM segun CORE
        return r.resolvedUrl != null && !r.resolvedUrl.isBlank() ? r.resolvedUrl : r.baseUrl + r.path;
    }

    private String token() throws IOException, InterruptedException {
        String base = ident.identPublicUrl();
        JsonNode discovery = mapper.readTree(http.send(HttpRequest.newBuilder(URI.create(ident.internal(base + "/.well-known/openid-configuration")))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
        String tokenEndpoint = discovery.path("token_endpoint").asText("");
        if (tokenEndpoint.isBlank()) throw new IOException("IDENT no publica token_endpoint en su descubrimiento");
        String secret = secrets.read(req(secretRef, "sgi.ident.publisher.secret-ref"));
        String form = "grant_type=client_credentials&client_id=" + URLEncoder.encode(req(clientId, "sgi.ident.publisher.client-id"), StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8);
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(ident.internal(tokenEndpoint))).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IOException("IDENT rechazo la credencial de SGI_COM (HTTP " + r.statusCode() + ")");
        return mapper.readTree(r.body()).path("access_token").asText();
    }

    private JsonNode rbac() throws IOException {
        try (InputStream in = IdentRolePublisher.class.getResourceAsStream("/rbac/roles.json")) {
            if (in == null) throw new IOException("rbac/roles.json no encontrado");
            return mapper.readTree(in);
        }
    }

    /** Contrato de publicacion de IDENT por cada Rol de roles.json. */
    static List<Map<String, Object>> payloads(JsonNode rbac) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode r : rbac.path("roles")) {
            String code = r.path("code").asText(), name = r.path("name").asText(""), scope = r.path("scopeRequirement").asText("");
            String status = r.path("status").asText("ACTIVE");
            if (code.isBlank() || name.isBlank() || scope.isBlank()) throw new IllegalStateException("rbac/roles.json: cada Rol necesita code, name y scopeRequirement");
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("role_code", code); m.put("role_name", name);
            m.put("role_version", fingerprint(List.of("name=" + name, "scope=" + scope, "status=" + status)));
            m.put("permissions_version", fingerprint(List.of("code=" + code)));
            m.put("scope_requirement", scope); m.put("status", status);
            out.add(m);
        }
        return out;
    }

    private static String fingerprint(Collection<String> parts) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("\n", parts).getBytes(StandardCharsets.UTF_8)), 0, 8); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static String req(Optional<String> v, String key) {
        return v.filter(x -> !x.isBlank()).orElseThrow(() -> new IllegalStateException(key + " no configurado"));
    }
}
