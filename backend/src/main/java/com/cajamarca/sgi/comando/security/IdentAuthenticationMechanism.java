package com.cajamarca.sgi.comando.security;

import io.quarkus.security.credential.TokenCredential;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;

/**
 * Ingreso con IDENT: "Authorization: Bearer <token de IDENT>". Si el token no es valido la peticion queda anonima y
 * los recursos protegidos responden 401 (el frontend vuelve a pedir login a IDENT). Los tokens fijos de los servicios
 * entrantes (no son JWT) no pasan por aqui. Con IDENT encendido el desafio es Bearer, no Basic.
 */
@ApplicationScoped
public class IdentAuthenticationMechanism implements HttpAuthenticationMechanism {
    @Inject IdentTokenVerifier verifier;

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        if (!verifier.enabled()) return Uni.createFrom().nullItem();
        String h = context.request().getHeader("Authorization");
        if (h == null || !h.regionMatches(true, 0, "Bearer ", 0, 7)) return Uni.createFrom().nullItem();
        String token = h.substring(7).trim();
        if (token.chars().filter(c -> c == '.').count() != 2) return Uni.createFrom().nullItem();
        return identityProviderManager.authenticate(new TokenAuthenticationRequest(new TokenCredential(token, "bearer")))
                .onFailure().recoverWithNull();
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return verifier.enabled() ? Uni.createFrom().item(new ChallengeData(401, "WWW-Authenticate", "Bearer")) : Uni.createFrom().nullItem();
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() { return Set.of(TokenAuthenticationRequest.class); }

    @Override
    public int getPriority() { return 3000; }
}
