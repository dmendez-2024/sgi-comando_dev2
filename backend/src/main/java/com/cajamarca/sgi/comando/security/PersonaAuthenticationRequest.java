package com.cajamarca.sgi.comando.security;

import io.quarkus.security.identity.request.BaseAuthenticationRequest;

/** SGI Operador sin token: la persona se indica con su persona_id de DHO (X-Persona-Id o ?personaId=). */
public final class PersonaAuthenticationRequest extends BaseAuthenticationRequest {
    private final long personaId;

    public PersonaAuthenticationRequest(long personaId) { this.personaId = personaId; }

    public long personaId() { return personaId; }
}
