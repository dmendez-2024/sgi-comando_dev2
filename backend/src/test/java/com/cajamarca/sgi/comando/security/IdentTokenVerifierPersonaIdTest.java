package com.cajamarca.sgi.comando.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IdentTokenVerifierPersonaIdTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private Long personaId(String claims) throws Exception {
        JsonNode c = mapper.readTree(claims);
        return IdentTokenVerifier.personaId(c);
    }

    @Test
    void identEnviaPersonaIdComoTexto() throws Exception {
        // Token real de IDENT de desarrollo: "persona_id":"6125"
        assertEquals(6125L, personaId("{\"persona_id\":\"6125\"}"));
    }

    @Test
    void tambienAceptaPersonaIdNumerico() throws Exception {
        assertEquals(6125L, personaId("{\"persona_id\":6125}"));
    }

    @Test
    void sinPersonaIdOValorNoNumericoQuedaNull() throws Exception {
        assertNull(personaId("{}"));
        assertNull(personaId("{\"persona_id\":null}"));
        assertNull(personaId("{\"persona_id\":\"\"}"));
        assertNull(personaId("{\"persona_id\":\"abc\"}"));
        assertNull(personaId("{\"persona_id\":\"-5\"}"));
    }
}
