package com.cajamarca.sgi.comando.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdentIdentityProviderRolesTest {
    @Test
    void gerenciaGeneralConservaSuRolSinRecibirPresidencia() {
        assertEquals(Set.of("GERENCIA_GENERAL"),
                IdentIdentityProvider.comandoRoles(Set.of("GERENCIA_GENERAL")));
        assertEquals(Set.of("PRESIDENTE"),
                IdentIdentityProvider.comandoRoles(Set.of("PRESIDENTE")));
        assertEquals(Set.of("DIRECTOR_NACIONAL"),
                IdentIdentityProvider.comandoRoles(Set.of("DIRECTOR_NACIONAL")));
    }
}
