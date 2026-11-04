package com.cajamarca.sgi.comando.secelec;

import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class CameraSecretCipherTest {
    static CameraSecretCipher cipher(String key) {
        return cipher(key, null);
    }

    static CameraSecretCipher cipher(String key, String defaultKey) {
        CameraSecretCipher c = new CameraSecretCipher();
        c.secretKey = Optional.ofNullable(key);
        c.defaultSecretKey = Optional.ofNullable(defaultKey);
        return c;
    }

    @Test void blankKeyFallsBackToDefault() {
        String stored = cipher(" ", "llave-fija").encrypt("secreta");
        assertEquals("secreta", cipher(null, "llave-fija").decrypt(stored));
        assertEquals("secreta", cipher("llave-fija", "otra").decrypt(stored), "la variable definida tiene prioridad");
    }

    @Test void roundTripWithRandomIv() {
        CameraSecretCipher c = cipher("llave-de-prueba");
        String a = c.encrypt("Hik#2026"), b = c.encrypt("Hik#2026");
        assertTrue(a.startsWith("v1:"));
        assertNotEquals(a, b, "cada cifrado usa un IV distinto");
        assertFalse(a.contains("Hik#2026"));
        assertEquals("Hik#2026", c.decrypt(a));
    }

    @Test void wrongKeyCannotDecrypt() {
        String stored = cipher("llave-a").encrypt("secreta");
        assertThrows(IllegalStateException.class, () -> cipher("llave-b").decrypt(stored));
    }

    @Test void withoutAnyKeyRefusesToStore() {
        WebApplicationException e = assertThrows(WebApplicationException.class, () -> cipher(" ").encrypt("secreta"));
        assertEquals(503, e.getResponse().getStatus());
    }
}
