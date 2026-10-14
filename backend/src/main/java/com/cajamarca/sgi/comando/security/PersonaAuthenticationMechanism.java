package com.cajamarca.sgi.comando.security;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;

/**
 * SGI Operador sin token de IDENT: en las rutas que consume la app (OperatorRoutes) la persona se indica con su
 * persona_id de DHO, en la cabecera X-Persona-Id o en el parametro personaId. Va despues de IDENT: si llega un token
 * valido, manda el token. Fuera de esas rutas no hace nada.
 */
@ApplicationScoped
public class PersonaAuthenticationMechanism implements HttpAuthenticationMechanism {
    public static final String HEADER = "X-Persona-Id", PARAM = "personaId";

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        if (!OperatorRoutes.allowsOperatorApp(context.request().method().name(), context.normalizedPath())) return Uni.createFrom().nullItem();
        String v = context.request().getHeader(HEADER);
        if (v == null || v.isBlank()) v = context.request().getParam(PARAM);
        if (v == null || v.isBlank()) return Uni.createFrom().nullItem();
        long personaId;
        try { personaId = Long.parseLong(v.trim()); }
        catch (NumberFormatException e) { return Uni.createFrom().failure(new AuthenticationFailedException("persona_id invalido")); }
        return identityProviderManager.authenticate(new PersonaAuthenticationRequest(personaId));
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) { return Uni.createFrom().nullItem(); }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() { return Set.of(PersonaAuthenticationRequest.class); }

    @Override
    public int getPriority() { return 100; }
}
