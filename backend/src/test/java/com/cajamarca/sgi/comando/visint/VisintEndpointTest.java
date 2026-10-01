package com.cajamarca.sgi.comando.visint;

import com.cajamarca.sgi.comando.interconnections.*;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** URL de VISINT: CORE (SGI_COM_VISINT_0001_v002) primero y SGI_VISINT_URL como respaldo. */
class VisintEndpointTest {
    static final String ENV_URL = "http://visint-env:8010/v1/evidence/validate";
    static final String INSTANCE = "398233d2-293a-4709-ac30-b74c4269e22d";
    // Respuesta real del resolver de CORE (ResolutionView).
    static final String CORE_JSON = """
        {"accessMode":"EXECUTE","authType":"SERVICE_TOKEN","basePath":"/v1/evidence/validate","code":"SGI_COM_VISINT_0001_v002",
         "contractName":"SGI_COM_VISINT_0001","contractVersion":"v1","credentialRef":null,"environment":"LOCAL","host":"181.39.84.138",
         "httpMethod":"POST","instanceCountryCode":"ECU-CM","instanceCountryId":"398233d2-293a-4709-ac30-b74c4269e22d","port":8010,
         "protocol":"HTTP","referenceCode":"SGI_COM_VISINT_0001","resolvedUrl":"http://181.39.84.138:8010/v1/evidence/validate",
         "resourceScope":"visint:evidencias:execute","retries":3,"timeoutMs":5000,"versionNo":2}""";

    @Test void coreResolutionViewIsNormalized() throws Exception {
        ResolvedInterconnection r = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .readValue(CORE_JSON, ResolvedInterconnection.class);
        r.normalize();
        assertEquals("http://181.39.84.138:8010", r.baseUrl);
        assertEquals("/v1/evidence/validate", r.path);
        assertEquals("POST", r.method);
        assertEquals("SGI_COM_VISINT_0001_v002", r.interconnectionId);
        assertEquals(3, r.maxRetries);
        assertEquals("http://181.39.84.138:8010/v1/evidence/validate", r.url());
    }

    @Test void nonHttpResolvedUrlIsRejected() {
        ResolvedInterconnection r = new ResolvedInterconnection();
        r.resolvedUrl = "ftp://x/y";
        assertThrows(InterconnectionException.class, r::normalize);
    }

    @Test void usesTheUrlResolvedByCore() {
        AtomicInteger calls = new AtomicInteger();
        VisintEndpoint e = endpoint((id, iface, instance) -> {
            calls.incrementAndGet();
            assertEquals("SGI_COM_VISINT_0001_v002", id);
            assertNull(iface);
            assertEquals(UUID.fromString(INSTANCE), instance);
            ResolvedInterconnection r = new ResolvedInterconnection();
            r.resolvedUrl = "http://core-visint:8010/v1/evidence/validate"; r.httpMethod = "POST"; r.normalize();
            return r;
        });
        assertEquals("http://core-visint:8010/v1/evidence/validate", e.url());
        assertEquals(1, calls.get());
    }

    @Test void fallsBackToEnvWhenCoreFails() {
        VisintEndpoint e = endpoint((id, iface, instance) -> { throw new InterconnectionException("CORE_RESOLUTION_HTTP_404", "sin binding"); });
        assertEquals(ENV_URL, e.url());
    }

    @Test void fallsBackToEnvWhenInstanceIdIsInvalid() {
        VisintEndpoint e = endpoint((id, iface, instance) -> fail("no debe llamar a CORE"));
        e.instanceCountryId = Optional.of("no-es-uuid");
        assertEquals(ENV_URL, e.url());
    }

    @Test void withoutCoreConfigUsesEnv() {
        VisintEndpoint e = endpoint((id, iface, instance) -> fail("no debe llamar a CORE"));
        e.coreResolverUrl = Optional.empty();
        assertEquals(ENV_URL, e.url());
    }

    @Test void withoutCoreAndWithoutEnvIsUnavailable() {
        VisintEndpoint e = endpoint((id, iface, instance) -> { throw new InterconnectionException("CORE_RESOLUTION_FAILED", "caído"); });
        e.fallbackUrl = Optional.empty();
        var ex = assertThrows(VisintUnavailableException.class, e::url);
        assertTrue(ex.getMessage().contains("no configurado"));
    }

    interface Core { ResolvedInterconnection resolve(String id, String iface, UUID instance); }

    static VisintEndpoint endpoint(Core core) {
        VisintEndpoint e = VisintEndpoint.fixed(Optional.of(ENV_URL));
        e.coreResolverUrl = Optional.of("http://core:5173");
        e.instanceCountryId = Optional.of(INSTANCE);
        e.resolver = new CoreInterconnectionResolver() {
            @Override public ResolvedInterconnection resolve(String id, String iface, UUID instance) { return core.resolve(id, iface, instance); }
        };
        e.cache = new ResolutionCache() {
            @Override public ResolvedInterconnection resolve(String id, String iface, UUID instance, CoreInterconnectionResolver r) { return r.resolve(id, iface, instance); }
        };
        return e;
    }
}
