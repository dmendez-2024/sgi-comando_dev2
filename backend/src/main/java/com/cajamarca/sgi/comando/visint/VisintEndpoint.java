package com.cajamarca.sgi.comando.visint;

import com.cajamarca.sgi.comando.interconnections.CoreInterconnectionResolver;
import com.cajamarca.sgi.comando.interconnections.InterconnectionException;
import com.cajamarca.sgi.comando.interconnections.InterconnectionIds;
import com.cajamarca.sgi.comando.interconnections.ResolutionCache;
import com.cajamarca.sgi.comando.interconnections.ResolvedInterconnection;
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

    /**
     * URL de VISINT y de dónde salió. source: CORE, SGI_VISINT_URL o NONE (url null); coreError/coreErrorCode: por qué no
     * se usó CORE. Si respondió CORE: authType y token (credentialRef de su respuesta). environment/instanceCountryId:
     * con qué se le preguntó. El token nunca sale en toString ni en el log.
     */
    public record Resolution(String interconnectionCode, String url, String source, String coreErrorCode, String coreError,
                             String environment, String instanceCountryId, String authType, String token) {
        @Override public String toString() {
            return "Resolution[" + interconnectionCode + " " + source + " " + url + " token=" + (token == null ? "no" : "sí") + "]";
        }
    }

    /** Cada llamada deja en el log la URL usada y su origen: CORE o SGI_VISINT_URL (con el motivo). */
    public String url() {
        Resolution r = resolve(false);
        if (r.url() == null) throw new VisintUnavailableException("VISINT no configurado: " + r.coreError() + " y SGI_VISINT_URL está vacío");
        return r.url();
    }

    /** fresh = true consulta CORE sin la caché (diagnóstico). */
    public Resolution resolve(boolean fresh) {
        String reason, errorCode, environment = resolver == null ? null : resolver.environment(), instance = null;
        if (present(coreResolverUrl) && instances != null) {
            try {
                UUID pe = instances.current();
                instance = pe.toString();
                ResolvedInterconnection r = fresh ? resolver.resolve(code, null, pe) : cache.resolve(code, null, pe, resolver);
                String url = r.url();
                LOG.infof("URL de VISINT desde CORE %s → %s (token %s)", code, url, present(Optional.ofNullable(r.credentialRef)) ? "sí" : "no");
                String token = present(Optional.ofNullable(r.credentialRef)) ? r.credentialRef.trim() : null;
                return new Resolution(code, url, "CORE", null, null, environment, instance, r.authType, token);
            } catch (InterconnectionException e) {
                errorCode = e.code();
                reason = "CORE no resolvió " + code + ": " + e.getMessage();
            } catch (RuntimeException e) {
                errorCode = "CORE_RESOLUTION_FAILED";
                reason = "CORE no resolvió " + code + ": " + e.getMessage();
            }
        } else {
            errorCode = "CORE_RESOLVER_NOT_CONFIGURED";
            reason = "CORE no configurado (SGI_INTERCONNECTIONS_CORE_RESOLVER_URL vacío)";
        }
        if (present(fallbackUrl)) {
            String url = fallbackUrl.get().trim();
            LOG.warnf("URL de VISINT desde SGI_VISINT_URL → %s (%s)", url, reason);
            return new Resolution(code, url, "SGI_VISINT_URL", errorCode, reason, environment, instance, null, null);
        }
        return new Resolution(code, null, "NONE", errorCode, reason, environment, instance, null, null);
    }

    private static boolean present(Optional<String> v) { return v.isPresent() && !v.get().isBlank(); }
}
