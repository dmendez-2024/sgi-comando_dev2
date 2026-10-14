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
 * Quien entra con que:
 * <ul>
 *   <li>SGI Operador (Agente y Supervisor de Seguridad) entra solo con IDENT. Su usuario y contrasena de SGI (app_user)
 *       ya no se aceptan, y su token (aud=SGI_OPR) solo sirve para la API del operador y las lecturas del Supervisor.</li>
 *   <li>Con IDENT encendido para la web, la web de SGI: Comando solo entra con IDENT.</li>
 * </ul>
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class IdentAccessFilter implements ContainerRequestFilter {
    @Inject IdentTokenVerifier verifier;
    @Inject SecurityIdentity identity;

    @Override
    public void filter(ContainerRequestContext request) {
        if (identity.isAnonymous()) return;
        String path = request.getUriInfo().getPath();
        if (IdentIdentity.isIdent(identity)) {
            if (IdentIdentity.isOperatorApp(identity) && !OperatorRoutes.allowsOperatorApp(request.getMethod(), path))
                request.abortWith(error(403, "El ingreso de SGI Operador no da acceso a este servicio de SGI: Comando."));
            return;
        }
        if (IdentIdentity.hasOperatorRole(identity))
            request.abortWith(error(401, "SGI Operador entra con IDENT. El usuario y contrasena de SGI ya no se aceptan."));
        else if (verifier.enabled())
            request.abortWith(error(401, "Ingrese con IDENT. El usuario y contrasena de SGI ya no se aceptan."));
    }

    private static Response error(int status, String message) {
        Response.ResponseBuilder r = Response.status(status).type(MediaType.APPLICATION_JSON).entity(Map.of("message", message));
        return status == 401 ? r.header("WWW-Authenticate", "Bearer").build() : r.build();
    }
}
