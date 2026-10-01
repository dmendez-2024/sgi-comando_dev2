# Fase 2 — VISINT para Hitos de patrulla · Plan de implementación

> **Nota 2026-09-30:** plan original. Lo implementado cambió después (hasta 5 fotos estándar por Hito, una foto del agente, contrato real de VISINT `/v1/evidence/validate`). Estado vigente: `docs/VISINT_ESTADO_IMPLEMENTACION.md`.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que cada ejecución de un Hito con VISINT activado se envíe a VISINT junto con su foto estándar, que VISINT responda en la misma llamada **PASS (cumple), FAIL (no cumple) o ERROR**, y que el supervisor vea ese veredicto en **Servicios → Operación**, con la foto estándar al lado de las fotos del agente.

**Architecture:** VISINT es opcional por Hito (`visint_enabled`, apagado por defecto). Al confirmarse un Hito con VISINT activado se crea una `visual_review` en cola (misma transacción que la ejecución). Un worker (`@Scheduled`, cada 2 s) hace **una llamada síncrona** a VISINT mediante `VisintPort`: HTTP real vía `GenericInterconnectionExecutor` (por defecto) o **simulado**, solo si `SGI_VISINT_MODE=MOCK` **y** `SGI_UAT_FEATURES_ENABLED=true`. Comando no calcula el veredicto: lo guarda y lo muestra. Si VISINT no responde, se reintenta con espera creciente. La confirmación del agente nunca depende de VISINT. Las herramientas UAT (VISINT simulado y Simulador de Agente) quedan detrás de la bandera UAT.

**Tech Stack:** Quarkus 3.28 (REST, Panache, Flyway, scheduler), Postgres 17, MinIO (`io.minio:minio` 8.5.17), React 19 + Vite + TypeScript, Playwright (E2E fuera del repo).

**Spec:** Conversación de diseño del 2026-09-29 (memoria `visint-flow-scope`), `docs/SGI_OPR_VISINT_IMPULSOS.md`, precisiones del usuario (*VISINT síncrono; responde cumple/no cumple sin puntajes; Comando no decide "al menos una foto"; VISINT opcional por Hito; simular la API hasta tener la real*) y `Revision_Puesta_en_Produccion_SGI_Comando_Sistemas_2026-09-28.pdf` (VISINT PASS/FAIL/ERROR trazable y reintentable; "Cero DEMO/mock" en rutas productivas; funcionalidad no productiva detrás de feature flag). Impulsos **fuera de alcance**.

## Global Constraints

- **No hacer commits** (pedido del usuario).
- VISINT **síncrono**: una llamada `SGI_COM_VISINT_0001_IF01` (`POST /api/v1/visual-reviews`) devuelve `result: PASS|FAIL|ERROR` y `findings` opcional. **Sin puntajes**, sin resultado por foto, sin reglas en Comando. Contrato **provisional** hasta recibir el real.
- VISINT **opcional por Hito** (`visint_enabled`, defecto `false`). Activado ⇒ el Hito necesita foto estándar para publicarse.
- Modo: `sgi.visint.mode=${SGI_VISINT_MODE:HTTP}`. `MOCK` solo funciona si `sgi.uat.features-enabled=true`; si no, la revisión falla con “VISINT simulado deshabilitado fuera de UAT”.
- Bandera UAT: `sgi.uat.features-enabled=${SGI_UAT_FEATURES_ENABLED:false}`; habilita VISINT simulado y el menú del Simulador de Agente.
- Estados de `visual_review`: `QUEUED_FOR_VISINT`, `PASSED` (PASS), `FAILED` (FAIL), `ERROR_RETRYABLE` (VISINT no respondió, se reintenta), `ERROR_FINAL` (VISINT respondió ERROR, o se agotaron los reintentos). Columna `result` ∈ {PASS, FAIL, ERROR}.
- Reintentos: máximo 5; espera 30 s, 60 s, 120 s, 240 s (tope 10 min); luego `ERROR_FINAL`.
- Migración nueva: **V38**. Tenant en toda tabla/consulta. Textos en español. Operación solo para roles de Comando.
- Pruebas: `powershell -NoProfile -File scripts/backend-test.ps1 [-Test Clases]`; frontend `cd frontend && npm run build`.

## Review Focus

1. **VISINT caído al confirmar el Hito:** la confirmación del agente no falla; `ERROR_RETRYABLE` y luego se completa → T3 `retriesAfterFailureThenPasses`.
2. **VISINT responde ERROR:** queda `ERROR_FINAL` con `result=ERROR` (no se reintenta solo; sí manualmente) → T3 `visintErrorIsFinal`.
3. **Modo MOCK sin bandera UAT (producción mal configurada):** no se simula un resultado → T2 `mockRefusedOutsideUat`.
4. **Foto estándar reemplazada después de ejecutar:** Operación muestra la enviada a VISINT → T4 `standardImageIsTheSnapshotUsed`.
5. **VISINT activado sin foto estándar al publicar:** rechazo claro → T1 `publishRejectsVisintWithoutStandardImage`.

---

## Mapa de archivos

**Backend — crear:** `db/migration/V38__visual_review.sql`; `visint/` (`VisintPort`, `VisintUnavailableException`, `MockVisintAdapter`, `HttpVisintAdapter`, `VisintClient`, `VisualReview`, `VisualReviewService`, `VisualReviewWorker`); `operation/OperationResource.java`; `common/FeaturesResource.java`. Tests: `patrols/CheckpointVisintConfigTest`, `visint/MockVisintAdapterTest`, `visint/VisintClientTest`, `visint/HttpVisintAdapterTest`, `storage/PresignTest`, `operator/VisintFixtures`, `visint/VisualReviewFlowTest`, `operation/OperationResourceTest`, `common/FeaturesResourceTest`.
**Backend — modificar:** `patrols/PatrolResource.java`, `operator/OperatorPatrols.java`, `operator/PatrolExecutionService.java`, `storage/StorageService.java`, `application.properties`, `docker-compose.yml`, `.env` (local, no versionado), catálogo, `docs/API_CONTRACTS.md`, `docs/DECISIONS.md`.
**Frontend:** modificar `api.ts`, `PatrolConfig.tsx`, `Services.tsx`, `Sidebar.tsx`, `App.tsx`, `styles.css`; crear `OperationPage.tsx`.

---

### Task 1: VISINT opcional por Hito (backend)

**Files:** Modify `patrols/PatrolResource.java`, `operator/OperatorPatrols.java`; Test `patrols/CheckpointVisintConfigTest.java`

**Interfaces — Produces:** `SaveCheckpointRequest` agrega al final `Boolean visintEnabled` (`null` = conservar). Activar exige `requiresEvidence` (400 “VISINT requiere que el Hito exija fotos”); si el Hito deja de requerir evidencia, VISINT se apaga. Publicar con VISINT activo sin foto estándar → 400 “<código>: VISINT activo requiere foto estándar”. Subir foto estándar no apaga VISINT; eliminarla sí. Runtime del operador incluye `visintEnabled`.

- [ ] **Step 1: Prueba que falla**
```java
package com.cajamarca.sgi.comando.patrols;

import com.cajamarca.sgi.comando.operator.OperatorFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class CheckpointVisintConfigTest {
    Map<String,Object> body(Boolean visint, boolean requiresEvidence) {
        Map<String,Object> b = new HashMap<>(Map.of("name", "Portón prueba", "description", "", "originMode", "FIELD", "latitude", -2.17, "longitude", -79.92,
            "gpsAccuracyM", 5.0, "controlType", "FOTOGRAFIA", "requiresEvidence", requiresEvidence, "standardImageNotes", ""));
        b.put("visintEnabled", visint);
        return b;
    }
    String cp() { return (String) OperatorFixtures.createDraftPatrolWithCheckpoint(-2.17, -79.92).get("checkpointId"); }

    @Test void offByDefaultAndCanBeToggled() {
        String cp = cp();
        as("coord").contentType(ContentType.JSON).body(body(null, true)).put("/api/patrols/checkpoints/" + cp).then().statusCode(200).body("visintEnabled", is(false));
        as("coord").contentType(ContentType.JSON).body(body(true, true)).put("/api/patrols/checkpoints/" + cp).then().statusCode(200).body("visintEnabled", is(true));
        as("coord").contentType(ContentType.JSON).body(body(null, true)).put("/api/patrols/checkpoints/" + cp).then().statusCode(200).body("visintEnabled", is(true));
        as("coord").contentType(ContentType.JSON).body(body(false, true)).put("/api/patrols/checkpoints/" + cp).then().statusCode(200).body("visintEnabled", is(false));
    }
    @Test void requiresPhotosToEnable() {
        as("coord").contentType(ContentType.JSON).body(body(true, false)).put("/api/patrols/checkpoints/" + cp()).then().statusCode(400);
    }
    @Test void uploadingStandardImageKeepsVisintEnabled() {
        String cp = cp();
        as("coord").contentType(ContentType.JSON).body(body(true, true)).put("/api/patrols/checkpoints/" + cp).then().statusCode(200);
        as("coord").multiPart("file", Path.of("src/test/resources/fixtures/sample.jpg").toFile(), "image/jpeg")
            .post("/api/patrols/checkpoints/" + cp + "/standard-image").then().statusCode(200).body("visintEnabled", is(true));
    }
    @Test void publishRejectsVisintWithoutStandardImage() {
        Map<String,Object> ids = OperatorFixtures.createDraftPatrolWithCheckpoint(-2.17, -79.92);
        as("coord").contentType(ContentType.JSON).body(body(true, true)).put("/api/patrols/checkpoints/" + ids.get("checkpointId")).then().statusCode(200);
        as("coord").post("/api/patrols/protocols/" + ids.get("protocolId") + "/publish").then().statusCode(400);
    }
}
```
Run `-Test CheckpointVisintConfigTest` → FAIL.

- [ ] **Step 2: Implementación** (`PatrolResource.java`)
1. `SaveCheckpointRequest`: agregar `,Boolean visintEnabled` al final.
2. En `saveCheckpoint`, después de `cp.evidenceMinCount=minPhotos;cp.evidenceMaxCount=maxPhotos;`:
```java
        if(req.visintEnabled()!=null){if(req.visintEnabled()&&!cp.requiresEvidence)throw new BadRequestException("VISINT requiere que el Hito exija fotos");cp.visintEnabled=req.visintEnabled();}
        if(!cp.requiresEvidence)cp.visintEnabled=false;
```
3. `uploadImage`: `cp.standardImageVersion=Math.max(1,cp.standardImageVersion+1);cp.visintEnabled=false;` → `cp.standardImageVersion=Math.max(1,cp.standardImageVersion+1);`.
4. `validatePatrolForPublish`: `for(PatrolCheckpoint checkpoint:items)validateOrigin(checkpoint,protocol);` →
```java
for(PatrolCheckpoint checkpoint:items){validateOrigin(checkpoint,protocol);if(checkpoint.visintEnabled&&!StandardImageStore.has(checkpoint.standardImageObjectKey,checkpoint.standardImageData))throw new BadRequestException(checkpoint.code+": VISINT activo requiere foto estándar");}
```
`OperatorPatrols.runtime`: agregar `.put("visintEnabled", c.visintEnabled)`.
- [ ] **Step 3:** `-Test CheckpointVisintConfigTest` PASS (4); suite PASS.

---

### Task 2: Puerto VISINT (HTTP real / simulado UAT) + URLs firmadas

**Files:** Create `visint/VisintPort.java`, `VisintUnavailableException.java`, `MockVisintAdapter.java`, `HttpVisintAdapter.java`, `VisintClient.java`; Modify `storage/StorageService.java`, `application.properties`; Test `visint/MockVisintAdapterTest.java`, `visint/VisintClientTest.java`, `visint/HttpVisintAdapterTest.java`, `storage/PresignTest.java`

**Interfaces — Produces:**
```java
public interface VisintPort {
    record EvidenceRef(UUID evidenceId, String bucket, String objectKey, String sha256, String contentType) {}
    record StandardRef(UUID targetId, int version, String bucket, String objectKey, String sha256) {}
    record ReviewRequest(UUID reviewId, UUID taskExecutionId, String taskType, UUID employeeId, UUID pointId, UUID postId,
                         List<EvidenceRef> evidences, StandardRef standard, UUID correlationId) {}
    /** result: "PASS" (cumple), "FAIL" (no cumple) o "ERROR" (VISINT no pudo evaluar). findings: texto opcional. */
    record ReviewResult(String externalId, String result, String findings) {}
    /** Llamada síncrona. Lanza VisintUnavailableException si VISINT no responde o responde algo inválido. */
    ReviewResult review(ReviewRequest request);
}
```
- `VisintClient implements VisintPort`: campos de paquete `mode`, `uatEnabled`, `mock`, `http`; `boolean simulated()` = `MOCK && uatEnabled`; `review()` → MOCK+UAT: `mock`; MOCK sin UAT: lanza `VisintUnavailableException("VISINT simulado deshabilitado fuera de UAT")`; si no: `http`.
- `MockVisintAdapter.failNextCalls(int n)`, `MockVisintAdapter.forceNextResult(String result)` (solo pruebas/demos).
- `HttpVisintAdapter.parseResult(String json, ObjectMapper m) -> ReviewResult` (acepta PASS/FAIL/ERROR).
- `StorageService.presignedGet(String bucket, String key, int ttlSeconds) -> String`.
- Config:
```properties
sgi.visint.mode=${SGI_VISINT_MODE:HTTP}
sgi.visint.worker-enabled=true
sgi.visint.max-attempts=5
sgi.visint.mock.latency-ms=${SGI_VISINT_MOCK_LATENCY_MS:800}
sgi.storage.presign-ttl-seconds=600
sgi.uat.features-enabled=${SGI_UAT_FEATURES_ENABLED:false}
%test.sgi.visint.mode=MOCK
%test.sgi.uat.features-enabled=true
%test.sgi.visint.worker-enabled=false
%test.sgi.visint.mock.latency-ms=0
```
**Simulado (determinista, solo UAT):** `PASS` si alguna foto del agente tiene el mismo sha256 que la estándar; si no, según el primer carácter hex de `sha256(concatenación de sha256 de las fotos + sha256 estándar)`: `0–9` → PASS, `a–d` → FAIL, `e–f` → ERROR. Hallazgos: “Cumple con el estándar (VISINT simulado)”, “No cumple con el estándar (VISINT simulado)”, “No se pudo evaluar la imagen (VISINT simulado)”. `externalId = "mock-" + reviewId`.

- [ ] **Step 1: Pruebas que fallan**

`MockVisintAdapterTest.java`:
```java
package com.cajamarca.sgi.comando.visint;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class MockVisintAdapterTest {
    @Inject VisintClient client;
    @Inject MockVisintAdapter mock;
    static VisintPort.ReviewRequest request(String... shas) {
        List<VisintPort.EvidenceRef> ev = new ArrayList<>();
        for (String s : shas) ev.add(new VisintPort.EvidenceRef(UUID.randomUUID(), "b", "k", s, "image/jpeg"));
        return new VisintPort.ReviewRequest(UUID.randomUUID(), UUID.randomUUID(), "PATROL_CHECKPOINT", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            ev, new VisintPort.StandardRef(UUID.randomUUID(), 1, "b", "k", "a".repeat(64)), UUID.randomUUID());
    }
    @Test void identicalPhotoPasses() {
        var r = client.review(request("b".repeat(64), "a".repeat(64)));
        assertEquals("PASS", r.result()); assertTrue(r.findings().contains("simulado")); assertTrue(client.simulated());
    }
    @Test void deterministicVerdict() {
        var req = request("b".repeat(64), "c".repeat(64));
        String first = client.review(req).result();
        assertEquals(first, client.review(req).result());
        assertTrue(Set.of("PASS", "FAIL", "ERROR").contains(first));
    }
    @Test void canSimulateOutageAndForcedResult() {
        mock.failNextCalls(1);
        assertThrows(VisintUnavailableException.class, () -> client.review(request("a".repeat(64))));
        mock.forceNextResult("ERROR");
        assertEquals("ERROR", client.review(request("a".repeat(64))).result());
        assertEquals("PASS", client.review(request("a".repeat(64))).result());
    }
}
```
`VisintClientTest.java`:
```java
package com.cajamarca.sgi.comando.visint;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VisintClientTest {
    @Test void mockRefusedOutsideUat() {
        VisintClient c = new VisintClient();
        c.mode = "MOCK"; c.uatEnabled = false; c.mock = new MockVisintAdapter();
        assertFalse(c.simulated());
        VisintUnavailableException e = assertThrows(VisintUnavailableException.class, () -> c.review(MockVisintAdapterTest.request("a".repeat(64))));
        assertTrue(e.getMessage().contains("fuera de UAT"));
    }
}
```
`HttpVisintAdapterTest.java`:
```java
package com.cajamarca.sgi.comando.visint;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HttpVisintAdapterTest {
    final ObjectMapper m = new ObjectMapper();
    @Test void parsesVerdicts() {
        var r = HttpVisintAdapter.parseResult("{\"reviewId\":\"v-1\",\"result\":\"FAIL\",\"findings\":\"candado abierto\"}", m);
        assertEquals("v-1", r.externalId()); assertEquals("FAIL", r.result()); assertEquals("candado abierto", r.findings());
        assertEquals("ERROR", HttpVisintAdapter.parseResult("{\"reviewId\":\"v-3\",\"result\":\"ERROR\"}", m).result());
        assertNull(HttpVisintAdapter.parseResult("{\"reviewId\":\"v-2\",\"result\":\"PASS\"}", m).findings());
    }
    @Test void rejectsMissingOrUnknownResult() {
        assertThrows(VisintUnavailableException.class, () -> HttpVisintAdapter.parseResult("{\"reviewId\":\"x\"}", m));
        assertThrows(VisintUnavailableException.class, () -> HttpVisintAdapter.parseResult("{\"reviewId\":\"x\",\"result\":\"MAYBE\"}", m));
        assertThrows(VisintUnavailableException.class, () -> HttpVisintAdapter.parseResult("no json", m));
    }
}
```
`PresignTest.java`:
```java
package com.cajamarca.sgi.comando.storage;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PresignTest {
    @Inject StorageService storage;
    @Test void presignedUrlDownloadsTheObjectWithoutCredentials() throws Exception {
        String key = "test/" + UUID.randomUUID() + ".bin";
        storage.put(storage.evidenceBucket(), key, new byte[]{7, 8, 9}, "application/octet-stream");
        String url = storage.presignedGet(storage.evidenceBucket(), key, 60);
        assertTrue(url.contains("X-Amz-Signature"));
        HttpResponse<byte[]> r = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, r.statusCode()); assertArrayEquals(new byte[]{7, 8, 9}, r.body());
    }
}
```
Run `-Test "MockVisintAdapterTest,VisintClientTest,HttpVisintAdapterTest,PresignTest"` → FAIL de compilación.

- [ ] **Step 2: Implementación**

`VisintUnavailableException.java`:
```java
package com.cajamarca.sgi.comando.visint;
/** VISINT no respondió o respondió algo inválido; la revisión se reintenta. */
public class VisintUnavailableException extends RuntimeException {
    public VisintUnavailableException(String message) { super(message); }
    public VisintUnavailableException(String message, Throwable cause) { super(message, cause); }
}
```
`VisintPort.java`: el bloque de Interfaces con `package com.cajamarca.sgi.comando.visint;` e `import java.util.*;`.

`MockVisintAdapter.java`:
```java
package com.cajamarca.sgi.comando.visint;

import com.cajamarca.sgi.comando.storage.Digests;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;

/** VISINT simulado (solo UAT) mientras no exista el servicio real. Determinista: usa las huellas sha256, no analiza imágenes. */
@ApplicationScoped
public class MockVisintAdapter implements VisintPort {
    @ConfigProperty(name="sgi.visint.mock.latency-ms", defaultValue="0") long latencyMs;
    private final AtomicInteger failures = new AtomicInteger();
    private final AtomicReference<String> forced = new AtomicReference<>();

    public void failNextCalls(int n) { failures.set(n); }
    public void forceNextResult(String result) { forced.set(result); }

    @Override
    public ReviewResult review(ReviewRequest request) {
        if (failures.getAndUpdate(x -> Math.max(0, x - 1)) > 0) throw new VisintUnavailableException("VISINT simulado: sin respuesta (falla forzada)");
        if (latencyMs > 0) { try { Thread.sleep(latencyMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
        String result = forced.getAndSet(null);
        if (result == null) {
            String standard = request.standard().sha256();
            if (request.evidences().stream().anyMatch(e -> e.sha256().equals(standard))) result = "PASS";
            else {
                StringBuilder all = new StringBuilder();
                request.evidences().forEach(e -> all.append(e.sha256()));
                char c = Digests.sha256Hex((all + standard).getBytes(StandardCharsets.UTF_8)).charAt(0);
                result = c <= '9' ? "PASS" : c <= 'd' ? "FAIL" : "ERROR";
            }
        }
        String findings = switch (result) {
            case "PASS" -> "Cumple con el estándar (VISINT simulado)";
            case "FAIL" -> "No cumple con el estándar (VISINT simulado)";
            default -> "No se pudo evaluar la imagen (VISINT simulado)";
        };
        return new ReviewResult("mock-" + request.reviewId(), result, findings);
    }
}
```
`HttpVisintAdapter.java`:
```java
package com.cajamarca.sgi.comando.visint;

import com.cajamarca.sgi.comando.interconnections.*;
import com.cajamarca.sgi.comando.storage.StorageService;
import com.fasterxml.jackson.databind.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.*;

/**
 * VISINT real: una llamada síncrona a SGI_COM_VISINT_0001_IF01 con URLs firmadas de MinIO.
 * Request/response PROVISIONALES hasta recibir el contrato oficial de VISINT (ver docs/API_CONTRACTS.md).
 */
@ApplicationScoped
public class HttpVisintAdapter implements VisintPort {
    @Inject GenericInterconnectionExecutor executor;
    @Inject StorageService storage;
    @Inject ObjectMapper mapper;
    @ConfigProperty(name="sgi.storage.presign-ttl-seconds") int ttl;

    @Override
    public ReviewResult review(ReviewRequest r) {
        List<Map<String,Object>> evidences = new ArrayList<>();
        for (EvidenceRef e : r.evidences()) evidences.add(Map.of("evidenceId", e.evidenceId().toString(),
            "url", storage.presignedGet(e.bucket(), e.objectKey(), ttl), "sha256", e.sha256(), "contentType", e.contentType()));
        Map<String,Object> standard = Map.of("targetId", r.standard().targetId().toString(), "version", r.standard().version(),
            "url", storage.presignedGet(r.standard().bucket(), r.standard().objectKey(), ttl), "sha256", r.standard().sha256());
        IntegrationRequest req = new IntegrationRequest();
        req.idempotencyKey = r.reviewId().toString(); req.correlationId = r.correlationId().toString();
        req.body = Map.of("visintReviewId", r.reviewId().toString(), "taskExecutionId", r.taskExecutionId().toString(), "taskType", r.taskType(),
            "employeeId", r.employeeId().toString(), "pointId", r.pointId().toString(), "postId", r.postId().toString(),
            "evidenceRefs", evidences, "standardReference", standard, "correlationId", r.correlationId().toString());
        IntegrationResult res;
        try { res = executor.execute(InterconnectionIds.VISINT_REVIEW_REQUEST, "SGI_COM_VISINT_0001_IF01", req); }
        catch (RuntimeException e) { throw new VisintUnavailableException("VISINT no disponible: " + e.getMessage(), e); }
        if (!res.successful()) throw new VisintUnavailableException("VISINT respondió " + res.statusCode);
        return parseResult(res.body, mapper);
    }

    public static ReviewResult parseResult(String json, ObjectMapper mapper) {
        JsonNode n;
        try { n = mapper.readTree(json); } catch (Exception e) { throw new VisintUnavailableException("Respuesta de VISINT inválida", e); }
        String result = n.path("result").asText("");
        if (!Set.of("PASS", "FAIL", "ERROR").contains(result)) throw new VisintUnavailableException("Respuesta de VISINT sin veredicto PASS/FAIL/ERROR");
        return new ReviewResult(n.path("reviewId").asText(null), result, n.hasNonNull("findings") ? n.path("findings").asText() : null);
    }
}
```
`VisintClient.java`:
```java
package com.cajamarca.sgi.comando.visint;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Acceso único a VISINT. Real (HTTP) por defecto; simulado solo si SGI_VISINT_MODE=MOCK y la bandera UAT está activa. */
@ApplicationScoped
public class VisintClient implements VisintPort {
    @Inject MockVisintAdapter mock;
    @Inject HttpVisintAdapter http;
    @ConfigProperty(name="sgi.visint.mode") String mode;
    @ConfigProperty(name="sgi.uat.features-enabled") boolean uatEnabled;

    boolean mockRequested() { return "MOCK".equalsIgnoreCase(mode); }
    public boolean simulated() { return mockRequested() && uatEnabled; }

    @Override
    public ReviewResult review(ReviewRequest request) {
        if (mockRequested()) {
            if (!uatEnabled) throw new VisintUnavailableException("VISINT simulado deshabilitado fuera de UAT");
            return mock.review(request);
        }
        return http.review(request);
    }
}
```
`StorageService.presignedGet` (import `io.minio.http.Method`):
```java
public String presignedGet(String bucket, String key, int ttlSeconds) {
    try { return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET).bucket(bucket).object(key).expiry(ttlSeconds).build()); }
    catch (Exception e) { throw new StorageUnavailableException("No se pudo generar el enlace temporal del archivo", e); }
}
```
- [ ] **Step 3:** PASS (8); suite PASS.

---

### Task 3: Revisión visual en cola y worker síncrono

**Files:** Create `db/migration/V38__visual_review.sql`, `visint/VisualReview.java`, `visint/VisualReviewService.java`, `visint/VisualReviewWorker.java`; Modify `operator/PatrolExecutionService.java`; Test `operator/VisintFixtures.java`, `visint/VisualReviewFlowTest.java`

**Interfaces — Produces:** `VisualReviewService.enqueue(TaskExecution, PatrolCheckpoint) -> VisualReview`, `statusFor(UUID) -> String` (`NOT_REQUESTED` si no hay); `VisualReviewWorker.processDue() -> int`; ack `validationStatus` real; `VisintFixtures.publishVisintPatrol() -> Map`, `VisintFixtures.executeWithPhotos(EntityManager, Map, List<Path>) -> UUID`.

- [ ] **Step 1: V38**
```sql
-- Revisión visual (VISINT) de cada ejecución de un Hito con VISINT activado.
-- VISINT responde de forma síncrona PASS (cumple), FAIL (no cumple) o ERROR (no pudo evaluar). Sin puntajes.
CREATE TABLE visual_review (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  task_execution_id uuid NOT NULL UNIQUE REFERENCES task_execution(id),
  status varchar(24) NOT NULL,
  standard_target_type varchar(40) NOT NULL,
  standard_target_id uuid NOT NULL,
  standard_image_version integer NOT NULL,
  standard_bucket varchar(63),
  standard_object_key varchar(300),
  standard_sha256 char(64),
  simulated boolean NOT NULL DEFAULT false,
  visint_external_id varchar(120),
  attempts integer NOT NULL DEFAULT 0,
  next_attempt_at timestamptz,
  last_error varchar(500),
  result varchar(8),
  findings varchar(1000),
  correlation_id uuid NOT NULL,
  created_at timestamptz NOT NULL,
  requested_at timestamptz,
  reviewed_at timestamptz,
  CHECK (status IN ('QUEUED_FOR_VISINT','PASSED','FAILED','ERROR_RETRYABLE','ERROR_FINAL')),
  CHECK (result IS NULL OR result IN ('PASS','FAIL','ERROR'))
);
CREATE INDEX ix_visual_review_due ON visual_review(status, next_attempt_at);
```
- [ ] **Step 2: Fixtures y prueba que falla** — `VisintFixtures.java` (publica protocolo con VISINT activado y foto estándar `sample.jpg`; sube fotos por FormData y confirma el Hito) y `VisualReviewFlowTest.java` con: `queuedThenVerdictFromVisint` (SAME+OTHER → QUEUED → PASSED, `simulated=true`, hallazgo con “simulado”), `retriesAfterFailureThenPasses` (`failNextCalls(1)` → ERROR_RETRYABLE, attempts 1 → forzar `next_attempt_at=now()` → PASSED), `givesUpAfterMaxAttempts` (attempts=4 + falla → ERROR_FINAL, `result` null), `visintErrorIsFinal` (`forceNextResult("ERROR")` → ERROR_FINAL con `result=ERROR`), `failVerdictIsStored` (`forceNextResult("FAIL")` → FAILED), `executionWithoutVisintHasNoReview`. (Código completo en la implementación; mismas utilidades que Fase 1.)
- [ ] **Step 3: Entidad `VisualReview`** — Panache, un campo por columna.
- [ ] **Step 4: `VisualReviewService`** — `enqueue` guarda snapshot de la foto estándar (bucket, clave, versión, sha256), `simulated = visint.simulated()`; sin clave de MinIO → `ERROR_FINAL` “La foto estándar aún no está en el almacenamiento”.
- [ ] **Step 5: `VisualReviewWorker`** — `processDue()` toma hasta 20 revisiones `QUEUED_FOR_VISINT|ERROR_RETRYABLE` vencidas; cada una en su transacción con `for update skip locked`; arma `ReviewRequest` con las fotos en orden; `PASS → PASSED`, `FAIL → FAILED`, `ERROR → ERROR_FINAL` (con `result`/`findings`); `VisintUnavailableException` → `ERROR_RETRYABLE` con espera `min(600, 30·2^(n-1))` s o `ERROR_FINAL` al llegar a `max-attempts`.
- [ ] **Step 6: `PatrolExecutionService`** — `validationStatus` = `reviews.enqueue(x, cp).status` si `cp.visintEnabled`, si no `NOT_REQUESTED`; la rama idempotente usa `reviews.statusFor(eventId)`.
- [ ] **Step 7:** `-Test "VisualReviewFlowTest,PatrolExecutionTest"` PASS; suite PASS.

---

### Task 4: Endpoints de la vista Operación

**Files:** Create `operation/OperationResource.java`; Test `operation/OperationResourceTest.java`

**Interfaces — Produces** (`/api/operation`; lectura: PRESIDENTE, DIRECTOR_OPERACIONES_LATAM, DIRECTOR_OPERACIONES_NACIONAL, DIRECTOR_NACIONAL, DIRECTOR_ZONAL, JEFE_REGIONAL, COORDINADOR_COMPANIA, ASISTENTE_COORDINACION, SUPERVISOR_SEGURIDAD; reintento: los mismos sin SUPERVISOR_SEGURIDAD; `scope.requireCompany`):
- `GET /executions?pointId&limit` → `ExecutionRow(id, executedAt, postCode, postName, protocolCode, protocolVersion, patrolCode, patrolName, checkpointCode, checkpointName, employeeName, evidenceIds, flags, ReviewSummary review)`; `ReviewSummary(id, status, result, simulated)`.
- `GET /executions/{id}` → `ExecutionDetail(row, observation, latitude, longitude, standardNotes, List<EvidenceView>, ReviewDetail)`; `EvidenceView(id, capturedAt, latitude, longitude, source, flags)`; `ReviewDetail(id, status, result, findings, standardImageVersion, simulated, attempts, lastError, createdAt, requestedAt, reviewedAt)`.
- `GET /evidences/{id}/content`; `GET /executions/{id}/standard-image` (snapshot de la revisión); `POST /visual-reviews/{id}/retry` (solo desde `ERROR_RETRYABLE`/`ERROR_FINAL`, si no 409).

- [ ] **Step 1: Prueba que falla** — `listAndDetailShowVerdict`, `servesAgentPhotoAndStandard`, `standardImageIsTheSnapshotUsed`, `retryOnlyFromErrorAndNotForAgents`, `agentsCannotListOperation`.
- [ ] **Step 2: Implementación** — `OperationResource` según Interfaces (consulta nativa para códigos de puesto/protocolo/patrulla/Hito y nombre del agente; fotos por `task_execution_evidence.sort_order`).
- [ ] **Step 3:** PASS (5); suite PASS.

---

### Task 5: Bandera UAT (features) y visibilidad del Simulador

**Files:** Create `common/FeaturesResource.java`; Test `common/FeaturesResourceTest.java`; Modify `docker-compose.yml`, `.env` local, `frontend/src/api.ts`, `frontend/src/components/Sidebar.tsx`, `frontend/src/App.tsx`

**Interfaces — Produces:** `GET /api/features` (`@Authenticated`) → `{"uatTools":boolean,"visintMode":"HTTP"|"MOCK","visintSimulated":boolean}`. `docker-compose.yml` backend: `SGI_VISINT_MODE: ${SGI_VISINT_MODE:-HTTP}`, `SGI_UAT_FEATURES_ENABLED: ${SGI_UAT_FEATURES_ENABLED:-false}`. `.env` local: `SGI_VISINT_MODE=MOCK`, `SGI_UAT_FEATURES_ENABLED=true`. Frontend: `api.features()`; el menú “Simulador Agente (UAT)” y su pantalla solo si `uatTools`.

- [ ] **Step 1: Prueba** — `FeaturesResourceTest`: en perfil de prueba devuelve `uatTools=true`, `visintMode=MOCK`, `visintSimulated=true`; sin credenciales → 401.
- [ ] **Step 2: Implementación** backend + compose + `.env`.
- [ ] **Step 3: Frontend** — `Sidebar` recibe los ítems filtrados (el ítem del simulador se omite si `!uatTools`); `App` muestra “Herramienta UAT deshabilitada” si se llega al simulador sin la bandera.
- [ ] **Step 4:** PASS; `npm run build` OK.

---

### Task 6: Frontend — interruptor VISINT en el Hito

- [ ] `saveCheckpoint` envía `visintEnabled`. En `CheckpointEditor`, dentro de `.pat-standard` tras “Notas del estándar”, casilla **“Validar con VISINT (opcional)”** (deshabilitada sin foto estándar o si el Hito no requiere evidencia) con texto de ayuda. Estilos `.pat-visint`. `npm run build` OK.

---

### Task 7: Frontend — vista Operación

- [ ] `api.ts`: `operationExecutions`, `operationExecution`, `operationEvidenceImage`, `operationStandardImage`, `retryVisualReview`.
- [ ] `OperationPage.tsx`: indicadores (Ejecuciones, Cumplen, No cumplen, En revisión, Error VISINT, Sin VISINT); tabla (fecha, Hito, patrulla, agente, miniaturas, veredicto, alertas); panel lateral con veredicto, texto de VISINT, “VISINT simulado” si aplica, foto estándar vs foto del agente, galería, línea de tiempo y **Reintentar VISINT** en error. Etiquetas: `PASSED`→Cumple, `FAILED`→No cumple, `QUEUED_FOR_VISINT`→En revisión, `ERROR_RETRYABLE`→Reintentando, `ERROR_FINAL` con `result=ERROR`→“VISINT no pudo evaluar”, sin result→“VISINT no disponible”. Refresco cada 4 s mientras haya pendientes.
- [ ] `Services.tsx`: vista `'operation'` y botón **Operación** habilitado.
- [ ] Estilos `.opr-*`. `npm run build` OK; reconstruir `frontend`.

---

### Task 8: E2E con video, contrato y documentación

- [ ] `docker compose up -d --build` (V38; `.env` con MOCK + UAT).
- [ ] Script `evidencias_playwright/fase2_visint.mjs` → carpeta nueva `fase2_visint/`: coordinador crea nueva versión de `PRO-PAT-0006`, activa **Validar con VISINT (opcional)**, guarda y publica; simulador sube 3 fotos (una idéntica a la estándar) y confirma; **Operación**: la fila pasa de “En revisión” a **“Cumple”**; abrir detalle, comparar y recorrer las fotos.
- [ ] Catálogo `SGI_COM_VISINT_0001`: `mode: SYNC`, response `["reviewId","result","findings"]`, nota “contrato provisional”.
- [ ] `docs/API_CONTRACTS.md`: sección Fase 2 con el contrato **provisional** de VISINT (request/response) y los endpoints de Operación y `/api/features`.
- [ ] `docs/DECISIONS.md`: `SGI-VIS-DEC-001` VISINT opcional por Hito; `-002` síncrono vía worker (la confirmación del agente no depende de VISINT); `-003` veredicto PASS/FAIL/ERROR de VISINT, sin puntajes ni reglas en Comando; `-004` snapshot de la foto estándar; `-005` VISINT real por defecto, simulado solo con `SGI_VISINT_MODE=MOCK` + bandera UAT (“Cero DEMO/mock” en producción); `-006` herramientas UAT detrás de `SGI_UAT_FEATURES_ENABLED`; `-007` URLs firmadas con endpoint interno de MinIO.
- [ ] Suite backend PASS; `npm run build` OK; E2E OK.

---

## Self-review

- **Cobertura:** opcional por Hito (T1, T6) · VISINT síncrono real/simulado con PASS/FAIL/ERROR (T2) · cola, worker y reintentos (T3) · Operación (T4, T7) · bandera UAT para mock y simulador (T2, T5) · E2E y documentación (T8).
- **Review Focus:** 1→T3 `retriesAfterFailureThenPasses`; 2→T3 `visintErrorIsFinal`; 3→T2 `mockRefusedOutsideUat`; 4→T4 `standardImageIsTheSnapshotUsed`; 5→T1 `publishRejectsVisintWithoutStandardImage`.
- **Consistencia:** `VisintClient.simulated()` (T2) → T3/T5; `processDue()` (T3) → T4; `VisintFixtures` (T3) → T4; estados y `result` iguales en migración, worker, endpoints y UI.
