package com.cajamarca.sgi.comando.common;

import com.cajamarca.sgi.comando.visint.VisintClient;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Banderas que el frontend necesita para ocultar herramientas UAT (Simulador de Agente, VISINT simulado) fuera de UAT. */
@Path("/api/features") @Authenticated @Produces(MediaType.APPLICATION_JSON)
public class FeaturesResource {
    public record Features(boolean uatTools, String visintMode, boolean visintSimulated) {}

    @Inject VisintClient visint;
    @ConfigProperty(name="sgi.uat.features-enabled") boolean uatEnabled;

    @GET
    public Features get() { return new Features(uatEnabled, visint.mode(), visint.simulated()); }
}
