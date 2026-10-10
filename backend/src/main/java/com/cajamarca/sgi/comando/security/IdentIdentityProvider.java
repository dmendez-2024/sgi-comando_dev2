package com.cajamarca.sgi.comando.security;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Convierte el token de IDENT en la identidad de SGI: el Rol (groups) sale del Cargo configurado en IDENT y el nombre
 * de la persona es su correo (lo que SGI guarda como "quien lo hizo"). La validacion consulta IDENT, por eso va fuera
 * del hilo de eventos.
 */
@ApplicationScoped
public class IdentIdentityProvider implements IdentityProvider<TokenAuthenticationRequest> {
    @Inject IdentTokenVerifier verifier;
    @Inject InstanceCountrySource instances;

    @Override public Class<TokenAuthenticationRequest> getRequestType() { return TokenAuthenticationRequest.class; }

    @Override
    public Uni<SecurityIdentity> authenticate(TokenAuthenticationRequest request, AuthenticationRequestContext context) {
        return context.runBlocking(() -> {
            IdentTokenVerifier.Identity id;
            try { id = verifier.verify(request.getToken().getToken()); }
            catch (IdentTokenVerifier.InvalidTokenException e) { throw new AuthenticationFailedException(e.getMessage()); }
            QuarkusSecurityIdentity.Builder b = QuarkusSecurityIdentity.builder()
                    .setPrincipal(new QuarkusPrincipal(id.email() != null ? id.email() : id.subject()))
                    .addRoles(new java.util.HashSet<>(id.groups()))
                    .addAttribute(IdentIdentity.SOURCE, Boolean.TRUE)
                    .addAttribute(IdentIdentity.IDENTITY_ID, id.subject());
            if (id.personaId() != null) b.addAttribute(IdentIdentity.PERSONA_ID, id.personaId());
            // El PE de SGI sale del token: debe ser una Instancia PE donde esta SGI: Comando segun CORE.
            if (id.instanceCountryId() == null) throw new AuthenticationFailedException("El token de IDENT no trae la Instancia PE");
            if (!instances.belongs(id.instanceCountryId())) throw new AuthenticationFailedException("SGI: Comando no esta habilitado en la Instancia PE del token");
            b.addAttribute(IdentIdentity.INSTANCE_COUNTRY_ID, id.instanceCountryId());
            return b.build();
        });
    }
}
