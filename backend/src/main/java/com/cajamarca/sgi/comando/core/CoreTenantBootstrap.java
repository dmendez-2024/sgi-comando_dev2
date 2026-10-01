package com.cajamarca.sgi.comando.core;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Resuelve al iniciar el identificador canónico Instancia-País. La caída
 * temporal de CORE no impide levantar SGI: Comando: se conserva el último
 * contexto persistido y el siguiente refresco de Territorio o Compañías
 * vuelve a intentar la reconciliación.
 */
@ApplicationScoped
public class CoreTenantBootstrap {
    private static final Logger LOG = Logger.getLogger(CoreTenantBootstrap.class);

    @Inject CoreCatalogService coreCatalogService;

    void onStart(@Observes StartupEvent ignored) {
        try {
            coreCatalogService.synchronizeTenantContext();
        } catch (RuntimeException e) {
            LOG.warn("No se pudo reconciliar Instancia-País con CORE al iniciar; se conserva el último contexto local.", e);
        }
    }
}
