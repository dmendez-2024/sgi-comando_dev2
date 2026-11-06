package com.cajamarca.sgi.comando.secelec;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Permiso para ver una cámara: Comando firma la ruta del video (live/cam-…) con una fecha de vencimiento y lo pega en la URL
 * (?token=vence.firma). MediaMTX le pregunta a Comando por cada pedido del navegador (ver VideoAuthResource) y solo entrega el
 * video si la firma es de Comando, es de esa misma ruta y no venció. Así solo quien recibió la URL desde Comando ve la cámara.
 */
@ApplicationScoped
public class VideoToken {
    /** Llave para firmar. Vacía: se genera una al arrancar (sirve con un solo backend; con varios, configurar la misma en todos). */
    @ConfigProperty(name = "sgi.video.token-key") Optional<String> configuredKey;
    /** Cuánto sirve la URL. Debe cubrir lo que dura mirar la cámara: HLS vuelve a pedir permiso por cada trozo de video. */
    @ConfigProperty(name = "sgi.video.token-ttl", defaultValue = "PT12H") Duration ttl;

    private byte[] key;

    @PostConstruct
    void init() {
        key = configuredKey.filter(v -> !v.isBlank()).map(v -> v.getBytes(StandardCharsets.UTF_8)).orElseGet(() -> {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            return random;
        });
    }

    /** "vence.firma" para la ruta, válido por {@link #ttl}. */
    public String sign(String path) { return sign(path, Instant.now().plus(ttl)); }

    String sign(String path, Instant expires) {
        long exp = expires.getEpochSecond();
        return exp + "." + mac(path + "|" + exp);
    }

    /** true si el token lo firmó Comando para esta ruta y aún no vence. */
    public boolean valid(String path, String token) { return problem(path, token) == null; }

    /** null si el token sirve; si no, por qué (para el registro de consultas de MediaMTX y el diagnóstico). */
    public String problem(String path, String token) {
        if (path == null) return "consulta sin ruta";
        if (token == null || token.isBlank()) return "sin token";
        int dot = token.indexOf('.');
        if (dot <= 0) return "token con formato inválido";
        long exp;
        try { exp = Long.parseLong(token.substring(0, dot)); } catch (NumberFormatException e) { return "token con formato inválido"; }
        if (Instant.now().getEpochSecond() > exp) return "token vencido";
        byte[] expected = mac(path + "|" + exp).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, token.substring(dot + 1).getBytes(StandardCharsets.US_ASCII))
            ? null : "firma inválida (otra llave u otra ruta)";
    }

    private String mac(String data) {
        try {
            Mac m = Mac.getInstance("HmacSHA256");
            m.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(m.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo firmar la URL de video", e);
        }
    }
}
