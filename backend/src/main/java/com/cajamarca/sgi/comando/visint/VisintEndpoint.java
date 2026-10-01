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
    /** Id (UUID) de la Instancia PE en CORE; temporal hasta que IDENT entregue el id de la empresa. */
    @ConfigProperty(name="sgi.interconnections.instance-country-id") Optional<String> instanceCountryId;
    @ConfigProperty(name="sgi.visint.interconnection-code", defaultValue=InterconnectionIds.VISINT_REVIEW_REQUEST) String code;
    @ConfigProperty(name="sgi.visint.url") Optional<String> fallbackUrl;

    /** Solo SGI_VISINT_URL, sin CORE (pruebas). */
    static VisintEndpoint fixed(Optional<String> url) {
        VisintEndpoint e = new VisintEndpoint();
        e.coreResolverUrl = Optional.empty(); e.instanceCountryId = Optional.empty();
        e.code = InterconnectionIds.VISINT_REVIEW_REQUEST; e.fallbackUrl = url;
        return e;
    }

    public String url() {
        if (present(coreResolverUrl) && present(instanceCountryId)) {
            try {
                return using(cache.resolve(code, null, UUID.fromString(instanceCountryId.get().trim()), resolver).url(), "CORE " + code);
            } catch (RuntimeException e) {
                LOG.warnf("CORE no resolvió %s (%s); se usa SGI_VISINT_URL", code, e.getMessage());
            }
        }
        if (present(fallbackUrl)) return using(fallbackUrl.get().trim(), "SGI_VISINT_URL");
        throw new VisintUnavailableException("VISINT no configurado: CORE no resolvió " + code + " y SGI_VISINT_URL está vacío");
    }

    private volatile String lastSource;

    /** Deja en el log de dónde sale la URL cada vez que cambia (CORE ↔ respaldo). */
    private String using(String url, String source) {
        String now = source + " → " + url;
        if (!now.equals(lastSource)) { lastSource = now; LOG.infof("URL de VISINT desde %s", now); }
        return url;
    }

    private static boolean present(Optional<String> v) { return v.isPresent() && !v.get().isBlank(); }
}
