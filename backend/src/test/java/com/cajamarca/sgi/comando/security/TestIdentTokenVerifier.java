package com.cajamarca.sgi.comando.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Solo pruebas: acepta tokens "test.&lt;claims en base64url&gt;.sig" sin firma ni IDENT. Los claims pasan por las mismas
 * reglas que un token real (audiencia, token_use, vencimiento, persona_id, PE). Los demas tokens van al verificador real.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class TestIdentTokenVerifier extends IdentTokenVerifier {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PREFIX = "test.";

    public static String token(Map<String, Object> claims) {
        try {
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(MAPPER.writeValueAsBytes(claims)) + ".sig";
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @Override
    public Identity verify(String token) {
        if (token == null || !token.startsWith(PREFIX)) return super.verify(token);
        String[] parts = token.split("\\.");
        if (parts.length != 3) throw new InvalidTokenException("Token mal formado");
        JsonNode claims;
        try { claims = MAPPER.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new InvalidTokenException("Token mal formado"); }
        return identity(claims);
    }
}
