package com.cajamarca.sgi.comando.smc;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.ident.IdentServiceTokens;
import com.cajamarca.sgi.comando.ident.OpenBaoSecretReader;
import com.cajamarca.sgi.comando.interconnections.*;
import com.cajamarca.sgi.comando.security.IdentTokenVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

/** HTTP local con resolver, AppRole/KV, adquisición M2M y ejecutor reales. No usa servicios reales. */
class SmcOpenBaoTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings={"BEARER","OAUTH2_CLIENT_CREDENTIALS"})
    void obtainsCredentialFromOpenBaoAndCallsSmc(String authType) throws Exception {
        String key="sgi.m2m.client-id.SMC", previous=System.getProperty(key);
        System.setProperty(key,"sgi-com-to-smc-local-test");
        try (Harness h=new Harness(directory,authType,"vault:kv/data/local-test/smc")) {
            var result=h.adapter.send(h.pe,h.body());
            assertEquals(201,result.statusCode);
            assertEquals(1,h.logins.get());assertEquals(1,h.secretReads.get());assertEquals(1,h.smcCalls.get());
            assertEquals("local-bao-session",h.vaultToken.get());
            assertEquals(authType.equals("BEARER")?"Bearer local-secret-value":"Bearer local-ident-access",h.smcAuth.get());
            assertEquals("SGI_COM_SMC__0001_v001",h.smcConnection.get());
            assertEquals("stable-local-event",h.idempotency.get());
            if(authType.equals("OAUTH2_CLIENT_CREDENTIALS")) {
                assertEquals(1,h.tokenCalls.get());
                assertTrue(h.tokenForm.get().contains("client_id=sgi-com-to-smc-local-test"));
                assertTrue(h.tokenForm.get().contains("client_secret=local-secret-value"));
            } else assertEquals(0,h.tokenCalls.get());
        } finally {
            if(previous==null)System.clearProperty(key);else System.setProperty(key,previous);
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"plain-env-reference",""})
    void rejectsNonVaultCredentialsBeforeContactingSmc(String ref) throws Exception {
        try(Harness h=new Harness(directory,"BEARER",ref)) {
            var error=assertThrows(InterconnectionException.class,()->h.adapter.send(h.pe,h.body()));
            assertEquals("OPENBAO_CREDENTIAL_REQUIRED",error.code());
            assertEquals(0,h.logins.get());assertEquals(0,h.smcCalls.get());
        }
    }

    @Test void refusesAnonymousBindingForSmcButPreservesOtherIntegrations() throws Exception {
        try(Harness h=new Harness(directory,"NONE","")) {
            assertEquals("OPENBAO_CREDENTIAL_REQUIRED",assertThrows(InterconnectionException.class,
                ()->h.adapter.send(h.pe,h.body())).code());
            assertEquals(0,h.smcCalls.get());
            // La política se activa solo para la llamada SMC; no cambia otros adaptadores.
            IntegrationRequest request=new IntegrationRequest();request.body=h.body();
            assertEquals(201,h.executor.execute("SGI_COM_SMC__0001_v001","SGI_COM_SMC_0001_IF01",request).statusCode);
            assertEquals(1,h.smcCalls.get());assertEquals(0,h.secretReads.get());
        }
    }

    @Test void openBaoDeniedDoesNotSendAnyFactOrExposeSecret() throws Exception {
        try(Harness h=new Harness(directory,"BEARER","vault:kv/data/local-test/smc")) {
            h.secretStatus.set(403);
            var error=assertThrows(InterconnectionException.class,()->h.adapter.send(h.pe,h.body()));
            assertEquals("CREDENTIAL_NOT_AVAILABLE",error.code());
            assertEquals(0,h.smcCalls.get());assertEquals(2,h.secretReads.get());
            assertFalse(error.getMessage().contains("local-secret-value"));
        }
    }

    static class Harness implements AutoCloseable {
        final UUID pe=UUID.randomUUID();
        final ObjectMapper json=new ObjectMapper();
        final HttpServer server;
        final SmcAdapter adapter=new SmcAdapter();
        final GenericInterconnectionExecutor executor=new GenericInterconnectionExecutor();
        final AtomicInteger logins=new AtomicInteger(),secretReads=new AtomicInteger(),smcCalls=new AtomicInteger(),tokenCalls=new AtomicInteger(),secretStatus=new AtomicInteger(200);
        final AtomicReference<String> vaultToken=new AtomicReference<>(),smcAuth=new AtomicReference<>(),smcConnection=new AtomicReference<>(),idempotency=new AtomicReference<>(),tokenForm=new AtomicReference<>();

        Harness(Path files,String authType,String ref) throws Exception {
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            server.createContext("/core/api/v1/interconnections/",e->reply(e,200,json.writeValueAsString(Map.of(
                "interconnectionId","SGI_COM_SMC__0001_v001","interfaceId","SGI_COM_SMC_0001_IF01",
                "baseUrl",base,"path","/api/v1/operational-facts","method","POST","protocol","HTTP",
                "authType",authType,"credentialRef",ref,"maxRetries",0))));
            server.createContext("/v1/auth/approle/login",e->{logins.incrementAndGet();e.getRequestBody().readAllBytes();
                reply(e,200,"{\"auth\":{\"client_token\":\"local-bao-session\",\"lease_duration\":600}}");});
            server.createContext("/v1/kv/data/local-test/smc",e->{secretReads.incrementAndGet();vaultToken.set(e.getRequestHeaders().getFirst("X-Vault-Token"));
                reply(e,secretStatus.get(),secretStatus.get()==200?"{\"data\":{\"data\":{\"value\":\"local-secret-value\"}}}":"{\"errors\":[\"permission denied\"]}");});
            server.createContext("/.well-known/openid-configuration",e->reply(e,200,json.writeValueAsString(Map.of("token_endpoint",base+"/oauth2/token"))));
            server.createContext("/oauth2/token",e->{tokenCalls.incrementAndGet();tokenForm.set(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
                reply(e,200,"{\"access_token\":\"local-ident-access\",\"expires_in\":600}");});
            server.createContext("/api/v1/operational-facts",e->{smcCalls.incrementAndGet();smcAuth.set(e.getRequestHeaders().getFirst("Authorization"));
                smcConnection.set(e.getRequestHeaders().getFirst("X-Interconnection-Id"));idempotency.set(e.getRequestHeaders().getFirst("Idempotency-Key"));
                e.getRequestBody().readAllBytes();reply(e,201,"{\"accepted\":true,\"smcFactId\":\"1024\"}");});

            Path role=files.resolve("role_id"),secret=files.resolve("secret_id");
            Files.writeString(role,"local-role");Files.writeString(secret,"local-approle-secret");
            OpenBaoSecretReader reader=new OpenBaoSecretReader();
            set(reader,"addr",Optional.of(base));set(reader,"caFile",Optional.empty());
            set(reader,"roleIdFile",Optional.of(role.toString()));set(reader,"secretIdFile",Optional.of(secret.toString()));set(reader,"timeoutMs",5000L);
            CredentialRefResolver credentials=new CredentialRefResolver();set(credentials,"openBao",reader);
            IdentServiceTokens tokens=new IdentServiceTokens();set(tokens,"secrets",reader);
            set(tokens,"ident",new IdentTokenVerifier(){@Override public synchronized String identPublicUrl(){return base;}@Override public String internal(String url){return url;}});
            CoreInterconnectionResolver resolver=new CoreInterconnectionResolver();set(resolver,"coreResolverUrl",Optional.of(base+"/core"));
            set(resolver,"environment","TEST");set(resolver,"coreTimeoutMs",5000);set(resolver,"objectMapper",json);
            ResolutionCache cache=new ResolutionCache();set(cache,"defaultTtl",30);set(cache,"defaultAllowStale",0);
            TenantContext tenant=new TenantContext(){@Override public UUID instanceCountryId(){return pe;}};
            set(executor,"tenant",tenant);set(executor,"coreResolver",resolver);set(executor,"cache",cache);
            set(executor,"credentials",credentials);set(executor,"serviceTokens",tokens);set(executor,"circuits",new CircuitRegistry());set(executor,"objectMapper",json);
            adapter.executor=executor;adapter.tenant=tenant;adapter.interconnectionId="SGI_COM_SMC__0001_v001";adapter.interfaceId="SGI_COM_SMC_0001_IF01";
            server.start();
        }
        Map<String,Object> body(){return Map.of("eventId","stable-local-event","instanceCountryId",pe.toString(),"employeeId",UUID.randomUUID().toString(),"factType","ROUTE_COMPLETION","occurredAt","2026-10-27T13:00:00Z","value",100);}
        public void close(){server.stop(0);}
    }

    static void reply(HttpExchange e,int status,String body) throws java.io.IOException {
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","application/json");
        e.sendResponseHeaders(status,bytes.length);e.getResponseBody().write(bytes);e.close();
    }
    static void set(Object target,String field,Object value) throws Exception {
        var f=target.getClass().getDeclaredField(field);f.setAccessible(true);f.set(target,value);
    }
}
