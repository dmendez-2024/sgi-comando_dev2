package com.cajamarca.sgi.comando.common;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.UUID;

@ApplicationScoped
public class TenantContext {
    @ConfigProperty(name="sgi.instance-country-id")
    String instanceCountryId;
    public UUID instanceCountryId() { return UUID.fromString(instanceCountryId); }
}
