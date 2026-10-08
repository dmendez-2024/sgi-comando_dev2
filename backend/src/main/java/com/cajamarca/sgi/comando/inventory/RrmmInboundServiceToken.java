package com.cajamarca.sgi.comando.inventory;

import com.cajamarca.sgi.comando.interconnections.CredentialRefResolver;
import com.cajamarca.sgi.comando.interconnections.InterconnectionException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

/** Valida la identidad técnica de SIC:RRMM mediante la credential_ref resuelta en tiempo de ejecución. */
@ApplicationScoped
public class RrmmInboundServiceToken {
    @ConfigProperty(name="sgi.rrmm.inbound.credential-ref") Optional<String> credentialRef;
    @Inject CredentialRefResolver credentials;

    public void assertAuthorized(String authorization) {
        String ref=credentialRef.orElse("").trim();
        if(ref.isEmpty()) throw new ServiceUnavailableException("La credential_ref de SIC:RRMM no está configurada.");
        final String expected;
        try { expected=credentials.resolve(ref); }
        catch(InterconnectionException error) { throw new ServiceUnavailableException("La credencial de SIC:RRMM no está disponible."); }
        String supplied=authorization!=null&&authorization.startsWith("Bearer ")?authorization.substring(7).trim():"";
        if(!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))
            throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED)
                .header("WWW-Authenticate","Bearer").entity(Map.of("error","Credencial de SIC:RRMM inválida."))
                .type(MediaType.APPLICATION_JSON).build());
    }
}
