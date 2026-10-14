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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Convierte el token de IDENT en la identidad de SGI: el Rol (groups) sale del Cargo configurado en IDENT y el nombre
 * de la persona es su correo (lo que SGI guarda como "quien lo hizo"). La validacion consulta IDENT, por eso va fuera
 * del hilo de eventos.
 * <p>Token de SGI Operador (aud=SGI_OPR): sus Roles de IDENT (AGENTE, SUPERVISOR) se traducen a los Roles de SGI: Comando
 * y el nombre es el identificador de IDENT (sub), que no cambia aunque cambie el correo: es el que se guarda como dueno
 * de relevos, bitacoras, patrullas y evidencias.
 */
@ApplicationScoped
public class IdentIdentityProvider implements IdentityProvider<TokenAuthenticationRequest> {
    /** Roles de IDENT para SGI_OPR -> Roles de SGI: Comando. Un Rol que no este aqui no da acceso. */
    static final Map<String, String> OPERATOR_ROLES = Map.of("AGENTE", "AGENTE_SEGURIDAD", "SUPERVISOR", "SUPERVISOR_SEGURIDAD");

    @Inject IdentTokenVerifier verifier;
    @Inject InstanceCountrySource instances;

    @Override public Class<TokenAuthenticationRequest> getRequestType() { return TokenAuthenticationRequest.class; }

    @Override
    public Uni<SecurityIdentity> authenticate(TokenAuthenticationRequest request, AuthenticationRequestContext context) {
        return context.runBlocking(() -> {
            IdentTokenVerifier.Identity id;
            try { id = verifier.verify(request.getToken().getToken()); }
            catch (IdentTokenVerifier.InvalidTokenException e) { throw new AuthenticationFailedException(e.getMessage()); }
            boolean operatorApp = IdentTokenVerifier.PROGRAM_OPERATOR.equals(id.program());
            if (operatorApp && (id.subject() == null || id.subject().isBlank())) throw new AuthenticationFailedException("El token de IDENT no trae sub");
            Set<String> roles = new HashSet<>();
            for (String g : id.groups()) {
                if (!operatorApp) roles.add(g);
                else if (OPERATOR_ROLES.containsKey(g)) roles.add(OPERATOR_ROLES.get(g));
            }
            String name = operatorApp ? id.subject() : id.email() != null ? id.email() : id.subject();
            QuarkusSecurityIdentity.Builder b = QuarkusSecurityIdentity.builder()
                    .setPrincipal(new QuarkusPrincipal(name))
                    .addRoles(roles)
                    .addAttribute(IdentIdentity.SOURCE, Boolean.TRUE)
                    .addAttribute(IdentIdentity.PROGRAM, id.program())
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
