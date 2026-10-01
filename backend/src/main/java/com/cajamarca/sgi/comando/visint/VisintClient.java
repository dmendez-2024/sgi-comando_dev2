package com.cajamarca.sgi.comando.visint;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Acceso único a VISINT. Por defecto usa el servicio real (HTTP). El simulado solo funciona si
 * SGI_VISINT_MODE=MOCK y la bandera UAT está activa ("Cero DEMO/mock" en producción).
 */
@ApplicationScoped
public class VisintClient implements VisintPort {
    @Inject MockVisintAdapter mock;
    @Inject HttpVisintAdapter http;
    @ConfigProperty(name="sgi.visint.mode") String mode;
    @ConfigProperty(name="sgi.uat.features-enabled") boolean uatEnabled;

    boolean mockRequested() { return "MOCK".equalsIgnoreCase(mode); }
    public boolean simulated() { return mockRequested() && uatEnabled; }
    public String mode() { return mockRequested() ? "MOCK" : "HTTP"; }

    @Override
    public ReviewResult review(ReviewRequest request) {
        if (mockRequested()) {
            if (!uatEnabled) throw new VisintUnavailableException("VISINT simulado deshabilitado fuera de UAT");
            return mock.review(request);
        }
        return http.review(request);
    }
}
