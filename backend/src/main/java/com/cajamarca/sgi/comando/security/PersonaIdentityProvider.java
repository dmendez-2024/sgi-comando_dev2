package com.cajamarca.sgi.comando.security;

import com.cajamarca.sgi.comando.assignments.AssignmentRolePolicy;
import com.cajamarca.sgi.comando.common.TenantContext;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.UUID;

/**
 * Identidad de SGI Operador por persona_id, sin token: la misma que daria un token de IDENT aud=SGI_OPR (programa
 * SGI_OPR, persona_id, Instancia PE), con el Rol que corresponde al Cargo del empleado (role_code). El nombre
 * es "persona:&lt;persona_id&gt;": es el que queda como dueno de relevos, bitacoras, patrullas y evidencias.
 */
@ApplicationScoped
public class PersonaIdentityProvider implements IdentityProvider<PersonaAuthenticationRequest> {
    @Inject EntityManager em;
    @Inject TenantContext tenant;
    @Inject AssignmentRolePolicy roles;

    @Override public Class<PersonaAuthenticationRequest> getRequestType() { return PersonaAuthenticationRequest.class; }

    @Override
    public Uni<SecurityIdentity> authenticate(PersonaAuthenticationRequest request, AuthenticationRequestContext context) {
        return context.runBlocking(() -> QuarkusTransaction.requiringNew().call(() -> {
            UUID pe = tenant.instanceCountryId();
            List<?> rows = em.createNativeQuery("select role_code from employee_operational_snapshot where instance_country_id=:tenant and persona_id=:persona")
                .setParameter("tenant", pe).setParameter("persona", request.personaId()).getResultList();
            if (rows.size() != 1) throw new AuthenticationFailedException("Vínculo persona empleado pendiente de configuración");
            // role_code trae el Cargo de DHO ("Agente de Seguridad", "Supervisor de Seguridad CL") o el codigo del Rol.
            String cargo = rows.get(0) == null ? "" : rows.get(0).toString();
            String role = IdentIdentity.OPERATOR_ROLES.stream().filter(r -> roles.matchesRequiredRole(r, cargo)).findFirst()
                .orElseThrow(() -> new AuthenticationFailedException("La persona no es Agente ni Supervisor de Seguridad"));
            return QuarkusSecurityIdentity.builder()
                .setPrincipal(new QuarkusPrincipal("persona:" + request.personaId()))
                .addRole(role)
                .addAttribute(IdentIdentity.SOURCE, Boolean.TRUE)
                .addAttribute(IdentIdentity.PROGRAM, IdentTokenVerifier.PROGRAM_OPERATOR)
                .addAttribute(IdentIdentity.PERSONA_ID, request.personaId())
                .addAttribute(IdentIdentity.INSTANCE_COUNTRY_ID, pe)
                .build();
        }));
    }
}
