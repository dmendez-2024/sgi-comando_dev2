package com.cajamarca.sgi.comando.ident;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Lee secretos de SGI: Comando desde OpenBao (KV v2) con su propio AppRole (role_id/secret_id en archivos, CA propia).
 * Referencia: vault:kv/data/<ruta> (campo "value"), igual que credential_ref de IDENT. Nunca registra el secreto.
 */
@ApplicationScoped
public class OpenBaoSecretReader {
    @ConfigProperty(name = "sgi.vault.addr") Optional<String> addr;
    @ConfigProperty(name = "sgi.vault.ca-cert-file") Optional<String> caFile;
    @ConfigProperty(name = "sgi.vault.role-id-file") Optional<String> roleIdFile;
    @ConfigProperty(name = "sgi.vault.secret-id-file") Optional<String> secretIdFile;
    @ConfigProperty(name = "sgi.vault.timeout-ms", defaultValue = "5000") long timeoutMs;

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpClient http;
    private String token;
    private Instant tokenUntil = Instant.EPOCH;

    public synchronized String read(String ref) throws IOException, InterruptedException {
        if (ref == null || !ref.startsWith("vault:")) throw new IllegalStateException("Referencia de secreto invalida (se espera vault:kv/data/...)");
        HttpResponse<String> r = send(HttpRequest.newBuilder(uri("/v1/" + ref.substring(6))).header("X-Vault-Token", login()).GET());
        if (r.statusCode() == 403) { tokenUntil = Instant.EPOCH; r = send(HttpRequest.newBuilder(uri("/v1/" + ref.substring(6))).header("X-Vault-Token", login()).GET()); }
        if (r.statusCode() != 200) throw new IOException("OpenBao respondio HTTP " + r.statusCode() + " al leer " + ref);
        JsonNode v = mapper.readTree(r.body()).path("data").path("data").path("value");
        if (!v.isTextual() || v.asText().isEmpty()) throw new IOException("El secreto " + ref + " no tiene el campo value");
        return v.asText();
    }

    private String login() throws IOException, InterruptedException {
        if (token != null && Instant.now().isBefore(tokenUntil)) return token;
        String body = mapper.createObjectNode().put("role_id", file(roleIdFile, "sgi.vault.role-id-file"))
                .put("secret_id", file(secretIdFile, "sgi.vault.secret-id-file")).toString();
        HttpResponse<String> r = send(HttpRequest.newBuilder(uri("/v1/auth/approle/login")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
        if (r.statusCode() != 200) throw new IOException("OpenBao: login AppRole de SGI_COM rechazado (HTTP " + r.statusCode() + ")");
        JsonNode auth = mapper.readTree(r.body()).path("auth");
        token = auth.path("client_token").asText();
        tokenUntil = Instant.now().plusSeconds(Math.max(30, auth.path("lease_duration").asLong(600) * 2 / 3));
        return token;
    }

    private HttpResponse<String> send(HttpRequest.Builder b) throws IOException, InterruptedException {
        return client().send(b.timeout(Duration.ofMillis(timeoutMs)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create(addr.filter(a -> !a.isBlank()).orElseThrow(() -> new IllegalStateException("sgi.vault.addr no configurado")).replaceAll("/+$", "") + path);
    }

    private HttpClient client() throws IOException {
        if (http != null) return http;
        HttpClient.Builder b = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs));
        if (caFile.isPresent() && !caFile.get().isBlank()) {
            try (InputStream in = Files.newInputStream(Path.of(caFile.get()))) {
                KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                ks.load(null, null);
                int i = 0;
                for (var c : CertificateFactory.getInstance("X.509").generateCertificates(in)) ks.setCertificateEntry("ca" + i++, (X509Certificate) c);
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ks);
                SSLContext ssl = SSLContext.getInstance("TLS");
                ssl.init(null, tmf.getTrustManagers(), null);
                b.sslContext(ssl);
            } catch (IOException e) { throw e; } catch (Exception e) { throw new IOException("CA de OpenBao invalida: " + e.getMessage(), e); }
        }
        return http = b.build();
    }

    private static String file(Optional<String> p, String key) throws IOException {
        return Files.readString(Path.of(p.filter(v -> !v.isBlank()).orElseThrow(() -> new IllegalStateException(key + " no configurado"))), StandardCharsets.UTF_8).trim();
    }
}
