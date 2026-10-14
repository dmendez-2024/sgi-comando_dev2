package com.cajamarca.sgi.comando.security;

import io.quarkus.security.identity.SecurityIdentity;

/** Datos de la persona que entro con IDENT, guardados como atributos de la identidad de Quarkus. */
public final class IdentIdentity {
    public static final String SOURCE = "ident";
    public static final String PERSONA_ID = "persona_id";
    public static final String IDENTITY_ID = "identity_id";
    /** Instancia PE del ingreso (claim active_instance_country_id del token de IDENT, DEC-46). */
    public static final String INSTANCE_COUNTRY_ID = "instance_country_id";
    /** Programa del token: SGI_COM (web) o SGI_OPR (app SGI Operador). */
    public static final String PROGRAM = "program";
    /** Roles de SGI Operador: entran solo con IDENT, nunca con usuario y contrasena de SGI. */
    public static final java.util.Set<String> OPERATOR_ROLES = java.util.Set.of("AGENTE_SEGURIDAD", "SUPERVISOR_SEGURIDAD");

    private IdentIdentity() {}

    /** true si la persona entro con el token de IDENT (no con usuario y contrasena de SGI). */
    public static boolean isIdent(SecurityIdentity identity) {
        return identity != null && !identity.isAnonymous() && Boolean.TRUE.equals(identity.getAttribute(SOURCE));
    }

    /** true si la persona entro con el token de la app SGI Operador (aud=SGI_OPR). */
    public static boolean isOperatorApp(SecurityIdentity identity) {
        return isIdent(identity) && IdentTokenVerifier.PROGRAM_OPERATOR.equals(identity.getAttribute(PROGRAM));
    }

    /** true si la identidad tiene un Rol de SGI Operador (Agente o Supervisor de Seguridad). */
    public static boolean hasOperatorRole(SecurityIdentity identity) {
        return identity != null && OPERATOR_ROLES.stream().anyMatch(identity::hasRole);
    }

    /** persona_id de DHO que envia IDENT en el token; null mientras IDENT no lo tenga registrado. */
    public static Long personaId(SecurityIdentity identity) {
        Object v = identity == null ? null : identity.getAttribute(PERSONA_ID);
        return v instanceof Long l ? l : null;
    }

    /** Instancia PE con la que la persona entro (viene en su token de IDENT); null si no entro con IDENT. */
    public static java.util.UUID instanceCountryId(SecurityIdentity identity) {
        Object v = identity == null || identity.isAnonymous() ? null : identity.getAttribute(INSTANCE_COUNTRY_ID);
        return v instanceof java.util.UUID u ? u : null;
    }
}
