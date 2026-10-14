package com.cajamarca.sgi.comando.security;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.UUID;

/** Solo pruebas: la Instancia PE de la base de pruebas pertenece a SGI: Comando sin consultar CORE. */
@Alternative
@Priority(1)
@ApplicationScoped
public class TestInstanceCountrySource extends InstanceCountrySource {
    static final UUID TEST_PE = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Override
    public boolean belongs(UUID instanceCountryId) {
        return TEST_PE.equals(instanceCountryId) || super.belongs(instanceCountryId);
    }
}
