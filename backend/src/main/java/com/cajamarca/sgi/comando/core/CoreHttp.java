package com.cajamarca.sgi.comando.core;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import java.net.InetAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Llamadas HTTP a CORE: siempre directas (sin proxy) y con logs para diagnosticar conectividad.
 * Java no usa HTTP_PROXY/HTTPS_PROXY/NO_PROXY (curl sí); por eso al arrancar se registra qué proxy ve el entorno.
 */
@ApplicationScoped
public class CoreHttp {
    private static final Logger LOG = Logger.getLogger(CoreHttp.class);

    /** Cliente sin proxy para CORE. */
    public static HttpClient client(Duration connectTimeout) {
        return HttpClient.newBuilder()
            .proxy(HttpClient.Builder.NO_PROXY)
            .connectTimeout(connectTimeout)
            .build();
    }

    /** Envía y registra destino, IP resuelta, estado y duración; en un error, la cadena de causas. */
    public static HttpResponse<String> send(HttpClient client, HttpRequest request, String label) throws Exception {
        URI uri = request.uri();
        long start = System.nanoTime();
        String ips = resolve(uri.getHost());
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            LOG.infof("CORE %s · %s %s → HTTP %d en %d ms · host %s=%s · sin proxy · %s",
                label, request.method(), withoutQuery(uri), response.statusCode(), elapsed(start), uri.getHost(), ips, response.version());
            return response;
        } catch (Exception e) {
            LOG.warnf("CORE %s · %s %s FALLÓ en %d ms (timeout %s) · host %s=%s · sin proxy · causa: %s",
                label, request.method(), withoutQuery(uri), elapsed(start),
                request.timeout().map(Duration::toMillis).map(ms -> ms + " ms").orElse("—"), uri.getHost(), ips, causes(e));
            throw e;
        }
    }

    void onStart(@Observes StartupEvent ev) {
        var config = ConfigProvider.getConfig();
        Map<String, String> env = new TreeMap<>();
        for (String k : List.of("HTTP_PROXY", "HTTPS_PROXY", "NO_PROXY", "http_proxy", "https_proxy", "no_proxy")) {
            String v = System.getenv(k);
            if (v != null) env.put(k, mask(v));
        }
        Map<String, String> props = new TreeMap<>();
        for (String k : List.of("http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort", "http.nonProxyHosts", "java.net.useSystemProxies")) {
            String v = System.getProperty(k);
            if (v != null) props.put(k, mask(v));
        }
        LOG.infof("CORE · llamadas sin proxy. Proxy en variables de entorno (Java no las usa): %s · propiedades Java: %s",
            env.isEmpty() ? "ninguno" : env, props.isEmpty() ? "ninguna" : props);
        for (String key : List.of("sgi.core.catalog-base-url", "sgi.interconnections.core-resolver-url")) {
            config.getOptionalValue(key, String.class).filter(v -> !v.isBlank()).ifPresent(url -> {
                URI uri = URI.create(url.trim());
                String byDefault;
                try { byDefault = String.valueOf(ProxySelector.getDefault().select(uri)); } catch (Exception e) { byDefault = "—"; }
                LOG.infof("CORE · %s=%s · host %s=%s · proxy que Java habría usado sin NO_PROXY: %s", key, withoutQuery(uri), uri.getHost(), resolve(uri.getHost()), byDefault);
            });
        }
    }

    private static String resolve(String host) {
        if (host == null) return "—";
        try {
            return Arrays.stream(InetAddress.getAllByName(host)).map(InetAddress::getHostAddress).collect(Collectors.joining(","));
        } catch (Exception e) {
            return "NO RESUELVE (" + e.getClass().getSimpleName() + ")";
        }
    }

    private static String causes(Throwable e) {
        List<String> chain = new ArrayList<>();
        for (Throwable t = e; t != null && chain.size() < 6; t = t.getCause()) {
            chain.add(t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage()));
            if (t.getCause() == t) break;
        }
        return String.join(" ← ", chain);
    }

    private static long elapsed(long start) { return (System.nanoTime() - start) / 1_000_000; }

    private static String withoutQuery(URI uri) {
        String s = uri.toString();
        int q = s.indexOf('?');
        return q < 0 ? s : s.substring(0, q);
    }

    /** Oculta usuario y clave de un proxy (http://usuario:clave@host:puerto). */
    private static String mask(String value) {
        return value.replaceAll("(?<=://)[^/@\\s]+@", "***@");
    }
}
