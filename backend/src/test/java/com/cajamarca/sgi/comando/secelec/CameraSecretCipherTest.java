package com.cajamarca.sgi.comando.secelec;

import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class CameraSecretCipherTest {
    static CameraSecretCipher cipher(String key) {
        CameraSecretCipher c = new CameraSecretCipher();
        c.secretKey = Optional.ofNullable(key);
        return c;
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

    @Test void withoutKeyRefusesToStore() {
        WebApplicationException e = assertThrows(WebApplicationException.class, () -> cipher(" ").encrypt("secreta"));
        assertEquals(503, e.getResponse().getStatus());
    }
}
