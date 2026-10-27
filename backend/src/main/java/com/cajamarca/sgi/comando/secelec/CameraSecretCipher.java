package com.cajamarca.sgi.comando.secelec;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Cifra las contraseñas de las cámaras con AES-256-GCM. La llave sale de sgi.secelec.secret-key (SGI_SECELEC_SECRET_KEY);
 * si viene vacía o no está, de la llave fija sgi.secelec.default-secret-key.
 * Formato guardado: "v1:" + base64(iv(12) || texto cifrado+tag). Si se adopta OpenBao, solo cambia esta clase.
 */
@ApplicationScoped
public class CameraSecretCipher {
    private static final String PREFIX = "v1:";
    private static final SecureRandom RANDOM = new SecureRandom();

    @ConfigProperty(name = "sgi.secelec.secret-key") Optional<String> secretKey;
    /** Llave fija del repositorio: se usa cuando el despliegue no define SGI_SECELEC_SECRET_KEY (o la pasa vacía). */
    @ConfigProperty(name = "sgi.secelec.default-secret-key") Optional<String> defaultSecretKey;

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar la credencial", e);
        }
    }

    /** "Probar conexión" con la contraseña guardada y, más adelante, el servidor de video. */
    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) throw new IllegalArgumentException("Credencial con formato desconocido");
        try {
            byte[] data = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, data, 0, 12));
            return new String(cipher.doFinal(data, 12, data.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo descifrar la credencial", e);
        }
    }

    private SecretKeySpec key() throws GeneralSecurityException {
        String value = secretKey.map(String::trim).filter(s -> !s.isEmpty())
            .or(() -> defaultSecretKey.map(String::trim).filter(s -> !s.isEmpty())).orElseThrow(() -> new WebApplicationException(
            "Falta configurar SGI_SECELEC_SECRET_KEY para guardar credenciales de cámaras.", Response.Status.SERVICE_UNAVAILABLE));
        return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)), "AES");
    }
}
