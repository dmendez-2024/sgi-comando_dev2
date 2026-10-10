package com.cajamarca.sgi.comando.visint;

import com.cajamarca.sgi.comando.interconnections.CoreInterconnectionResolver;
import com.cajamarca.sgi.comando.interconnections.InterconnectionIds;
import com.cajamarca.sgi.comando.interconnections.ResolutionCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import java.util.Optional;
import java.util.UUID;

/**
 * URL de VISINT. Primero se pide a CORE (Interconexión SGI_COM_VISINT_0001_v002 de la Instancia PE y ambiente configurados;
 * la respuesta queda en caché con su TTL y margen de stale). Si CORE no está configurado, no responde o no tiene binding,
 * se usa SGI_VISINT_URL.
 */
@ApplicationScoped
public class VisintEndpoint {
    private static final Logger LOG = Logger.getLogger(VisintEndpoint.class);

    @Inject CoreInterconnectionResolver resolver;
    @Inject ResolutionCache cache;
    @ConfigProperty(name="sgi.interconnections.core-resolver-url") Optional<String> coreResolverUrl;
    /** Instancia PE: la del token de IDENT o, sin persona, la que diga CORE (sin ID configurado, DEC-46). */
    @Inject com.cajamarca.sgi.comando.security.InstanceCountrySource instances;
    @ConfigProperty(name="sgi.visint.interconnection-code", defaultValue=InterconnectionIds.VISINT_REVIEW_REQUEST) String code;
    @ConfigProperty(name="sgi.visint.url") Optional<String> fallbackUrl;

    /** Solo SGI_VISINT_URL, sin CORE (pruebas). */
    static VisintEndpoint fixed(Optional<String> url) {
        VisintEndpoint e = new VisintEndpoint();
        e.coreResolverUrl = Optional.empty(); e.instances = null;
        e.code = InterconnectionIds.VISINT_REVIEW_REQUEST; e.fallbackUrl = url;
        return e;
    }

    /** Cada llamada deja en el log la URL usada y su origen: CORE o SGI_VISINT_URL (con el motivo). */
    public String url() {
        String reason;
        if (present(coreResolverUrl) && instances != null) {
            try {
                String url = cache.resolve(code, null, instances.current(), resolver).url();
                LOG.infof("URL de VISINT desde CORE %s → %s", code, url);
                return url;
            } catch (RuntimeException e) {
                reason = "CORE no resolvió " + code + ": " + e.getMessage();
            }
        } else {
            reason = "CORE no configurado (SGI_INTERCONNECTIONS_CORE_RESOLVER_URL vacío)";
        }
        if (present(fallbackUrl)) {
            String url = fallbackUrl.get().trim();
            LOG.warnf("URL de VISINT desde SGI_VISINT_URL → %s (%s)", url, reason);
            return url;
        }
        throw new VisintUnavailableException("VISINT no configurado: " + reason + " y SGI_VISINT_URL está vacío");
    }

    private static boolean present(Optional<String> v) { return v.isPresent() && !v.get().isBlank(); }
}
