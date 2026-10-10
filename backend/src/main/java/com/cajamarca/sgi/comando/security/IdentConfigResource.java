package com.cajamarca.sgi.comando.security;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Configuracion publica del login con IDENT para el frontend (sin secretos). La direccion de IDENT sale de CORE. */
@Path("/api/v1/auth/ident-config")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class IdentConfigResource {
    public record IdentConfig(boolean enabled, String identUrl, String clientId) {}

    @Inject IdentTokenVerifier verifier;

    @GET
    public IdentConfig config() {
        if (!verifier.enabled()) return new IdentConfig(false, null, null);
        return new IdentConfig(true, verifier.identPublicUrl(), verifier.clientId());
    }
}
