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
 * El backend de SGI (compartido por Comando y Operador) publica los Roles de los dos Sistemas en IDENT
 * (SGI_COM_IDENT_0002_v001; cada Sistema es dueno de sus Roles), igual que SIC_RRMM y CORE:
 *   - SGI_COM: rbac/roles.json (comunes) + rbac/roles-comando.json (solo Comando), clave SGI_COM_PUBLISH;
 *   - SGI_OPR: rbac/roles.json (comunes) + rbac/roles-operador.json (solo Operador), clave SGI_OPR_PUBLISH.
 * Al arrancar, en segundo plano y con reintentos independientes por Sistema:
 *   1. direcciones desde CORE (login para el token, roles para publicar), sin URL fija; SGI_OPR usa la misma
 *      interconexion cambiando el Sistema de la ruta (/programs/SGI_COM/ -> /programs/SGI_OPR/);
 *   2. clave M2M de cada Sistema desde OpenBao con el AppRole propio de SGI;
 *   3. token client_credentials en IDENT y PUT de cada Rol. role_version = huella de la definicion (cambia solo si cambia).
 * IDENT responde 202 si un cambio de alcance requiere aprobacion de su administrador.
 */
@ApplicationScoped
public class IdentRolePublisher {
    private static final Logger LOG = Logger.getLogger(IdentRolePublisher.class);

    @ConfigProperty(name = "sgi.ident.publish-roles.enabled", defaultValue = "false") boolean enabled;
    @ConfigProperty(name = "sgi.ident.publisher.client-id") Optional<String> clientId;
    @ConfigProperty(name = "sgi.ident.publisher.secret-ref") Optional<String> secretRef;
    @ConfigProperty(name = "sgi.ident.opr-publisher.client-id") Optional<String> oprClientId;
    @ConfigProperty(name = "sgi.ident.opr-publisher.secret-ref") Optional<String> oprSecretRef;
    @Inject CoreInterconnectionResolver core;
    @Inject InstanceCountrySource instances;
    @Inject IdentTokenVerifier ident;
    @Inject OpenBaoSecretReader secrets;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    static final String COM = "SGI_COM", OPR = "SGI_OPR";
    static final String COMMON = "/rbac/roles.json";
    static final Map<String, String> EXTRA = Map.of(COM, "/rbac/roles-comando.json", OPR, "/rbac/roles-operador.json");

    void onStart(@Observes StartupEvent ignored) {
        if (!enabled) return;
        Thread.ofVirtual().name("ident-role-publisher").start(() -> {
            Set<String> pending = new LinkedHashSet<>(List.of(COM, OPR));
            long[] waits = {5, 10, 30, 60, 120};
            for (int i = 0; i < waits.length && !pending.isEmpty(); i++) {
                try { Thread.sleep(waits[i] * 1000); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                for (Iterator<String> it = pending.iterator(); it.hasNext(); ) {
                    String program = it.next();
                    try {
                        LOG.infof("Roles de %s publicados en IDENT: %s", program, publish(program));
                        it.remove();
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                    catch (Exception e) { LOG.warnf("Publicacion de Roles de %s en IDENT fallo (intento %d/%d): %s", program, i + 1, waits.length, e.getMessage()); }
                }
            }
            for (String program : pending) LOG.errorf("No se pudieron publicar los Roles de %s en IDENT; se reintenta en el proximo arranque", program);
        });
    }

    Map<String, Integer> publish(String program) throws IOException, InterruptedException {
        List<Map<String, Object>> roles = payloads(catalog(rbac(COMMON), rbac(EXTRA.get(program))));
        String token = OPR.equals(program)
                ? token(req(oprClientId, "sgi.ident.opr-publisher.client-id"), req(oprSecretRef, "sgi.ident.opr-publisher.secret-ref"))
                : token(req(clientId, "sgi.ident.publisher.client-id"), req(secretRef, "sgi.ident.publisher.secret-ref"));
        // primero el token: resuelve la direccion de IDENT y deja lista la Instancia PE de SGI
        String rolesUrl = ident.internal(programUrl(rolesUrl().replaceAll("/+$", ""), program));
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

    /** La interconexion de CORE apunta a /programs/SGI_COM/roles; para SGI_OPR solo cambia el Sistema de la ruta. */
    static String programUrl(String comRolesUrl, String program) {
        String com = "/programs/" + COM + "/roles";
        if (!comRolesUrl.endsWith(com)) throw new IllegalStateException("La direccion de Roles de CORE no termina en " + com + ": " + comRolesUrl);
        return COM.equals(program) ? comRolesUrl : comRolesUrl.substring(0, comRolesUrl.length() - com.length()) + "/programs/" + program + "/roles";
    }

    private String token(String client, String secretReference) throws IOException, InterruptedException {
        String base = ident.identPublicUrl();
        JsonNode discovery = mapper.readTree(http.send(HttpRequest.newBuilder(URI.create(ident.internal(base + "/.well-known/openid-configuration")))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
        String tokenEndpoint = discovery.path("token_endpoint").asText("");
        if (tokenEndpoint.isBlank()) throw new IOException("IDENT no publica token_endpoint en su descubrimiento");
        String secret = secrets.read(secretReference);
        String form = "grant_type=client_credentials&client_id=" + URLEncoder.encode(client, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8);
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(ident.internal(tokenEndpoint))).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IOException("IDENT rechazo la credencial " + client + " (HTTP " + r.statusCode() + ")");
        return mapper.readTree(r.body()).path("access_token").asText();
    }

    private JsonNode rbac(String resource) throws IOException {
        try (InputStream in = IdentRolePublisher.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException(resource + " no encontrado");
            return mapper.readTree(in);
        }
    }

    /** Roles comunes + Roles propios del Sistema. Un codigo repetido es un error: cada Rol se define en un solo archivo. */
    static JsonNode catalog(JsonNode common, JsonNode extra) {
        com.fasterxml.jackson.databind.node.ObjectNode out = new ObjectMapper().createObjectNode();
        com.fasterxml.jackson.databind.node.ArrayNode roles = out.putArray("roles");
        Set<String> codes = new HashSet<>();
        for (JsonNode source : List.of(common, extra))
            for (JsonNode r : source.path("roles")) {
                if (!codes.add(r.path("code").asText())) throw new IllegalStateException("Rol repetido entre roles.json y el catalogo del Sistema: " + r.path("code").asText());
                roles.add(r);
            }
        return out;
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
