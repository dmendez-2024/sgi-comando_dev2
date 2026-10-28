package com.cajamarca.sgi.comando.ident;

import com.cajamarca.sgi.comando.interconnections.InterconnectionException;
import com.cajamarca.sgi.comando.security.IdentTokenVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SEG-M2M: token de servicio de IDENT con el que SGI_COM se identifica ante otro Sistema (p. ej. SIC_RRMM). Lo usa el
 * motor de integraciones cuando el binding de CORE dice OAUTH2_CLIENT_CREDENTIALS: la clave del cliente M2M se lee en
 * OpenBao en el credentialRef de CORE (vault:...), y el client_id sale de sgi.m2m.client-id.<DESTINO> (no es
 * secreto). El destino sale del codigo de la interconexion (<ORIGEN>_<DESTINO>_<NNNN>_v<NNN>, §9). La persona que uso SGI_COM no interviene (el destino valida al Sistema). Token en cache hasta
 * poco antes de vencer; nunca se registran la clave ni el token.
 */
@ApplicationScoped
public class IdentServiceTokens {
    @Inject IdentTokenVerifier ident;
    @Inject OpenBaoSecretReader secrets;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private record Token(String value, Instant refreshAt) {}
    private final ConcurrentHashMap<String, Token> cache = new ConcurrentHashMap<>();

    /** Program ID destino desde el codigo versionado de SGI_COM: SGI_COM_SIC_RRMM_0001_v001 -> SIC_RRMM. */
    public static String targetOf(String interconnectionId) {
        String c = interconnectionId == null ? "" : interconnectionId.trim();
        if (!c.startsWith("SGI_COM_")) return "";
        return c.substring("SGI_COM_".length()).replaceFirst("(?i)_+\\d{4}(_v\\d{3})?$", "");
    }

    /** Access token para llamar al Sistema destino; la clave esta en OpenBao (credentialRef vault: de CORE). */
    public String tokenFor(String targetProgramId, String credentialRef) {
        String clientId = ConfigProvider.getConfig().getOptionalValue("sgi.m2m.client-id." + targetProgramId, String.class)
                .map(String::trim).orElse("");
        if (clientId.isEmpty()) {
            throw new InterconnectionException("M2M_CLIENT_NOT_CONFIGURED", "No hay cliente M2M de SGI_COM para " + targetProgramId + " (sgi.m2m.client-id.<DESTINO>)");
        }
        if (credentialRef == null || !credentialRef.trim().startsWith("vault:")) {
            throw new InterconnectionException("CREDENTIAL_NOT_AVAILABLE", "CORE pide OAUTH2_CLIENT_CREDENTIALS sin credentialRef vault: para " + targetProgramId);
        }
        Token t = cache.get(clientId);
        if (t != null && Instant.now().isBefore(t.refreshAt())) return t.value();
        try {
            Token fresh = request(clientId, credentialRef.trim());
            cache.put(clientId, fresh);
            return fresh.value();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterconnectionException("M2M_TOKEN_UNAVAILABLE", "Interrumpido al pedir el token de servicio a IDENT");
        } catch (InterconnectionException e) {
            throw e;
        } catch (Exception e) {
            throw new InterconnectionException("M2M_TOKEN_UNAVAILABLE", "No se pudo obtener el token de servicio de IDENT: " + e.getClass().getSimpleName());
        }
    }

    private Token request(String clientId, String credentialRef) throws Exception {
        String base = ident.identPublicUrl();
        JsonNode discovery = mapper.readTree(http.send(HttpRequest.newBuilder(URI.create(ident.internal(base + "/.well-known/openid-configuration")))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
        String tokenEndpoint = discovery.path("token_endpoint").asText("");
        if (tokenEndpoint.isBlank()) throw new InterconnectionException("M2M_TOKEN_UNAVAILABLE", "IDENT no publica token_endpoint");
        String form = "grant_type=client_credentials&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(secrets.read(credentialRef), StandardCharsets.UTF_8);
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(ident.internal(tokenEndpoint))).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new InterconnectionException("M2M_TOKEN_REJECTED", "IDENT rechazo el cliente " + clientId + " (HTTP " + r.statusCode() + ")");
        JsonNode body = mapper.readTree(r.body());
        String value = body.path("access_token").asText("");
        if (value.isBlank()) throw new InterconnectionException("M2M_TOKEN_UNAVAILABLE", "IDENT no devolvio access_token");
        long ttl = body.path("expires_in").asLong(600);
        long skew = Math.max(30, Math.min(120, ttl / 5));
        return new Token(value, Instant.now().plusSeconds(Math.max(1, ttl - skew)));
    }

    public void clearCache() { cache.clear(); }
}
