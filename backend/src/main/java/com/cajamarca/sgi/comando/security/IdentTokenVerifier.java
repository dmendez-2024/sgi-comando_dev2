package com.cajamarca.sgi.comando.security;

import com.cajamarca.sgi.comando.interconnections.CoreInterconnectionResolver;
import com.cajamarca.sgi.comando.interconnections.InterconnectionIds;
import com.cajamarca.sgi.comando.interconnections.ResolvedInterconnection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Login de SGI: Comando con IDENT (interconexion SGI_COM_IDENT_0001_v001), igual que SIC_RRMM y CORE. Valida el access
 * token de IDENT (RS256) con Java estandar, sin dependencias nuevas: firma con las claves publicas de IDENT (JWKS),
 * emisor (iss), audiencia (aud=SGI_COM) y vencimiento. La direccion de IDENT NO es fija: se resuelve en CORE para la
 * Instancia PE + ambiente de SGI (SITC-NOM-001 §10) y solo se aceptan hosts de sgi.ident.allowed-hosts.
 */
@ApplicationScoped
public class IdentTokenVerifier {
    private static final Logger LOG = Logger.getLogger(IdentTokenVerifier.class);
    private static final Duration ADDRESS_TTL = Duration.ofMinutes(5), KEYS_TTL = Duration.ofMinutes(10), SKEW = Duration.ofSeconds(30);

    /** Persona que entro con IDENT. email = "quien lo hizo" en la auditoria de SGI; personaId = id de DHO (si IDENT lo envia). */
    public record Identity(String subject, String email, Long personaId, UUID instanceCountryId, List<String> groups, Instant expiresAt) {}
    public static class InvalidTokenException extends RuntimeException { public InvalidTokenException(String m) { super(m); } }

    @ConfigProperty(name = "sgi.ident.enabled", defaultValue = "false") boolean enabled;
    @ConfigProperty(name = "sgi.ident.login-interconnection", defaultValue = InterconnectionIds.IDENT_AUTH) String loginInterconnection;
    @ConfigProperty(name = "sgi.ident.allowed-hosts") Optional<String> allowedHosts;
    @ConfigProperty(name = "sgi.ident.client-id", defaultValue = "sgi-com-web") String clientId;
    @ConfigProperty(name = "sgi.ident.audience", defaultValue = "SGI_COM") String audience;
    /** SOLO LOCAL (SGI en Docker): direccion por la que el backend llega a IDENT; el emisor sigue siendo la publica. */
    @ConfigProperty(name = "sgi.ident.internal-url") Optional<String> internalUrl;
    @Inject CoreInterconnectionResolver core;
    @Inject InstanceCountrySource instances;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    private final Map<String, PublicKey> keys = new ConcurrentHashMap<>();
    private volatile String publicUrl, issuer;
    private volatile Instant addressUntil = Instant.EPOCH, keysUntil = Instant.EPOCH, lastForcedRefresh = Instant.EPOCH;

    public boolean enabled() { return enabled; }
    public String clientId() { return clientId; }

    /** SOLO LOCAL: cambia la direccion publica de IDENT por la interna (SGI en Docker). En produccion no cambia nada. */
    public String internal(String url) {
        String base = identPublicUrl();
        return internalUrl.filter(v -> !v.isBlank()).filter(v -> url.startsWith(base)).map(v -> v.replaceAll("/+$", "") + url.substring(base.length())).orElse(url);
    }

    /** Direccion publica de IDENT (la que usa el navegador), resuelta en CORE. */
    @ActivateRequestContext
    public synchronized String identPublicUrl() {
        if (publicUrl != null && Instant.now().isBefore(addressUntil)) return publicUrl;
        ResolvedInterconnection r = core.resolve(loginInterconnection, null, instances.any());   // IDENT es el mismo para todas las Instancias PE
        String url = (r.resolvedUrl != null && !r.resolvedUrl.isBlank() ? r.resolvedUrl : r.baseUrl + r.path).replaceAll("/+$", "");
        URI u = URI.create(url);
        Set<String> hosts = new HashSet<>();
        for (String h : allowedHosts.orElse("").split(",")) if (!h.isBlank()) hosts.add(h.trim().toLowerCase(Locale.ROOT));
        if (!hosts.contains(String.valueOf(u.getHost()).toLowerCase(Locale.ROOT)))
            throw new IllegalStateException("CORE entrego para " + loginInterconnection + " el host '" + u.getHost() + "', que no esta en sgi.ident.allowed-hosts");
        if (!Objects.equals(url, publicUrl)) { keys.clear(); issuer = null; keysUntil = Instant.EPOCH; }
        publicUrl = url;
        addressUntil = Instant.now().plus(ADDRESS_TTL);
        return url;
    }

    public Identity verify(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 3) throw new InvalidTokenException("Token mal formado");
        JsonNode header = json(parts[0]), claims = json(parts[1]);
        if (!"RS256".equals(header.path("alg").asText())) throw new InvalidTokenException("Algoritmo no permitido");
        PublicKey key = key(header.path("kid").asText(""));
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(key);
            sig.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!sig.verify(Base64.getUrlDecoder().decode(parts[2]))) throw new InvalidTokenException("Firma invalida");
        } catch (InvalidTokenException e) { throw e; } catch (Exception e) { throw new InvalidTokenException("Firma invalida"); }
        if (!Objects.equals(claims.path("iss").asText(), issuer)) throw new InvalidTokenException("Emisor no es IDENT");
        JsonNode aud = claims.path("aud");
        boolean audOk = false;
        if (aud.isArray()) { for (JsonNode a : aud) audOk |= audience.equals(a.asText()); } else audOk = audience.equals(aud.asText());
        if (!audOk) throw new InvalidTokenException("El token no es para SGI_COM (aud)");
        Instant exp = Instant.ofEpochSecond(claims.path("exp").asLong(0));
        if (Instant.now().minus(SKEW).isAfter(exp)) throw new InvalidTokenException("Token vencido");
        List<String> groups = new ArrayList<>();
        claims.path("groups").forEach(g -> groups.add(g.asText()));
        String email = claims.path("email").asText("");
        Long personaId = claims.path("persona_id").canConvertToLong() ? claims.path("persona_id").asLong() : null;
        // PE del ingreso (IDENT lo saca del dominio del correo, DEC-46); si el rol es por PE, IDENT tambien envia instance_country_id.
        UUID pe = uuid(claims.path("active_instance_country_id").asText(""));
        if (pe == null) pe = uuid(claims.path("instance_country_id").asText(""));
        return new Identity(claims.path("sub").asText(), email.isBlank() ? null : email, personaId, pe, groups, exp);
    }

    private static UUID uuid(String v) {
        try { return v == null || v.isBlank() || "null".equals(v) ? null : UUID.fromString(v.trim()); }
        catch (IllegalArgumentException e) { return null; }
    }

    private PublicKey key(String kid) {
        refreshKeys(false);
        PublicKey k = keys.get(kid);
        if (k == null && Instant.now().isAfter(lastForcedRefresh.plusSeconds(30))) { refreshKeys(true); k = keys.get(kid); }
        if (k == null) throw new InvalidTokenException("Clave de firma desconocida");
        return k;
    }

    private synchronized void refreshKeys(boolean force) {
        if (!force && issuer != null && Instant.now().isBefore(keysUntil)) return;
        if (force) lastForcedRefresh = Instant.now();
        try {
            String base = identPublicUrl();
            String fetchBase = internalUrl.filter(v -> !v.isBlank()).map(v -> v.replaceAll("/+$", "")).orElse(base);
            JsonNode discovery = get(fetchBase + "/.well-known/openid-configuration");
            String jwksUri = discovery.path("jwks_uri").asText("");
            String jwksPath = jwksUri.startsWith(base) ? jwksUri.substring(base.length()) : URI.create(jwksUri).getPath();
            Map<String, PublicKey> fresh = new HashMap<>();
            for (JsonNode k : get(fetchBase + jwksPath).path("keys")) {
                if (!"RSA".equals(k.path("kty").asText())) continue;
                BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(k.path("n").asText()));
                BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(k.path("e").asText()));
                fresh.put(k.path("kid").asText(), KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e)));
            }
            keys.clear(); keys.putAll(fresh);
            issuer = discovery.path("issuer").asText(base);
            keysUntil = Instant.now().plus(KEYS_TTL);
        } catch (InvalidTokenException e) { throw e; }
        catch (Exception e) {
            LOG.warnf("No se pudieron obtener las claves de IDENT: %s", e.getMessage());
            if (keys.isEmpty()) throw new InvalidTokenException("IDENT no disponible para validar el ingreso");
        }
    }

    private JsonNode get(String url) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IllegalStateException(url + " -> HTTP " + r.statusCode());
        return mapper.readTree(r.body());
    }

    private JsonNode json(String part) {
        try { return mapper.readTree(Base64.getUrlDecoder().decode(part)); } catch (Exception e) { throw new InvalidTokenException("Token mal formado"); }
    }
}
