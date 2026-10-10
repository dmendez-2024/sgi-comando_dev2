package com.cajamarca.sgi.comando.security;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/**
 * Con IDENT encendido la web de SGI: Comando solo entra con IDENT. El usuario y contrasena de SGI (app_user) quedan
 * unicamente para la app SGI Operador en /api/v1/operator/** hasta que esa app tenga su login con IDENT.
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class OperatorBasicOnlyFilter implements ContainerRequestFilter {
    @Inject IdentTokenVerifier verifier;
    @Inject SecurityIdentity identity;

    @Override
    public void filter(ContainerRequestContext request) {
        if (!verifier.enabled() || identity.isAnonymous() || IdentIdentity.isIdent(identity)) return;
        String path = request.getUriInfo().getPath();
        if (path.startsWith("/api/v1/operator") || path.startsWith("api/v1/operator")) return;
        request.abortWith(Response.status(401).type(MediaType.APPLICATION_JSON)
                .entity(Map.of("message", "Ingrese con IDENT. El usuario y contrasena de SGI solo se aceptan para SGI Operador."))
                .header("WWW-Authenticate", "Bearer").build());
    }
}
