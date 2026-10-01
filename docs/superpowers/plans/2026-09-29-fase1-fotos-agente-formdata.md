# Fase 1 — Fotos del agente por FormData (Patrullas) · Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que un agente pueda enviar varias fotos de un Hito de patrulla por `multipart/form-data`, que SGI: Comando las guarde en MinIO asociadas a una ejecución, y que las fotos estándar pasen de Postgres (`bytea`) a MinIO.

**Architecture:** Se agrega un `StorageService` (MinIO) usado por (a) las fotos estándar de Patrullas/Consignas/Bitácora, con lectura de respaldo desde el `bytea` antiguo y un migrador en segundo plano, y (b) un endpoint nuevo `POST /api/v1/operator/evidences` que recibe 1..N fotos + metadatos JSON. La ejecución del Hito se confirma con el endpoint existente `POST /api/v1/operator/executions` (nuevo tipo `PATROL_CHECKPOINT_COMPLETED`), que asocia las fotos en `task_execution`. En el frontend se migra la carga de foto estándar a FormData, se agregan mín/máx de fotos al Hito y un **Simulador de Agente (UAT)**.

**Tech Stack:** Quarkus 3.28 (quarkus-rest, Panache, Flyway, scheduler), Postgres 17, MinIO (SDK `io.minio:minio`), React 19 + Vite + TypeScript, Playwright (E2E fuera del repo).

**Spec:** Conversación de diseño del 2026-09-29 (resumen en la memoria `visint-flow-scope`) y `docs/SGI_OPR_VISINT_IMPULSOS.md`. Impulsos quedan **fuera de alcance**.

## Global Constraints

- Máximo por foto: **5 MB** (`5242880` bytes). Formatos: **JPEG, PNG, WebP**, detectados por bytes mágicos (no por extensión ni `Content-Type` del cliente).
- Fotos por Hito: `evidence_min_count` 1..5 y `evidence_max_count` min..5; valores por defecto **1 y 5**. Máximo **5 archivos por petición**.
- `quarkus.http.limits.max-body-size=30M`, `quarkus.http.limits.max-form-attribute-size=64K`.
- El campo `metadata` del FormData se envía como **texto** (string JSON), nunca como `Blob`.
- El cliente **no** fija `Content-Type` al enviar FormData.
- Tenant: toda tabla nueva lleva `instance_country_id` y toda consulta filtra por `tenant.instanceCountryId()`.
- Endpoints de operador bajo `/api/v1/operator`, habilitados por `sgi.operator.relief-uat-enabled` (sin cambiar el nombre de la propiedad).
- Buckets: `sgi-standard` (fotos estándar) y `sgi-evidence` (fotos del agente). En pruebas: `sgi-standard-test` y `sgi-evidence-test`.
- Clave de foto estándar direccionada por contenido: `standard/{module}/{sha256}.{ext}` (module = `patrol` | `consignment` | `bitacora`).
- Clave de foto de agente: `evidence/{yyyy}/{MM}/{eventId}/{evidenceId}.{ext}`.
- Migraciones Flyway nuevas: V35, V36, V37 (la última existente es V34).
- Textos de error de API y UI en español, como el resto del sistema.
- Sin Impulsos, sin VISINT (fase 2).

## Review Focus

1. **Reintento tras corte de red:** reenviar el mismo FormData (mismos `clientEvidenceId` y bytes) debe responder `ALREADY_STORED` sin duplicar filas ni objetos → prueba en Task 7.
2. **Metadatos grandes:** 5 ítems con GPS producen un JSON > 2 KB; debe aceptarse → prueba en Task 7.
3. **Archivo disfrazado:** un texto renombrado `.jpg` debe quedar `REJECTED` con `UNSUPPORTED_FORMAT` y no guardarse → prueba en Task 7.
4. **Ejecución con fotos ajenas o insuficientes:** fotos de otro Hito/evento o menos que `evidence_min_count` deben dar 400 → prueba en Task 8.
5. **Fotos estándar antiguas (bytea) sin migrar aún:** deben seguir viéndose (lectura de respaldo) y el migrador debe moverlas conservando versión → prueba en Task 3.

---

## Mapa de archivos

**Backend — crear**
- `backend/src/main/java/com/cajamarca/sgi/comando/storage/StorageService.java` — cliente MinIO: put/read/delete, buckets perezosos.
- `backend/src/main/java/com/cajamarca/sgi/comando/storage/StorageUnavailableException.java` — 503 legible.
- `backend/src/main/java/com/cajamarca/sgi/comando/storage/ImageSniffer.java` — tipo real por bytes mágicos.
- `backend/src/main/java/com/cajamarca/sgi/comando/storage/Digests.java` — SHA-256 hex.
- `backend/src/main/java/com/cajamarca/sgi/comando/storage/StandardImageStore.java` — guardar/leer foto estándar con respaldo bytea.
- `backend/src/main/java/com/cajamarca/sgi/comando/storage/StandardImageMigrator.java` — mueve bytea → MinIO.
- `backend/src/main/java/com/cajamarca/sgi/comando/operator/OperatorContext.java` — actor/empleado/asignación (extraído de `OperatorResource`).
- `backend/src/main/java/com/cajamarca/sgi/comando/operator/OperatorPatrols.java` — patrullas aplicables al puesto y validación de Hito.
- `backend/src/main/java/com/cajamarca/sgi/comando/operator/GeoDistance.java` — haversine.
- `backend/src/main/java/com/cajamarca/sgi/comando/operator/EvidenceUploadContract.java` — parseo/validación pura del FormData.
- `backend/src/main/java/com/cajamarca/sgi/comando/operator/OperatorEvidenceResource.java` — `POST /evidences`, `GET /checkpoints/{id}/standard-image`.
- `backend/src/main/java/com/cajamarca/sgi/comando/operator/PatrolExecutionContract.java` — validación pura del evento.
- `backend/src/main/java/com/cajamarca/sgi/comando/operator/PatrolExecutionService.java` — registra la ejecución del Hito.
- `backend/src/main/java/com/cajamarca/sgi/comando/execution/EvidenceObject.java`, `TaskExecution.java` — entidades.
- `backend/src/main/resources/db/migration/V35__standard_images_object_storage.sql`
- `backend/src/main/resources/db/migration/V36__patrol_checkpoint_evidence_counts.sql`
- `backend/src/main/resources/db/migration/V37__operator_evidence_and_task_execution.sql`
- Tests: `backend/src/test/java/com/cajamarca/sgi/comando/storage/ImageSnifferTest.java`, `StorageServiceTest.java`, `StandardImageStorageTest.java`; `.../operator/EvidenceUploadContractTest.java`, `PatrolExecutionContractTest.java`, `OperatorFixtures.java`, `OperatorEvidenceResourceTest.java`, `PatrolExecutionTest.java`; `.../patrols/CheckpointEvidenceCountTest.java`; `backend/src/test/resources/fixtures/*.jpg|png|txt`.
- `scripts/backend-test.ps1` — corre las pruebas en Docker (JDK 25).
- `database/uat-fixtures/DME_03_operador_agente_patrullas_UAT.sql` — vincula `agente` y crea asignaciones.

**Backend — modificar**
- `backend/pom.xml` (dependencia MinIO), `backend/src/main/resources/application.properties`.
- `patrols/PatrolCheckpoint.java`, `patrols/PatrolResource.java`, `consignments/ConsignmentEvidence.java`, `consignments/ConsignmentResource.java`, `bitacora/LogbookProtocolField.java`, `bitacora/BitacoraResource.java`.
- `operator/OperatorResource.java` (usar `OperatorContext`, runtime con patrullas, ruteo de `/executions`).
- `docs/API_CONTRACTS.md`, `docs/DECISIONS.md`, `backend/src/main/resources/interconnections/sgi-comando-catalog.json`.

**Frontend — crear / modificar**
- Modificar `frontend/src/api.ts` — `multipartRequest`, cargas por FormData, API de operador.
- Crear `frontend/src/lib/evidenceUpload.ts` — validación de imágenes, SHA-256, armado del FormData.
- Crear `frontend/src/pages/AgentSimulator.tsx`.
- Modificar `frontend/src/pages/PatrolConfig.tsx` (mín/máx), `frontend/src/App.tsx`, `frontend/src/components/Sidebar.tsx`, `frontend/src/components/Header.tsx`, `frontend/src/styles.css`.

---

### Task 1: Infraestructura de pruebas + StorageService (MinIO)

**Files:**
- Create: `scripts/backend-test.ps1`
- Modify: `backend/pom.xml`, `backend/src/main/resources/application.properties`
- Create: `storage/StorageService.java`, `storage/StorageUnavailableException.java`, `storage/Digests.java`, `storage/ImageSniffer.java`
- Test: `storage/ImageSnifferTest.java`, `storage/StorageServiceTest.java`, `backend/src/test/resources/fixtures/sample.jpg`, `sample.png`, `not-an-image.txt`

**Interfaces:**
- Produces:
  - `StorageService.put(String bucket, String key, InputStream in, long size, String contentType)`, `put(String bucket, String key, byte[] bytes, String contentType)`, `byte[] read(String bucket, String key)`, `boolean exists(String bucket, String key)`, `void delete(String bucket, String key)`, `String standardBucket()`, `String evidenceBucket()`.
  - `ImageSniffer.detect(byte[] head) -> String|null` (`image/jpeg|image/png|image/webp`), `ImageSniffer.detect(Path) -> String|null`, `ImageSniffer.extension(String contentType) -> "jpg"|"png"|"webp"`.
  - `Digests.sha256Hex(byte[])`, `Digests.sha256Hex(Path)`.
  - `StorageUnavailableException extends WebApplicationException` (HTTP 503, texto plano).

- [ ] **Step 1: Script de pruebas**

`scripts/backend-test.ps1`:
```powershell
param([string]$Test = "", [string]$Network = "sgi-comando_dev_default")
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root ".env"
$vars = @{}
Get-Content $envFile | Where-Object { $_ -match '^\s*[^#].*=' } | ForEach-Object { $k,$v = $_ -split '=',2; $vars[$k.Trim()] = $v.Trim() }
$exists = docker compose -f "$root/docker-compose.yml" exec -T postgres psql -U $vars.POSTGRES_USER -d postgres -tAc "select 1 from pg_database where datname='sgi_comando_test'"
if ($exists -ne "1") { docker compose -f "$root/docker-compose.yml" exec -T postgres psql -U $vars.POSTGRES_USER -d postgres -c "create database sgi_comando_test owner $($vars.POSTGRES_USER)" | Out-Null }
$mvn = @("-B", "test")
if ($Test) { $mvn += "-Dtest=$Test"; $mvn += "-Dsurefire.failIfNoSpecifiedTests=false" }
docker run --rm --network $Network `
  -v "${root}/backend:/workspace" -v sgi_comando_m2:/root/.m2 -w /workspace `
  -e QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://postgres:5432/sgi_comando_test `
  -e QUARKUS_DATASOURCE_USERNAME=$($vars.POSTGRES_USER) -e QUARKUS_DATASOURCE_PASSWORD=$($vars.POSTGRES_PASSWORD) `
  -e SGI_MINIO_ENDPOINT=http://minio:9000 -e SGI_MINIO_ACCESS_KEY=$($vars.MINIO_ROOT_USER) -e SGI_MINIO_SECRET_KEY=$($vars.MINIO_ROOT_PASSWORD) `
  maven:3.9.11-eclipse-temurin-25 mvn @mvn
exit $LASTEXITCODE
```

Run: `powershell -File scripts/backend-test.ps1`
Expected: `Tests run: 7, Failures: 0, Errors: 0` y `BUILD SUCCESS` (pruebas actuales).

- [ ] **Step 2: Dependencia y configuración**

`backend/pom.xml`, dentro de `<dependencies>`:
```xml
<dependency>
  <groupId>io.minio</groupId>
  <artifactId>minio</artifactId>
  <version>8.5.17</version>
</dependency>
```

`application.properties` — reemplazar `quarkus.http.limits.max-body-size=20M` y agregar:
```properties
quarkus.http.limits.max-body-size=30M
quarkus.http.limits.max-form-attribute-size=64K
quarkus.http.body.delete-uploaded-files-on-end=true

sgi.storage.standard-bucket=${SGI_STORAGE_STANDARD_BUCKET:sgi-standard}
sgi.storage.evidence-bucket=${SGI_STORAGE_EVIDENCE_BUCKET:sgi-evidence}
sgi.storage.migrate-standard-images=${SGI_STORAGE_MIGRATE_STANDARD_IMAGES:true}
sgi.evidence.max-file-bytes=5242880
sgi.evidence.max-files-per-request=5
sgi.evidence.default-radius-m=50

%test.sgi.operator.relief-uat-enabled=true
%test.sgi.storage.standard-bucket=sgi-standard-test
%test.sgi.storage.evidence-bucket=sgi-evidence-test
%test.sgi.storage.migrate-standard-images=false
```

- [ ] **Step 3: Fixtures de imagen**

Generar con ffmpeg (instalado en `C:\ffmpeg`) desde `backend/`:
```bash
mkdir -p src/test/resources/fixtures
ffmpeg -y -loglevel error -f lavfi -i color=c=red:s=64x48 -frames:v 1 src/test/resources/fixtures/sample.jpg
ffmpeg -y -loglevel error -f lavfi -i color=c=blue:s=64x48 -frames:v 1 src/test/resources/fixtures/sample.png
ffmpeg -y -loglevel error -f lavfi -i color=c=green:s=64x48 -frames:v 1 src/test/resources/fixtures/sample2.jpg
printf 'esto no es una imagen' > src/test/resources/fixtures/not-an-image.txt
```

- [ ] **Step 4: Pruebas que fallan**

`ImageSnifferTest.java`:
```java
package com.cajamarca.sgi.comando.storage;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
class ImageSnifferTest {
  static byte[] fixture(String n) throws Exception { return Files.readAllBytes(Path.of("src/test/resources/fixtures/"+n)); }
  @Test void detectsJpegAndPng() throws Exception {
    assertEquals("image/jpeg", ImageSniffer.detect(fixture("sample.jpg")));
    assertEquals("image/png", ImageSniffer.detect(fixture("sample.png")));
  }
  @Test void detectsWebp() {
    byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    assertEquals("image/webp", ImageSniffer.detect(webp));
  }
  @Test void rejectsTextAndTiny() throws Exception {
    assertNull(ImageSniffer.detect(fixture("not-an-image.txt")));
    assertNull(ImageSniffer.detect(new byte[]{(byte)0xFF}));
  }
  @Test void extensions() { assertEquals("jpg", ImageSniffer.extension("image/jpeg")); assertEquals("webp", ImageSniffer.extension("image/webp")); }
  @Test void sha256IsLowerHex() { assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Digests.sha256Hex("abc".getBytes())); }
}
```

`StorageServiceTest.java`:
```java
package com.cajamarca.sgi.comando.storage;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@QuarkusTest
class StorageServiceTest {
  @Inject StorageService storage;
  @Test void putReadDelete() {
    String key = "test/" + UUID.randomUUID() + ".bin";
    storage.put(storage.evidenceBucket(), key, new byte[]{1,2,3}, "application/octet-stream");
    assertTrue(storage.exists(storage.evidenceBucket(), key));
    assertArrayEquals(new byte[]{1,2,3}, storage.read(storage.evidenceBucket(), key));
    storage.delete(storage.evidenceBucket(), key);
    assertFalse(storage.exists(storage.evidenceBucket(), key));
    assertThrows(NotFoundException.class, () -> storage.read(storage.evidenceBucket(), key));
  }
}
```

Run: `powershell -File scripts/backend-test.ps1 -Test "ImageSnifferTest,StorageServiceTest"`
Expected: FAIL de compilación (`cannot find symbol ImageSniffer`).

- [ ] **Step 5: Implementación**

`Digests.java`:
```java
package com.cajamarca.sgi.comando.storage;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
public final class Digests {
  private Digests() {}
  public static String sha256Hex(byte[] bytes) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
  public static String sha256Hex(Path file) throws IOException {
    try (InputStream in = new DigestInputStream(Files.newInputStream(file), MessageDigest.getInstance("SHA-256"))) {
      in.transferTo(OutputStream.nullOutputStream());
      return HexFormat.of().formatHex(((DigestInputStream) in).getMessageDigest().digest());
    } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
}
```

`ImageSniffer.java`:
```java
package com.cajamarca.sgi.comando.storage;
import java.io.*;
import java.nio.file.*;
public final class ImageSniffer {
  private ImageSniffer() {}
  public static String detect(byte[] b) {
    if (b == null || b.length < 3) return null;
    if ((b[0]&0xFF)==0xFF && (b[1]&0xFF)==0xD8 && (b[2]&0xFF)==0xFF) return "image/jpeg";
    if (b.length >= 8 && (b[0]&0xFF)==0x89 && b[1]=='P' && b[2]=='N' && b[3]=='G' && b[4]==0x0D && b[5]==0x0A && b[6]==0x1A && b[7]==0x0A) return "image/png";
    if (b.length >= 12 && b[0]=='R' && b[1]=='I' && b[2]=='F' && b[3]=='F' && b[8]=='W' && b[9]=='E' && b[10]=='B' && b[11]=='P') return "image/webp";
    return null;
  }
  public static String detect(Path file) throws IOException { try (InputStream in = Files.newInputStream(file)) { return detect(in.readNBytes(12)); } }
  public static String extension(String contentType) {
    return switch (contentType) { case "image/jpeg" -> "jpg"; case "image/png" -> "png"; case "image/webp" -> "webp"; default -> "bin"; };
  }
}
```

`StorageUnavailableException.java`:
```java
package com.cajamarca.sgi.comando.storage;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.*;
public class StorageUnavailableException extends WebApplicationException {
  public StorageUnavailableException(String message, Throwable cause) {
    super(message, cause, Response.status(503).entity(message).type(MediaType.TEXT_PLAIN_TYPE).build());
  }
}
```

`StorageService.java`:
```java
package com.cajamarca.sgi.comando.storage;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.io.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class StorageService {
  @ConfigProperty(name="sgi.minio.endpoint") String endpoint;
  @ConfigProperty(name="sgi.minio.access-key") String accessKey;
  @ConfigProperty(name="sgi.minio.secret-key") String secretKey;
  @ConfigProperty(name="sgi.storage.standard-bucket") String standardBucket;
  @ConfigProperty(name="sgi.storage.evidence-bucket") String evidenceBucket;
  private MinioClient client;
  private final Set<String> ready = ConcurrentHashMap.newKeySet();

  @PostConstruct void init() { client = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build(); }
  public String standardBucket() { return standardBucket; }
  public String evidenceBucket() { return evidenceBucket; }

  public void put(String bucket, String key, InputStream in, long size, String contentType) {
    ensureBucket(bucket);
    try { client.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(in, size, -1).contentType(contentType).build()); }
    catch (Exception e) { throw new StorageUnavailableException("No se pudo guardar el archivo; el almacenamiento no está disponible", e); }
  }
  public void put(String bucket, String key, byte[] bytes, String contentType) { put(bucket, key, new ByteArrayInputStream(bytes), bytes.length, contentType); }

  public byte[] read(String bucket, String key) {
    try (InputStream in = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) { return in.readAllBytes(); }
    catch (ErrorResponseException e) {
      if ("NoSuchKey".equals(e.errorResponse().code()) || "NoSuchBucket".equals(e.errorResponse().code())) throw new NotFoundException("Archivo no encontrado");
      throw new StorageUnavailableException("No se pudo leer el archivo; el almacenamiento no está disponible", e);
    } catch (Exception e) { throw new StorageUnavailableException("No se pudo leer el archivo; el almacenamiento no está disponible", e); }
  }
  public boolean exists(String bucket, String key) {
    try { client.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build()); return true; }
    catch (ErrorResponseException e) {
      if ("NoSuchKey".equals(e.errorResponse().code()) || "NoSuchBucket".equals(e.errorResponse().code())) return false;
      throw new StorageUnavailableException("No se pudo consultar el almacenamiento", e);
    } catch (Exception e) { throw new StorageUnavailableException("No se pudo consultar el almacenamiento", e); }
  }
  public void delete(String bucket, String key) {
    try { client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build()); }
    catch (Exception e) { throw new StorageUnavailableException("No se pudo eliminar el archivo", e); }
  }
  private void ensureBucket(String bucket) {
    if (ready.contains(bucket)) return;
    try {
      if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
      ready.add(bucket);
    } catch (Exception e) { throw new StorageUnavailableException("El almacenamiento de archivos no está disponible", e); }
  }
}
```
Nota: los buckets se crean de forma perezosa para que el backend arranque aunque MinIO esté caído.

- [ ] **Step 6: Correr pruebas**

Run: `powershell -File scripts/backend-test.ps1 -Test "ImageSnifferTest,StorageServiceTest"`
Expected: PASS (6 pruebas).
Run: `powershell -File scripts/backend-test.ps1`
Expected: todas PASS.

- [ ] **Step 7: Commit**
```bash
git add scripts/backend-test.ps1 backend/pom.xml backend/src/main/resources/application.properties backend/src/main/java/com/cajamarca/sgi/comando/storage backend/src/test/java/com/cajamarca/sgi/comando/storage backend/src/test/resources/fixtures
git commit -m "feat(storage): servicio MinIO, detección de imágenes y script de pruebas"
```

---

### Task 2: Fotos estándar en MinIO (Patrullas, Consignas, Bitácora)

**Files:**
- Create: `backend/src/main/resources/db/migration/V35__standard_images_object_storage.sql`, `storage/StandardImageStore.java`
- Modify: `patrols/PatrolCheckpoint.java`, `patrols/PatrolResource.java:236-262,297`, `consignments/ConsignmentEvidence.java`, `consignments/ConsignmentResource.java:177-205`, `bitacora/LogbookProtocolField.java`, `bitacora/BitacoraResource.java:180-238`
- Test: `storage/StandardImageStorageTest.java`, `operator/OperatorFixtures.java` (helpers REST de creación, se amplía en tareas siguientes)

**Interfaces:**
- Consumes: `StorageService`, `ImageSniffer`, `Digests` (Task 1).
- Produces:
  - `StandardImageStore.save(String module, byte[] bytes) -> StandardImageStore.Stored(String objectKey, String sha256, long size, String contentType)`; lanza `BadRequestException("Formato de imagen no permitido")` / `("La foto estándar no puede superar 5 MB")`.
  - `StandardImageStore.read(String objectKey, byte[] legacy) -> byte[]` (MinIO si hay clave; si no, bytea; si nada, 404).
  - `StandardImageStore.has(String objectKey, byte[] legacy) -> boolean`.
  - Columnas nuevas en las 3 tablas: `standard_image_object_key`, `standard_image_sha256`, `standard_image_size`; campos Java `standardImageObjectKey`, `standardImageSha256`, `standardImageSize`.
  - Endpoints multipart nuevos (mismo path que los actuales, `@Consumes(MULTIPART_FORM_DATA)`, campo `file`):
    - `POST /api/patrols/checkpoints/{id}/standard-image`
    - `POST /api/consignments/evidences/{id}/standard-image`
    - `POST /api/bitacora/fields/{id}/standard-image`
  - `OperatorFixtures` (test): `static final String PASSWORD="CajamarcaUAT!2026"`, `static final UUID POST_GGTT01=UUID.fromString("50000000-0000-0000-0000-000000000001")`, `static RequestSpecification as(String user)`, `static Map<String,Object> createDraftPatrolWithCheckpoint(double lat, double lon)` → claves `protocolId`, `patrolId`, `checkpointId`.

- [ ] **Step 1: Migración**

`V35__standard_images_object_storage.sql`:
```sql
-- Fotos estándar pasan a MinIO. standard_image_data queda como respaldo hasta migrar y se elimina en una versión posterior.
ALTER TABLE patrol_checkpoint ADD COLUMN standard_image_object_key varchar(300), ADD COLUMN standard_image_sha256 char(64), ADD COLUMN standard_image_size bigint;
ALTER TABLE consignment_evidence ADD COLUMN standard_image_object_key varchar(300), ADD COLUMN standard_image_sha256 char(64), ADD COLUMN standard_image_size bigint;
ALTER TABLE logbook_protocol_field ADD COLUMN standard_image_object_key varchar(300), ADD COLUMN standard_image_sha256 char(64), ADD COLUMN standard_image_size bigint;
```

- [ ] **Step 2: Prueba que falla**

`OperatorFixtures.java` (versión inicial):
```java
package com.cajamarca.sgi.comando.operator;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.*;
import static io.restassured.RestAssured.given;
public final class OperatorFixtures {
  private OperatorFixtures() {}
  public static final String PASSWORD = "CajamarcaUAT!2026";
  public static final UUID POST_GGTT01 = UUID.fromString("50000000-0000-0000-0000-000000000001");
  public static RequestSpecification as(String user) { return given().auth().preemptive().basic(user, PASSWORD); }
  public static Map<String,Object> createDraftPatrolWithCheckpoint(double lat, double lon) {
    String protocolId = as("coord").contentType(ContentType.JSON).body(Map.of("postId", POST_GGTT01, "name", "Prueba evidencias " + UUID.randomUUID()))
      .post("/api/patrols/protocols").then().statusCode(200).extract().path("id");
    String patrolId = as("coord").contentType(ContentType.JSON).body(Map.of("name", "Ronda prueba", "structureType", "CLOSED", "scheduleType", "UNPROGRAMMED"))
      .post("/api/patrols/protocols/" + protocolId + "/patrols").then().statusCode(200).extract().path("id");
    Map<String,Object> cp = new HashMap<>(Map.of("name", "Portón prueba", "description", "", "originMode", "FIELD", "latitude", lat, "longitude", lon,
      "gpsAccuracyM", 5.0, "controlType", "FOTOGRAFIA", "requiresEvidence", true));
    String checkpointId = as("coord").contentType(ContentType.JSON).body(cp)
      .post("/api/patrols/patrols/" + patrolId + "/checkpoints").then().statusCode(200).extract().path("id");
    return Map.of("protocolId", protocolId, "patrolId", patrolId, "checkpointId", checkpointId);
  }
}
```

`StandardImageStorageTest.java`:
```java
package com.cajamarca.sgi.comando.storage;
import com.cajamarca.sgi.comando.operator.OperatorFixtures;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.UUID;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static org.junit.jupiter.api.Assertions.*;
@QuarkusTest
class StandardImageStorageTest {
  @Inject EntityManager em;
  @Inject StorageService storage;
  @Inject StandardImageMigrator migrator;
  static final Path JPG = Path.of("src/test/resources/fixtures/sample.jpg");

  @Test void multipartUploadGoesToMinioAndReadsBack() throws Exception {
    String cp = (String) OperatorFixtures.createDraftPatrolWithCheckpoint(-2.17, -79.92).get("checkpointId");
    as("coord").multiPart("file", JPG.toFile(), "image/jpeg").post("/api/patrols/checkpoints/" + cp + "/standard-image")
      .then().statusCode(200).body("hasStandardImage", org.hamcrest.Matchers.is(true)).body("standardImageVersion", org.hamcrest.Matchers.is(1));
    Object[] row = (Object[]) em.createNativeQuery("select standard_image_object_key, standard_image_data is null, standard_image_sha256 from patrol_checkpoint where id=:id")
      .setParameter("id", UUID.fromString(cp)).getSingleResult();
    assertNotNull(row[0]); assertEquals(true, row[1]); assertEquals(Digests.sha256Hex(Files.readAllBytes(JPG)), row[2].toString());
    assertTrue(storage.exists(storage.standardBucket(), row[0].toString()));
    byte[] back = as("coord").get("/api/patrols/checkpoints/" + cp + "/standard-image").then().statusCode(200).extract().asByteArray();
    assertArrayEquals(Files.readAllBytes(JPG), back);
  }

  @Test void rejectsDisguisedText() {
    String cp = (String) OperatorFixtures.createDraftPatrolWithCheckpoint(-2.17, -79.92).get("checkpointId");
    as("coord").multiPart("file", Path.of("src/test/resources/fixtures/not-an-image.txt").toFile(), "image/jpeg")
      .post("/api/patrols/checkpoints/" + cp + "/standard-image").then().statusCode(400);
  }

  @Test void legacyByteaStillReadableAndMigrates() throws Exception {
    String cp = (String) OperatorFixtures.createDraftPatrolWithCheckpoint(-2.17, -79.92).get("checkpointId");
    byte[] bytes = Files.readAllBytes(Path.of("src/test/resources/fixtures/sample.png"));
    QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("update patrol_checkpoint set standard_image_data=:d, standard_image_content_type='image/png', standard_image_version=3, standard_image_object_key=null where id=:id")
      .setParameter("d", bytes).setParameter("id", UUID.fromString(cp)).executeUpdate());
    assertArrayEquals(bytes, as("coord").get("/api/patrols/checkpoints/" + cp + "/standard-image").then().statusCode(200).extract().asByteArray());
    assertTrue(migrator.migrateAll() >= 1);
    Object[] row = (Object[]) em.createNativeQuery("select standard_image_object_key, standard_image_data is null, standard_image_version from patrol_checkpoint where id=:id")
      .setParameter("id", UUID.fromString(cp)).getSingleResult();
    assertNotNull(row[0]); assertEquals(true, row[1]); assertEquals(3, ((Number) row[2]).intValue());
    assertArrayEquals(bytes, as("coord").get("/api/patrols/checkpoints/" + cp + "/standard-image").then().statusCode(200).extract().asByteArray());
  }
}
```

Run: `powershell -File scripts/backend-test.ps1 -Test StandardImageStorageTest`
Expected: FAIL de compilación (`StandardImageMigrator` no existe).

- [ ] **Step 3: StandardImageStore**

```java
package com.cajamarca.sgi.comando.storage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
@ApplicationScoped
public class StandardImageStore {
  public static final long MAX_BYTES = 5L * 1024 * 1024;
  public record Stored(String objectKey, String sha256, long size, String contentType) {}
  @Inject StorageService storage;
  public Stored save(String module, byte[] bytes) {
    if (bytes == null || bytes.length == 0) throw new BadRequestException("Imagen obligatoria");
    if (bytes.length > MAX_BYTES) throw new BadRequestException("La foto estándar no puede superar 5 MB");
    String ct = ImageSniffer.detect(bytes);
    if (ct == null) throw new BadRequestException("Formato de imagen no permitido");
    String sha = Digests.sha256Hex(bytes);
    String key = "standard/" + module + "/" + sha + "." + ImageSniffer.extension(ct);
    if (!storage.exists(storage.standardBucket(), key)) storage.put(storage.standardBucket(), key, bytes, ct);
    return new Stored(key, sha, bytes.length, ct);
  }
  public byte[] read(String objectKey, byte[] legacy) {
    if (objectKey != null) return storage.read(storage.standardBucket(), objectKey);
    if (legacy != null && legacy.length > 0) return legacy;
    throw new NotFoundException("No hay foto estándar");
  }
  public static boolean has(String objectKey, byte[] legacy) { return objectKey != null || (legacy != null && legacy.length > 0); }
}
```

- [ ] **Step 4: Entidades**

En `PatrolCheckpoint.java`, `ConsignmentEvidence.java` y `LogbookProtocolField.java` agregar junto a `standardImageData`:
```java
@Column(name="standard_image_object_key", length=300) public String standardImageObjectKey;
@Column(name="standard_image_sha256", length=64) public String standardImageSha256;
@Column(name="standard_image_size") public Long standardImageSize;
```

- [ ] **Step 5: PatrolResource**

1. `@Inject StandardImageStore standardImages;`
2. En `uploadImage` (octet-stream) reemplazar desde `if(bytes==null…` hasta `cp.standardImageContentType=ct;` por:
```java
StandardImageStore.Stored s=standardImages.save("patrol",bytes);
cp.standardImageObjectKey=s.objectKey();cp.standardImageSha256=s.sha256();cp.standardImageSize=s.size();cp.standardImageContentType=s.contentType();cp.standardImageData=null;
cp.standardImageOriginalName=clean(filename,"foto-estandar");
```
(mantener `standardImageVersion++`, `visintEnabled=false`, `pat.versionNo++`, etc.)
3. Nuevo endpoint multipart, justo debajo:
```java
@POST
@Path("/checkpoints/{checkpointId}/standard-image")
@Consumes(MediaType.MULTIPART_FORM_DATA)
@Transactional
@RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION"})
public CheckpointDto uploadImageForm(@PathParam("checkpointId") UUID checkpointId,@org.jboss.resteasy.reactive.RestForm("file") org.jboss.resteasy.reactive.multipart.FileUpload file) throws java.io.IOException{
    if(file==null)throw new BadRequestException("Imagen obligatoria");
    if(file.size()>StandardImageStore.MAX_BYTES)throw new BadRequestException("La foto estándar no puede superar 5 MB");
    return uploadImage(checkpointId,file.fileName(),file.contentType(),java.nio.file.Files.readAllBytes(file.uploadedFile()));
}
```
4. En `image(...)`: `byte[] data=standardImages.read(cp.standardImageObjectKey,cp.standardImageData);` y devolver `Response.ok(data)...` (quitar el `if(cp.standardImageData==null)`).
5. En `deleteImage(...)`: además poner `cp.standardImageObjectKey=null;cp.standardImageSha256=null;cp.standardImageSize=null;`. **No** borrar el objeto (otras versiones pueden compartirlo).
6. En `cloneCheckpoint(...)`: copiar `cp.standardImageObjectKey=old.standardImageObjectKey;cp.standardImageSha256=old.standardImageSha256;cp.standardImageSize=old.standardImageSize;`.
7. En `checkpointDto(...)` (línea 297): reemplazar `c.standardImageData!=null&&c.standardImageData.length>0` por `StandardImageStore.has(c.standardImageObjectKey,c.standardImageData)`.

- [ ] **Step 6: ConsignmentResource y BitacoraResource**

Aplicar exactamente los mismos 7 cambios con estos nombres:
- Consignas: módulo `"consignment"`, entidad `ConsignmentEvidence e`, métodos `uploadImage` / `image` / `deleteImage` / `cloneConsignment` (copia de evidencias, variable `oldE`) / `evidenceDto` (línea 205). Nuevo `uploadImageForm` en `@Path("/evidences/{id}/standard-image")`, mismos `@RolesAllowed` que `uploadImage`, delegando en `uploadImage(id, file.fileName(), file.contentType(), bytes)`.
- Bitácora: módulo `"bitacora"`, entidad `LogbookProtocolField field`, métodos `uploadStandardImage` / `standardImage` / `deleteStandardImage` / `cloneField` / `fieldDto` (línea 238). Nuevo `uploadStandardImageForm` en `@Path("/fields/{fieldId}/standard-image")`, mismos roles que `uploadStandardImage`.

- [ ] **Step 7: StandardImageMigrator**

```java
package com.cajamarca.sgi.comando.storage;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import java.util.*;
@ApplicationScoped
public class StandardImageMigrator {
  private static final Logger LOG = Logger.getLogger(StandardImageMigrator.class);
  private static final Map<String,String> TABLES = new LinkedHashMap<>(Map.of("patrol_checkpoint","patrol","consignment_evidence","consignment","logbook_protocol_field","bitacora"));
  @Inject EntityManager em;
  @Inject StandardImageStore store;
  @ConfigProperty(name="sgi.storage.migrate-standard-images") boolean enabled;

  @Scheduled(delayed="20s", every="1h", concurrentExecution=Scheduled.ConcurrentExecution.SKIP)
  void scheduled() { if (!enabled) return; try { int n = migrateAll(); if (n > 0) LOG.infof("Fotos estándar migradas a MinIO: %d", n); } catch (RuntimeException e) { LOG.warn("Migración de fotos estándar pendiente: " + e.getMessage()); } }

  public int migrateAll() { int total = 0; for (var t : TABLES.entrySet()) total += migrate(t.getKey(), t.getValue()); return total; }

  @SuppressWarnings("unchecked")
  int migrate(String table, String module) {
    int moved = 0;
    for (int guard = 0; guard < 1000; guard++) {
      List<Object[]> rows = QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
        "select id, standard_image_data from " + table + " where standard_image_object_key is null and standard_image_data is not null").setMaxResults(20).getResultList());
      if (rows.isEmpty()) return moved;
      for (Object[] r : rows) {
        UUID id = (UUID) r[0]; byte[] data = (byte[]) r[1];
        StandardImageStore.Stored s = store.save(module, data);
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("update " + table +
          " set standard_image_object_key=:k, standard_image_sha256=:s, standard_image_size=:z, standard_image_content_type=:ct, standard_image_data=null where id=:id and standard_image_object_key is null")
          .setParameter("k", s.objectKey()).setParameter("s", s.sha256()).setParameter("z", s.size()).setParameter("ct", s.contentType()).setParameter("id", id).executeUpdate());
        moved++;
      }
    }
    return moved;
  }
}
```

- [ ] **Step 8: Correr pruebas**

Run: `powershell -File scripts/backend-test.ps1 -Test StandardImageStorageTest`
Expected: PASS (3). Luego `powershell -File scripts/backend-test.ps1` → todas PASS.

- [ ] **Step 9: Verificación manual con la app levantada**

```bash
docker compose up -d --build backend
```
Esperar ~40 s y verificar que las 4 fotos existentes migraron:
```bash
docker compose exec -T postgres psql -U sgi -d sgi_comando -c "select count(*) filter (where standard_image_object_key is not null) migradas, count(*) filter (where standard_image_data is not null) pendientes from patrol_checkpoint"
```
Expected: `pendientes = 0`. Abrir la app → Patrullas → PRO-PAT-0004 → H01 → la foto se sigue viendo.

- [ ] **Step 10: Commit**
```bash
git add backend/src/main backend/src/test
git commit -m "feat(storage): fotos estándar en MinIO con respaldo bytea, migrador y carga multipart"
```

---

### Task 3: Mínimo y máximo de fotos por Hito

**Files:**
- Create: `backend/src/main/resources/db/migration/V36__patrol_checkpoint_evidence_counts.sql`
- Modify: `patrols/PatrolCheckpoint.java`, `patrols/PatrolResource.java` (records `CheckpointDto`, `SaveCheckpointRequest`; `saveCheckpoint`; `cloneCheckpoint`; `checkpointDto`)
- Test: `patrols/CheckpointEvidenceCountTest.java`

**Interfaces:**
- Produces: `PatrolCheckpoint.evidenceMinCount`, `evidenceMaxCount` (int); en `CheckpointDto` los campos `evidenceMinCount`, `evidenceMaxCount` (al final del record); en `SaveCheckpointRequest` los campos opcionales `Integer evidenceMinCount, Integer evidenceMaxCount` (al final; si vienen `null` se conservan los actuales).

- [ ] **Step 1: Migración**
```sql
ALTER TABLE patrol_checkpoint
  ADD COLUMN evidence_min_count integer NOT NULL DEFAULT 1,
  ADD COLUMN evidence_max_count integer NOT NULL DEFAULT 5,
  ADD CONSTRAINT ck_patrol_checkpoint_evidence_count
    CHECK (evidence_min_count BETWEEN 1 AND 5 AND evidence_max_count BETWEEN evidence_min_count AND 5);
```

- [ ] **Step 2: Prueba que falla**
```java
package com.cajamarca.sgi.comando.patrols;
import com.cajamarca.sgi.comando.operator.OperatorFixtures;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.as;
import static org.hamcrest.Matchers.*;
@QuarkusTest
class CheckpointEvidenceCountTest {
  Map<String,Object> body(Integer min, Integer max) {
    Map<String,Object> b = new HashMap<>(Map.of("name","Portón prueba","description","","originMode","FIELD","latitude",-2.17,"longitude",-79.92,
      "gpsAccuracyM",5.0,"controlType","FOTOGRAFIA","requiresEvidence",true,"standardImageNotes",""));
    b.put("evidenceMinCount", min); b.put("evidenceMaxCount", max); return b;
  }
  @Test void defaultsAndSave() {
    String cp = (String) OperatorFixtures.createDraftPatrolWithCheckpoint(-2.17,-79.92).get("checkpointId");
    as("coord").contentType(ContentType.JSON).body(body(null,null)).put("/api/patrols/checkpoints/"+cp)
      .then().statusCode(200).body("evidenceMinCount", is(1)).body("evidenceMaxCount", is(5));
    as("coord").contentType(ContentType.JSON).body(body(2,3)).put("/api/patrols/checkpoints/"+cp)
      .then().statusCode(200).body("evidenceMinCount", is(2)).body("evidenceMaxCount", is(3));
  }
  @Test void rejectsInvalidRanges() {
    String cp = (String) OperatorFixtures.createDraftPatrolWithCheckpoint(-2.17,-79.92).get("checkpointId");
    as("coord").contentType(ContentType.JSON).body(body(3,2)).put("/api/patrols/checkpoints/"+cp).then().statusCode(400);
    as("coord").contentType(ContentType.JSON).body(body(1,6)).put("/api/patrols/checkpoints/"+cp).then().statusCode(400);
    as("coord").contentType(ContentType.JSON).body(body(0,2)).put("/api/patrols/checkpoints/"+cp).then().statusCode(400);
  }
}
```
Run: `powershell -File scripts/backend-test.ps1 -Test CheckpointEvidenceCountTest` → FAIL (`evidenceMinCount` ausente).

- [ ] **Step 3: Implementación**

Entidad:
```java
@Column(name="evidence_min_count", nullable=false) public int evidenceMinCount = 1;
@Column(name="evidence_max_count", nullable=false) public int evidenceMaxCount = 5;
```
Records: agregar `,int evidenceMinCount,int evidenceMaxCount` al final de `CheckpointDto` y `,Integer evidenceMinCount,Integer evidenceMaxCount` al final de `SaveCheckpointRequest`; en `checkpointDto(...)` pasar `c.evidenceMinCount,c.evidenceMaxCount` al final.
En `saveCheckpoint(...)`, después de aplicar los campos existentes:
```java
int min=req.evidenceMinCount()==null?cp.evidenceMinCount:req.evidenceMinCount();
int max=req.evidenceMaxCount()==null?cp.evidenceMaxCount:req.evidenceMaxCount();
if(min<1||min>5||max<min||max>5)throw new BadRequestException("Las fotos por Hito deben cumplir 1 ≤ mínimo ≤ máximo ≤ 5");
cp.evidenceMinCount=min;cp.evidenceMaxCount=max;
```
En `cloneCheckpoint(...)`: `cp.evidenceMinCount=old.evidenceMinCount;cp.evidenceMaxCount=old.evidenceMaxCount;`.

- [ ] **Step 4: Pruebas** → `-Test CheckpointEvidenceCountTest` PASS; suite completa PASS.

- [ ] **Step 5: Commit**
```bash
git add backend/src
git commit -m "feat(patrullas): mínimo y máximo de fotos por Hito"
```

---

### Task 4: Modelo de evidencias y ejecuciones + datos UAT del agente

**Files:**
- Create: `backend/src/main/resources/db/migration/V37__operator_evidence_and_task_execution.sql`, `execution/EvidenceObject.java`, `execution/TaskExecution.java`, `database/uat-fixtures/DME_03_operador_agente_patrullas_UAT.sql`
- Modify: `backend/src/test/java/com/cajamarca/sgi/comando/operator/OperatorFixtures.java` (vincular agente y crear asignación)

**Interfaces:**
- Produces:
  - Tablas `evidence_object`, `task_execution`, `task_execution_evidence`; columnas nuevas en `patrol_execution`.
  - `EvidenceObject` (Panache, campos = columnas en camelCase; `static EvidenceObject byClientId(UUID tenant, UUID clientEvidenceId)`).
  - `TaskExecution` (Panache, campos = columnas en camelCase).
  - `OperatorFixtures.ensureAgentAssignment(EntityManager em) -> UUID assignmentId` (vincula `agente` a un empleado de Galvarino y crea/reutiliza una asignación en el turno de GGTT01 que contiene “ahora”, o el más cercano) y `OperatorFixtures.publishPatrolWithCheckpoint(double lat,double lon,int min,int max) -> Map` (claves `protocolId`, `patrolId`, `checkpointId`; protocolo publicado y ACTIVO en GGTT01).

- [ ] **Step 1: Migración**
```sql
-- Fotos del agente (almacenadas en MinIO) y ejecuciones de tareas. Fase 1: solo Hitos de patrulla.
ALTER TABLE patrol_execution ALTER COLUMN patrol_plan_id DROP NOT NULL;
ALTER TABLE patrol_execution ADD COLUMN patrol_definition_id uuid REFERENCES patrol_definition(id),
  ADD COLUMN assignment_id uuid REFERENCES operational_assignment(id), ADD COLUMN username varchar(80);

CREATE TABLE evidence_object (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  client_evidence_id uuid NOT NULL,
  upload_batch_id uuid NOT NULL,
  event_id uuid NOT NULL,
  assignment_id uuid NOT NULL REFERENCES operational_assignment(id),
  employee_id uuid NOT NULL,
  username varchar(80) NOT NULL,
  target_type varchar(40) NOT NULL,
  target_id uuid NOT NULL,
  bucket varchar(63) NOT NULL,
  object_key varchar(300) NOT NULL,
  content_type varchar(40) NOT NULL,
  size_bytes bigint NOT NULL,
  sha256 char(64) NOT NULL,
  captured_at timestamptz NOT NULL,
  latitude double precision, longitude double precision, accuracy_m double precision,
  source varchar(16) NOT NULL,
  flags varchar(200) NOT NULL DEFAULT '',
  status varchar(16) NOT NULL,
  received_at timestamptz NOT NULL,
  UNIQUE (instance_country_id, client_evidence_id),
  CHECK (size_bytes BETWEEN 1 AND 5242880),
  CHECK (target_type IN ('PATROL_CHECKPOINT')),
  CHECK (source IN ('CAMERA','GALLERY')),
  CHECK (status IN ('STORED','ATTACHED'))
);
CREATE INDEX ix_evidence_object_event ON evidence_object(instance_country_id, event_id);
CREATE INDEX ix_evidence_object_sha ON evidence_object(instance_country_id, sha256);

CREATE TABLE task_execution (
  id uuid PRIMARY KEY,
  instance_country_id uuid NOT NULL,
  execution_type varchar(40) NOT NULL,
  assignment_id uuid NOT NULL REFERENCES operational_assignment(id),
  shift_occurrence_id uuid NOT NULL,
  point_id uuid NOT NULL,
  post_id uuid NOT NULL,
  employee_id uuid NOT NULL,
  username varchar(80) NOT NULL,
  target_type varchar(40) NOT NULL,
  target_id uuid NOT NULL,
  protocol_id uuid NOT NULL,
  protocol_version_no integer NOT NULL,
  patrol_execution_id uuid REFERENCES patrol_execution(id),
  executed_at timestamptz NOT NULL,
  received_at timestamptz NOT NULL,
  latitude double precision, longitude double precision, accuracy_m double precision,
  observation varchar(1000),
  batch_id uuid NOT NULL, correlation_id uuid NOT NULL, device_id varchar(120) NOT NULL,
  payload_hash char(64) NOT NULL,
  payload_json text NOT NULL,
  status varchar(24) NOT NULL,
  CHECK (execution_type IN ('PATROL_CHECKPOINT_COMPLETED')),
  CHECK (status IN ('RECEIVED')),
  UNIQUE (instance_country_id, patrol_execution_id, target_id)
);
CREATE INDEX ix_task_execution_post_time ON task_execution(instance_country_id, post_id, executed_at DESC);

CREATE TABLE task_execution_evidence (
  task_execution_id uuid NOT NULL REFERENCES task_execution(id),
  evidence_id uuid NOT NULL UNIQUE REFERENCES evidence_object(id),
  sort_order integer NOT NULL,
  PRIMARY KEY (task_execution_id, evidence_id)
);
```

- [ ] **Step 2: Entidades**

`execution/EvidenceObject.java`:
```java
package com.cajamarca.sgi.comando.execution;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="evidence_object")
public class EvidenceObject extends PanacheEntityBase {
  @Id public UUID id;
  @Column(name="instance_country_id",nullable=false) public UUID instanceCountryId;
  @Column(name="client_evidence_id",nullable=false) public UUID clientEvidenceId;
  @Column(name="upload_batch_id",nullable=false) public UUID uploadBatchId;
  @Column(name="event_id",nullable=false) public UUID eventId;
  @Column(name="assignment_id",nullable=false) public UUID assignmentId;
  @Column(name="employee_id",nullable=false) public UUID employeeId;
  @Column(nullable=false,length=80) public String username;
  @Column(name="target_type",nullable=false,length=40) public String targetType;
  @Column(name="target_id",nullable=false) public UUID targetId;
  @Column(nullable=false,length=63) public String bucket;
  @Column(name="object_key",nullable=false,length=300) public String objectKey;
  @Column(name="content_type",nullable=false,length=40) public String contentType;
  @Column(name="size_bytes",nullable=false) public long sizeBytes;
  @Column(nullable=false,length=64) public String sha256;
  @Column(name="captured_at",nullable=false) public Instant capturedAt;
  public Double latitude; public Double longitude;
  @Column(name="accuracy_m") public Double accuracyM;
  @Column(nullable=false,length=16) public String source;
  @Column(nullable=false,length=200) public String flags="";
  @Column(nullable=false,length=16) public String status;
  @Column(name="received_at",nullable=false) public Instant receivedAt;
  public static EvidenceObject byClientId(UUID tenant, UUID clientEvidenceId) { return find("instanceCountryId=?1 and clientEvidenceId=?2", tenant, clientEvidenceId).firstResult(); }
}
```
`execution/TaskExecution.java` — misma forma, un campo por columna de `task_execution` (`executionType`, `assignmentId`, `shiftOccurrenceId`, `pointId`, `postId`, `employeeId`, `username`, `targetType`, `targetId`, `protocolId`, `protocolVersionNo` (int), `patrolExecutionId`, `executedAt`, `receivedAt`, `latitude`, `longitude`, `accuracyM`, `observation`, `batchId`, `correlationId`, `deviceId`, `payloadHash`, `payloadJson` (`columnDefinition="text"`), `status`).

- [ ] **Step 3: Fixture UAT (SQL)**

`database/uat-fixtures/DME_03_operador_agente_patrullas_UAT.sql`:
```sql
-- UAT | Vincula el usuario 'agente' a un empleado de Galvarino y lo asigna a los turnos de GGTT01
-- desde ayer hasta 14 días. Idempotente. Requiere que existan planes de asignación de la semana.
BEGIN;
INSERT INTO operator_employee_binding(instance_country_id, username, employee_id, active)
SELECT '11111111-1111-1111-1111-111111111111', 'agente', e.employee_id, true
FROM employee_operational_snapshot e
WHERE e.company_id='20000000-0000-0000-0000-000000000001'
ORDER BY e.full_name LIMIT 1
ON CONFLICT (instance_country_id, username) DO NOTHING;

INSERT INTO operational_assignment(id, instance_country_id, assignment_plan_id, shift_occurrence_id, employee_id, status, created_at, updated_at, assigned_by_username, assigned_at)
SELECT gen_random_uuid(), s.instance_country_id, ap.id, s.id, b.employee_id, 'PUBLISHED', now(), now(), 'uat-fixture', now()
FROM shift_occurrence s
JOIN assignment_plan ap ON ap.instance_country_id=s.instance_country_id AND ap.company_id='20000000-0000-0000-0000-000000000001'
  AND ap.week_start=date_trunc('week', s.starts_at)::date
JOIN operator_employee_binding b ON b.instance_country_id=s.instance_country_id AND b.username='agente'
WHERE s.post_id='50000000-0000-0000-0000-000000000001'
  AND s.ends_at >= now() - interval '1 day' AND s.starts_at <= now() + interval '14 days'
  AND NOT EXISTS (SELECT 1 FROM operational_assignment a WHERE a.shift_occurrence_id=s.id AND a.status<>'REMOVED');
COMMIT;
```
Aplicar a la base local:
```bash
docker compose exec -T postgres psql -U sgi -d sgi_comando -f - < database/uat-fixtures/DME_03_operador_agente_patrullas_UAT.sql
docker compose exec -T postgres psql -U sgi -d sgi_comando -c "select count(*) from operational_assignment a join operator_employee_binding b on b.employee_id=a.employee_id where b.username='agente'"
```
Expected: count > 0. Agregar `SGI_OPERATOR_RELIEF_UAT_ENABLED=true` al `.env` local (no versionado).

- [ ] **Step 4: Fixtures de prueba (Java)**

Agregar a `OperatorFixtures`:
```java
public static UUID ensureAgentAssignment(jakarta.persistence.EntityManager em) {
  return io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().call(() -> {
    em.createNativeQuery("""
      insert into operator_employee_binding(instance_country_id,username,employee_id,active)
      select '11111111-1111-1111-1111-111111111111','agente',e.employee_id,true from employee_operational_snapshot e
      where e.company_id='20000000-0000-0000-0000-000000000001' order by e.full_name limit 1
      on conflict (instance_country_id,username) do nothing""").executeUpdate();
    Object shift = em.createNativeQuery("""
      select s.id from shift_occurrence s where s.post_id=:post order by abs(extract(epoch from (s.starts_at - now()))) limit 1""")
      .setParameter("post", POST_GGTT01).getSingleResult();
    java.util.List<?> existing = em.createNativeQuery("""
      select a.id from operational_assignment a join operator_employee_binding b on b.employee_id=a.employee_id and b.username='agente'
      where a.shift_occurrence_id=:s and a.status<>'REMOVED'""").setParameter("s", shift).getResultList();
    if (!existing.isEmpty()) return (UUID) existing.get(0);
    em.createNativeQuery("update operational_assignment set status='REMOVED' where shift_occurrence_id=:s and status<>'REMOVED'").setParameter("s", shift).executeUpdate();
    UUID id = UUID.randomUUID();
    em.createNativeQuery("""
      insert into operational_assignment(id,instance_country_id,assignment_plan_id,shift_occurrence_id,employee_id,status,created_at,updated_at)
      select :id, s.instance_country_id, ap.id, s.id, b.employee_id, 'PUBLISHED', now(), now()
      from shift_occurrence s
      join assignment_plan ap on ap.instance_country_id=s.instance_country_id and ap.company_id='20000000-0000-0000-0000-000000000001'
      join operator_employee_binding b on b.username='agente' and b.instance_country_id=s.instance_country_id
      where s.id=:s order by abs(ap.week_start - date_trunc('week', s.starts_at)::date) limit 1""")
      .setParameter("id", id).setParameter("s", shift).executeUpdate();
    return id;
  });
}

public static Map<String,Object> publishPatrolWithCheckpoint(double lat, double lon, int min, int max) {
  Map<String,Object> ids = createDraftPatrolWithCheckpoint(lat, lon);
  Map<String,Object> save = new HashMap<>(Map.of("name","Portón prueba","description","","originMode","FIELD","latitude",lat,"longitude",lon,
    "gpsAccuracyM",5.0,"controlType","FOTOGRAFIA","requiresEvidence",true,"standardImageNotes",""));
  save.put("evidenceMinCount", min); save.put("evidenceMaxCount", max);
  as("coord").contentType(io.restassured.http.ContentType.JSON).body(save).put("/api/patrols/checkpoints/" + ids.get("checkpointId")).then().statusCode(200);
  as("coord").multiPart("file", java.nio.file.Path.of("src/test/resources/fixtures/sample.jpg").toFile(), "image/jpeg")
    .post("/api/patrols/checkpoints/" + ids.get("checkpointId") + "/standard-image").then().statusCode(200);
  as("coord").post("/api/patrols/protocols/" + ids.get("protocolId") + "/publish").then().statusCode(200).body("status", org.hamcrest.Matchers.is("ACTIVO"));
  return ids;
}
```
Nota: si `publish` deja el protocolo `INACTIVO` porque la creación no agrega el alcance del puesto, antes de publicar llamar `PUT /api/patrols/protocols/{id}` con `{"name":..., "description":"", "applicablePostIds":[POST_GGTT01]}`.

- [ ] **Step 5: Verificar migración**

Run: `powershell -File scripts/backend-test.ps1` → todas PASS (Flyway aplica V37 en la base de prueba).

- [ ] **Step 6: Commit**
```bash
git add backend/src database/uat-fixtures/DME_03_operador_agente_patrullas_UAT.sql
git commit -m "feat(operador): modelo de evidencias y ejecuciones, datos UAT del agente"
```

---

### Task 5: OperatorContext y patrullas en `/runtime`

**Files:**
- Create: `operator/OperatorContext.java`, `operator/OperatorPatrols.java`
- Modify: `operator/OperatorResource.java` (usar `OperatorContext`; `runtime` agrega `patrols`)
- Test: `operator/OperatorRuntimeTest.java`

**Interfaces:**
- Produces:
  - `OperatorContext` (`@RequestScoped`): `AppUser actor()`, `UUID employee()`, `Assignment assignment(UUID assignmentId, UUID employee)`, `void lock(UUID assignmentId)`, `String username()`; `record Assignment(OperationalAssignmentEntity assignment, ShiftOccurrenceEntity shift, PostEntity post, PointEntity point)`.
  - `OperatorPatrols` (`@ApplicationScoped`): `ArrayNode runtime(UUID postId)`; `Applicable requireCheckpoint(UUID checkpointId, UUID postId)` con `record Applicable(PatrolProtocol protocol, PatrolDefinition patrol, PatrolCheckpoint checkpoint)`; lanza `BadRequestException("El Hito no pertenece a una patrulla activa de este puesto")`.
  - JSON de `GET /api/v1/operator/runtime?assignmentId=…` agrega, **al mismo nivel que `relief`** (sin tocar `configurationVersion` del relevo):
```json
"patrols":[{"protocolId":"…","protocolCode":"PRO-PAT-0005","protocolVersion":1,"patrolId":"…","code":"PAT-001","name":"…",
  "structureType":"CLOSED","scheduleType":"UNPROGRAMMED","sequenceType":"STRICT","windowStart":null,"windowEnd":null,
  "checkpoints":[{"checkpointId":"…","code":"H01","name":"…","description":"…","sortOrder":1,"requiresEvidence":true,
    "evidenceMinCount":1,"evidenceMaxCount":5,"hasStandardImage":true,"standardImageVersion":1,"standardImageNotes":"…",
    "latitude":-2.17,"longitude":-79.92,"radiusM":50}]}]
```

- [ ] **Step 1: Prueba que falla**
```java
package com.cajamarca.sgi.comando.operator;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.*;
import static org.hamcrest.Matchers.*;
@QuarkusTest
class OperatorRuntimeTest {
  @Inject EntityManager em;
  @Test void runtimeIncludesActivePatrolCheckpoints() {
    UUID assignment = ensureAgentAssignment(em);
    Map<String,Object> ids = publishPatrolWithCheckpoint(-2.17, -79.92, 2, 4);
    as("agente").queryParam("assignmentId", assignment).get("/api/v1/operator/runtime").then().statusCode(200)
      .body("relief.configurationVersion", notNullValue())
      .body("patrols.find{it.protocolId=='" + ids.get("protocolId") + "'}.checkpoints[0].evidenceMinCount", is(2))
      .body("patrols.find{it.protocolId=='" + ids.get("protocolId") + "'}.checkpoints[0].evidenceMaxCount", is(4))
      .body("patrols.find{it.protocolId=='" + ids.get("protocolId") + "'}.checkpoints[0].hasStandardImage", is(true));
  }
  @Test void draftProtocolsAreNotExposed() {
    UUID assignment = ensureAgentAssignment(em);
    Map<String,Object> draft = createDraftPatrolWithCheckpoint(-2.17, -79.92);
    as("agente").queryParam("assignmentId", assignment).get("/api/v1/operator/runtime").then().statusCode(200)
      .body("patrols.protocolId", not(hasItem(draft.get("protocolId"))));
  }
}
```
Run: `-Test OperatorRuntimeTest` → FAIL (`patrols` ausente).

- [ ] **Step 2: OperatorContext**

Mover de `OperatorResource` a `OperatorContext` los métodos `actor()`, `employee()`, `assignment(...)` (renombrando el record a `Assignment`) y `lock(...)`, sin cambiar su lógica; agregar `public String username(){return identity.getPrincipal().getName();}`. En `OperatorResource` inyectar `@Inject OperatorContext ctx;` y reemplazar las llamadas (`employee()` → `ctx.employee()`, etc.). Correr `-Test ReliefContractTest` → PASS (sin cambios de comportamiento).

- [ ] **Step 3: OperatorPatrols**
```java
package com.cajamarca.sgi.comando.operator;
import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.patrols.*;
import com.cajamarca.sgi.comando.storage.StandardImageStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.BadRequestException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.util.*;
@ApplicationScoped
public class OperatorPatrols {
  public record Applicable(PatrolProtocol protocol, PatrolDefinition patrol, PatrolCheckpoint checkpoint) {}
  @Inject TenantContext tenant; @Inject EntityManager em; @Inject ObjectMapper mapper;
  @ConfigProperty(name="sgi.evidence.default-radius-m") int defaultRadius;

  @SuppressWarnings("unchecked")
  List<PatrolProtocol> activeFor(UUID postId) {
    List<UUID> ids = em.createNativeQuery("""
      select p.id from patrol_protocol p where p.instance_country_id=:t and p.status='ACTIVO'
      and exists (select 1 from patrol_protocol_post_scope s where s.protocol_id=p.id and s.post_id=:post and s.instance_country_id=p.instance_country_id)
      order by p.code""").setParameter("t", tenant.instanceCountryId()).setParameter("post", postId).getResultList();
    return ids.isEmpty() ? List.of() : PatrolProtocol.list("id in ?1 order by code", ids);
  }
  public ArrayNode runtime(UUID postId) {
    ArrayNode out = mapper.createArrayNode();
    for (PatrolProtocol p : activeFor(postId)) {
      for (PatrolDefinition d : PatrolDefinition.<PatrolDefinition>list("protocolId=?1 and instanceCountryId=?2 order by code", p.id, tenant.instanceCountryId())) {
        ObjectNode n = out.addObject().put("protocolId", p.id.toString()).put("protocolCode", p.code).put("protocolVersion", p.versionNo)
          .put("patrolId", d.id.toString()).put("code", d.code).put("name", d.name).put("structureType", d.structureType)
          .put("scheduleType", d.scheduleType).put("sequenceType", d.sequenceType)
          .put("windowStart", d.windowStart == null ? null : d.windowStart.toString()).put("windowEnd", d.windowEnd == null ? null : d.windowEnd.toString());
        ArrayNode cps = n.putArray("checkpoints");
        for (PatrolCheckpoint c : PatrolCheckpoint.<PatrolCheckpoint>list("patrolId=?1 and instanceCountryId=?2 order by sortOrder", d.id, tenant.instanceCountryId())) {
          cps.addObject().put("checkpointId", c.id.toString()).put("code", c.code).put("name", c.name).put("description", c.description)
            .put("sortOrder", c.sortOrder).put("requiresEvidence", c.requiresEvidence).put("evidenceMinCount", c.evidenceMinCount)
            .put("evidenceMaxCount", c.evidenceMaxCount).put("hasStandardImage", StandardImageStore.has(c.standardImageObjectKey, c.standardImageData))
            .put("standardImageVersion", c.standardImageVersion).put("standardImageNotes", c.standardImageNotes)
            .put("latitude", c.latitude).put("longitude", c.longitude).put("radiusM", c.radiusM == null ? defaultRadius : c.radiusM);
        }
      }
    }
    return out;
  }
  public Applicable requireCheckpoint(UUID checkpointId, UUID postId) {
    PatrolCheckpoint c = PatrolCheckpoint.find("id=?1 and instanceCountryId=?2", checkpointId, tenant.instanceCountryId()).firstResult();
    PatrolDefinition d = c == null ? null : PatrolDefinition.find("id=?1 and instanceCountryId=?2", c.patrolId, tenant.instanceCountryId()).firstResult();
    PatrolProtocol p = d == null ? null : activeFor(postId).stream().filter(x -> x.id.equals(d.protocolId)).findFirst().orElse(null);
    if (p == null) throw new BadRequestException("El Hito no pertenece a una patrulla activa de este puesto");
    return new Applicable(p, d, c);
  }
}
```
Nota: `standardImageData` se carga al leer la entidad; tras la migración es `null`.

- [ ] **Step 4: Runtime**

En `OperatorResource.runtime(...)`, dentro de `if(assignmentId!=null)`, antes del `return`:
```java
var c=ctx.assignment(assignmentId,employee);
response.set("relief",context(c));
response.set("patrols",patrols.runtime(c.post().id));
return response;
```
(con `@Inject OperatorPatrols patrols;`).

- [ ] **Step 5: Pruebas** → `-Test "OperatorRuntimeTest,ReliefContractTest"` PASS; suite completa PASS.

- [ ] **Step 6: Commit**
```bash
git add backend/src
git commit -m "feat(operador): contexto compartido y patrullas activas en runtime"
```

---

### Task 6: Contrato puro de carga de evidencias

**Files:**
- Create: `operator/EvidenceUploadContract.java`
- Test: `operator/EvidenceUploadContractTest.java`

**Interfaces:**
- Produces:
  - `record Item(UUID clientEvidenceId, Instant capturedAt, Double latitude, Double longitude, Double accuracyM, String source, String sha256)`
  - `record Metadata(UUID uploadBatchId, UUID eventId, UUID assignmentId, String targetType, UUID targetId, List<Item> items)`
  - `static Metadata parse(String json, ObjectMapper mapper)` — `BadRequestException("Metadatos inválidos: …")`.
  - `static Map<UUID,Integer> matchFiles(Metadata m, List<String> fileNames, int maxFiles, Instant now)` — devuelve `clientEvidenceId → índice del archivo`; valida: 1..maxFiles ítems, mismo número de archivos, ids únicos, nombre de archivo sin extensión = `clientEvidenceId`, cada ítem con archivo, `capturedAt ≤ now+5min`, `source ∈ {CAMERA,GALLERY}`, `targetType == PATROL_CHECKPOINT`, `sha256` 64 hex minúsculas.

- [ ] **Step 1: Prueba que falla**
```java
package com.cajamarca.sgi.comando.operator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class EvidenceUploadContractTest {
  final ObjectMapper m = new ObjectMapper(); final Instant now = Instant.parse("2026-09-29T22:00:00Z");
  final String sha = "a".repeat(64);
  ObjectNode meta(int n) {
    ObjectNode o = m.createObjectNode().put("uploadBatchId", UUID.randomUUID().toString()).put("eventId", UUID.randomUUID().toString())
      .put("assignmentId", UUID.randomUUID().toString()).put("targetType", "PATROL_CHECKPOINT").put("targetId", UUID.randomUUID().toString());
    ArrayNode items = o.putArray("items");
    for (int i = 0; i < n; i++) items.addObject().put("clientEvidenceId", UUID.randomUUID().toString()).put("capturedAt", now.toString())
      .put("latitude", -2.17).put("longitude", -79.92).put("accuracyM", 8).put("source", "CAMERA").put("sha256", sha);
    return o;
  }
  List<String> names(ObjectNode o) { List<String> r = new ArrayList<>(); o.path("items").forEach(i -> r.add(i.path("clientEvidenceId").asText() + ".jpg")); return r; }
  @Test void acceptsThreePhotosInAnyOrder() {
    ObjectNode o = meta(3); List<String> files = names(o); Collections.reverse(files);
    var md = EvidenceUploadContract.parse(o.toString(), m);
    Map<UUID,Integer> idx = EvidenceUploadContract.matchFiles(md, files, 5, now);
    assertEquals(3, idx.size());
    assertEquals(2, idx.get(md.items().get(0).clientEvidenceId()));
  }
  @Test void rejectsMissingOrExtraFile() {
    ObjectNode o = meta(2); var md = EvidenceUploadContract.parse(o.toString(), m);
    assertThrows(BadRequestException.class, () -> EvidenceUploadContract.matchFiles(md, names(o).subList(0, 1), 5, now));
    List<String> extra = new ArrayList<>(names(o)); extra.set(1, UUID.randomUUID() + ".jpg");
    assertThrows(BadRequestException.class, () -> EvidenceUploadContract.matchFiles(md, extra, 5, now));
  }
  @Test void rejectsTooManyAndDuplicates() {
    ObjectNode six = meta(6); assertThrows(BadRequestException.class, () -> EvidenceUploadContract.matchFiles(EvidenceUploadContract.parse(six.toString(), m), names(six), 5, now));
    ObjectNode dup = meta(2); ((ObjectNode) dup.path("items").get(1)).put("clientEvidenceId", dup.path("items").get(0).path("clientEvidenceId").asText());
    assertThrows(BadRequestException.class, () -> EvidenceUploadContract.matchFiles(EvidenceUploadContract.parse(dup.toString(), m), names(dup), 5, now));
  }
  @Test void rejectsFutureCaptureBadSourceBadShaBadTarget() {
    for (String[] kv : new String[][]{{"capturedAt", now.plusSeconds(600).toString()}, {"source", "SCREENSHOT"}, {"sha256", "XYZ"}}) {
      ObjectNode o = meta(1); ((ObjectNode) o.path("items").get(0)).put(kv[0], kv[1]);
      assertThrows(BadRequestException.class, () -> EvidenceUploadContract.matchFiles(EvidenceUploadContract.parse(o.toString(), m), names(o), 5, now), kv[0]);
    }
    ObjectNode t = meta(1).put("targetType", "RELIEF_PURPOSE");
    assertThrows(BadRequestException.class, () -> EvidenceUploadContract.matchFiles(EvidenceUploadContract.parse(t.toString(), m), names(t), 5, now));
  }
  @Test void rejectsGarbageJson() { assertThrows(BadRequestException.class, () -> EvidenceUploadContract.parse("{no json", m)); }
}
```
Run: `-Test EvidenceUploadContractTest` → FAIL de compilación.

- [ ] **Step 2: Implementación**
```java
package com.cajamarca.sgi.comando.operator;
import com.fasterxml.jackson.databind.*;
import jakarta.ws.rs.BadRequestException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
public final class EvidenceUploadContract {
  private EvidenceUploadContract() {}
  private static final Pattern SHA = Pattern.compile("^[0-9a-f]{64}$");
  public record Item(UUID clientEvidenceId, Instant capturedAt, Double latitude, Double longitude, Double accuracyM, String source, String sha256) {}
  public record Metadata(UUID uploadBatchId, UUID eventId, UUID assignmentId, String targetType, UUID targetId, List<Item> items) {}

  public static Metadata parse(String json, ObjectMapper mapper) {
    if (json == null || json.isBlank()) throw new BadRequestException("Metadatos inválidos: el campo metadata es obligatorio");
    try {
      JsonNode n = mapper.readTree(json);
      List<Item> items = new ArrayList<>();
      for (JsonNode i : n.path("items")) items.add(new Item(ReliefContract.uuid(i, "clientEvidenceId"), ReliefContract.time(i, "capturedAt"),
        num(i, "latitude"), num(i, "longitude"), num(i, "accuracyM"), ReliefContract.text(i, "source"), ReliefContract.text(i, "sha256")));
      return new Metadata(ReliefContract.uuid(n, "uploadBatchId"), ReliefContract.uuid(n, "eventId"), ReliefContract.uuid(n, "assignmentId"),
        ReliefContract.text(n, "targetType"), ReliefContract.uuid(n, "targetId"), items);
    } catch (BadRequestException e) { throw new BadRequestException("Metadatos inválidos: " + e.getMessage()); }
    catch (Exception e) { throw new BadRequestException("Metadatos inválidos: JSON mal formado"); }
  }
  public static Map<UUID,Integer> matchFiles(Metadata m, List<String> fileNames, int maxFiles, Instant now) {
    if (!"PATROL_CHECKPOINT".equals(m.targetType())) throw new BadRequestException("Tipo de destino no soportado: " + m.targetType());
    if (m.items().isEmpty() || m.items().size() > maxFiles) throw new BadRequestException("Se admiten entre 1 y " + maxFiles + " fotos por envío");
    if (fileNames.size() != m.items().size()) throw new BadRequestException("La cantidad de archivos no coincide con los metadatos");
    Map<String,Integer> byName = new HashMap<>();
    for (int i = 0; i < fileNames.size(); i++) {
      String f = fileNames.get(i) == null ? "" : fileNames.get(i); int dot = f.lastIndexOf('.');
      if (byName.put((dot > 0 ? f.substring(0, dot) : f).toLowerCase(Locale.ROOT), i) != null) throw new BadRequestException("Archivos duplicados");
    }
    Map<UUID,Integer> result = new LinkedHashMap<>();
    for (Item it : m.items()) {
      if (it.capturedAt().isAfter(now.plusSeconds(300))) throw new BadRequestException("Fecha de captura futura");
      if (!Set.of("CAMERA", "GALLERY").contains(it.source())) throw new BadRequestException("Origen de foto inválido");
      if (!SHA.matcher(it.sha256()).matches()) throw new BadRequestException("sha256 inválido");
      Integer idx = byName.get(it.clientEvidenceId().toString());
      if (idx == null) throw new BadRequestException("Falta el archivo de la foto " + it.clientEvidenceId());
      if (result.put(it.clientEvidenceId(), idx) != null) throw new BadRequestException("clientEvidenceId duplicado");
    }
    return result;
  }
  private static Double num(JsonNode n, String k) { return n.hasNonNull(k) && n.path(k).isNumber() ? n.path(k).asDouble() : null; }
}
```

- [ ] **Step 3: Pruebas** → PASS.

- [ ] **Step 4: Commit**
```bash
git add backend/src
git commit -m "feat(operador): contrato de metadatos para carga multipart de evidencias"
```

---

### Task 7: Endpoint `POST /api/v1/operator/evidences` (multipart, varias fotos)

**Files:**
- Create: `operator/GeoDistance.java`, `operator/OperatorEvidenceResource.java`
- Test: `operator/OperatorEvidenceResourceTest.java`

**Interfaces:**
- Consumes: `OperatorContext`, `OperatorPatrols.requireCheckpoint`, `EvidenceUploadContract`, `StorageService`, `ImageSniffer`, `Digests`, `EvidenceObject`.
- Produces:
  - `POST /api/v1/operator/evidences` — `multipart/form-data`, campos `metadata` (texto JSON) y `files` (1..5). Cabecera opcional `Idempotency-Key`.
  - Respuesta 200: `{"results":[{"clientEvidenceId","evidenceId","status":"STORED|ALREADY_STORED|REJECTED","reason":null|"FILE_TOO_LARGE|UNSUPPORTED_FORMAT|CHECKSUM_MISMATCH|TOO_MANY_PHOTOS","flags":["GALLERY","OUT_OF_RANGE","SUSPECTED_REUSE"]}]}`.
  - 409 si un `clientEvidenceId` ya existe con otro contenido; 400 por forma inválida; 503 si MinIO no responde.
  - `GET /api/v1/operator/checkpoints/{checkpointId}/standard-image?assignmentId=…` — foto guía para el agente.
  - `GeoDistance.meters(double lat1,double lon1,double lat2,double lon2)`.

- [ ] **Step 1: Prueba que falla**
```java
package com.cajamarca.sgi.comando.operator;
import com.cajamarca.sgi.comando.storage.Digests;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.*;
import static org.hamcrest.Matchers.*;
@QuarkusTest
class OperatorEvidenceResourceTest {
  @Inject EntityManager em; final ObjectMapper m = new ObjectMapper();
  static final Path JPG = Path.of("src/test/resources/fixtures/sample.jpg"), JPG2 = Path.of("src/test/resources/fixtures/sample2.jpg"),
    PNG = Path.of("src/test/resources/fixtures/sample.png"), TXT = Path.of("src/test/resources/fixtures/not-an-image.txt");
  UUID assignment; String checkpoint;
  @BeforeEach void setUp() { assignment = ensureAgentAssignment(em); checkpoint = (String) publishPatrolWithCheckpoint(-2.17, -79.92, 1, 5).get("checkpointId"); }

  ObjectNode meta(UUID eventId, List<UUID> ids, List<Path> files, double lat) throws Exception {
    ObjectNode o = m.createObjectNode().put("uploadBatchId", UUID.randomUUID().toString()).put("eventId", eventId.toString())
      .put("assignmentId", assignment.toString()).put("targetType", "PATROL_CHECKPOINT").put("targetId", checkpoint);
    ArrayNode items = o.putArray("items");
    for (int i = 0; i < ids.size(); i++) items.addObject().put("clientEvidenceId", ids.get(i).toString()).put("capturedAt", Instant.now().toString())
      .put("latitude", lat).put("longitude", -79.92).put("accuracyM", 8.5).put("source", "CAMERA").put("sha256", Digests.sha256Hex(Files.readAllBytes(files.get(i))));
    return o;
  }
  RequestSpecification form(ObjectNode meta, List<UUID> ids, List<Path> files) {
    RequestSpecification r = as("agente").multiPart("metadata", meta.toString());
    for (int i = 0; i < ids.size(); i++) r = r.multiPart("files", ids.get(i) + ".jpg", org.apache.commons.io.FileUtils.readFileToByteArray(files.get(i).toFile()), "image/jpeg");
    return r;
  }
  @Test void storesThreePhotosAndIsIdempotent() throws Exception {
    UUID event = UUID.randomUUID(); List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()); List<Path> files = List.of(JPG, JPG2, PNG);
    ObjectNode meta = meta(event, ids, files, -2.17);
    form(meta, ids, files).post("/api/v1/operator/evidences").then().statusCode(200).body("results.status", everyItem(is("STORED"))).body("results.size()", is(3));
    form(meta, ids, files).post("/api/v1/operator/evidences").then().statusCode(200).body("results.status", everyItem(is("ALREADY_STORED")));
    Number count = (Number) em.createNativeQuery("select count(*) from evidence_object where event_id=:e").setParameter("e", event).getSingleResult();
    Assertions.assertEquals(3, count.intValue());
  }
  @Test void acceptsMetadataLargerThanTwoKilobytes() throws Exception {
    UUID event = UUID.randomUUID(); List<UUID> ids = new ArrayList<>(); List<Path> files = new ArrayList<>();
    for (int i = 0; i < 5; i++) { ids.add(UUID.randomUUID()); files.add(JPG); }
    ObjectNode meta = meta(event, ids, files, -2.17);
    Assertions.assertTrue(meta.toString().length() > 2048);
    form(meta, ids, files).post("/api/v1/operator/evidences").then().statusCode(200).body("results.size()", is(5));
  }
  @Test void rejectsDisguisedFileAndFlagsOutOfRange() throws Exception {
    UUID event = UUID.randomUUID(); List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID()); List<Path> files = List.of(TXT, JPG);
    ObjectNode meta = meta(event, ids, files, -2.30);
    form(meta, ids, files).post("/api/v1/operator/evidences").then().statusCode(200)
      .body("results.find{it.clientEvidenceId=='" + ids.get(0) + "'}.status", is("REJECTED"))
      .body("results.find{it.clientEvidenceId=='" + ids.get(0) + "'}.reason", is("UNSUPPORTED_FORMAT"))
      .body("results.find{it.clientEvidenceId=='" + ids.get(1) + "'}.flags", hasItem("OUT_OF_RANGE"));
  }
  @Test void conflictWhenSameIdDifferentContent() throws Exception {
    UUID event = UUID.randomUUID(); List<UUID> ids = List.of(UUID.randomUUID());
    form(meta(event, ids, List.of(JPG), -2.17), ids, List.of(JPG)).post("/api/v1/operator/evidences").then().statusCode(200);
    form(meta(event, ids, List.of(PNG), -2.17), ids, List.of(PNG)).post("/api/v1/operator/evidences").then().statusCode(409);
  }
  @Test void rejectsCheckpointOfDraftProtocol() throws Exception {
    checkpoint = (String) createDraftPatrolWithCheckpoint(-2.17, -79.92).get("checkpointId");
    List<UUID> ids = List.of(UUID.randomUUID());
    form(meta(UUID.randomUUID(), ids, List.of(JPG), -2.17), ids, List.of(JPG)).post("/api/v1/operator/evidences").then().statusCode(400);
  }
  @Test void agentCanSeeStandardImage() {
    as("agente").queryParam("assignmentId", assignment).get("/api/v1/operator/checkpoints/" + checkpoint + "/standard-image").then().statusCode(200).contentType("image/jpeg");
  }
}
```
(`commons-io` llega transitivamente con RestAssured; si no, usar `Files.readAllBytes`.)

Run: `-Test OperatorEvidenceResourceTest` → FAIL (404 en `/evidences`).

- [ ] **Step 2: GeoDistance**
```java
package com.cajamarca.sgi.comando.operator;
public final class GeoDistance {
  private GeoDistance() {}
  public static double meters(double lat1, double lon1, double lat2, double lon2) {
    double r = 6371000, dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
    double a = Math.sin(dLat/2)*Math.sin(dLat/2) + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(dLon/2)*Math.sin(dLon/2);
    return 2 * r * Math.asin(Math.sqrt(a));
  }
}
```

- [ ] **Step 3: OperatorEvidenceResource**
```java
package com.cajamarca.sgi.comando.operator;
import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.execution.EvidenceObject;
import com.cajamarca.sgi.comando.storage.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import java.io.*;
import java.nio.file.Files;
import java.time.*;
import java.util.*;

@Path("/api/v1/operator") @Authenticated @Produces(MediaType.APPLICATION_JSON)
public class OperatorEvidenceResource {
  public record UploadResult(UUID clientEvidenceId, UUID evidenceId, String status, String reason, List<String> flags) {}
  public record UploadResponse(List<UploadResult> results) {}
  @Inject OperatorContext ctx; @Inject OperatorPatrols patrols; @Inject StorageService storage; @Inject StandardImageStore standardImages;
  @Inject TenantContext tenant; @Inject ObjectMapper mapper;
  @ConfigProperty(name="sgi.evidence.max-file-bytes") long maxFileBytes;
  @ConfigProperty(name="sgi.evidence.max-files-per-request") int maxFiles;

  @POST @Path("/evidences") @Consumes(MediaType.MULTIPART_FORM_DATA) @Transactional
  public UploadResponse upload(@RestForm("metadata") String metadataJson, @RestForm("files") List<FileUpload> files) throws IOException {
    UUID employee = ctx.employee();
    var meta = EvidenceUploadContract.parse(metadataJson, mapper);
    List<FileUpload> uploads = files == null ? List.of() : files;
    Map<UUID,Integer> index = EvidenceUploadContract.matchFiles(meta, uploads.stream().map(FileUpload::fileName).toList(), maxFiles, Instant.now());
    var a = ctx.assignment(meta.assignmentId(), employee);
    var target = patrols.requireCheckpoint(meta.targetId(), a.post().id);
    if (!target.checkpoint().requiresEvidence) throw new BadRequestException("Este Hito no requiere fotos");
    ctx.lock(meta.assignmentId());
    UUID t = tenant.instanceCountryId();

    // 1) conflictos antes de guardar nada
    Map<UUID,String> sha = new HashMap<>();
    for (var it : meta.items()) {
      FileUpload f = uploads.get(index.get(it.clientEvidenceId()));
      sha.put(it.clientEvidenceId(), f.size() > maxFileBytes ? null : Digests.sha256Hex(f.uploadedFile()));
      EvidenceObject prev = EvidenceObject.byClientId(t, it.clientEvidenceId());
      if (prev != null && (!prev.username.equals(ctx.username()) || !prev.eventId.equals(meta.eventId()) || !prev.sha256.equals(sha.get(it.clientEvidenceId()))))
        throw new ClientErrorException("La foto " + it.clientEvidenceId() + " ya fue cargada con otro contenido", 409);
    }
    long already = EvidenceObject.count("instanceCountryId=?1 and eventId=?2 and targetId=?3", t, meta.eventId(), meta.targetId());
    List<UploadResult> results = new ArrayList<>();
    for (var it : meta.items()) {
      FileUpload f = uploads.get(index.get(it.clientEvidenceId()));
      EvidenceObject prev = EvidenceObject.byClientId(t, it.clientEvidenceId());
      if (prev != null) { results.add(new UploadResult(it.clientEvidenceId(), prev.id, "ALREADY_STORED", null, flags(prev.flags))); continue; }
      if (f.size() > maxFileBytes) { results.add(rejected(it, "FILE_TOO_LARGE")); continue; }
      String ct = ImageSniffer.detect(f.uploadedFile());
      if (ct == null) { results.add(rejected(it, "UNSUPPORTED_FORMAT")); continue; }
      if (!sha.get(it.clientEvidenceId()).equals(it.sha256())) { results.add(rejected(it, "CHECKSUM_MISMATCH")); continue; }
      if (already >= target.checkpoint().evidenceMaxCount) { results.add(rejected(it, "TOO_MANY_PHOTOS")); continue; }
      EvidenceObject e = new EvidenceObject();
      e.id = UUID.randomUUID(); e.instanceCountryId = t; e.clientEvidenceId = it.clientEvidenceId(); e.uploadBatchId = meta.uploadBatchId();
      e.eventId = meta.eventId(); e.assignmentId = meta.assignmentId(); e.employeeId = employee; e.username = ctx.username();
      e.targetType = meta.targetType(); e.targetId = meta.targetId(); e.bucket = storage.evidenceBucket();
      ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
      e.objectKey = String.format("evidence/%04d/%02d/%s/%s.%s", now.getYear(), now.getMonthValue(), meta.eventId(), e.id, ImageSniffer.extension(ct));
      e.contentType = ct; e.sizeBytes = f.size(); e.sha256 = it.sha256(); e.capturedAt = it.capturedAt();
      e.latitude = it.latitude(); e.longitude = it.longitude(); e.accuracyM = it.accuracyM(); e.source = it.source();
      e.flags = String.join(",", computeFlags(e, target.checkpoint())); e.status = "STORED"; e.receivedAt = Instant.now();
      try (InputStream in = Files.newInputStream(f.uploadedFile())) { storage.put(e.bucket, e.objectKey, in, f.size(), ct); }
      e.persist(); already++;
      results.add(new UploadResult(it.clientEvidenceId(), e.id, "STORED", null, flags(e.flags)));
    }
    return new UploadResponse(results);
  }

  @GET @Path("/checkpoints/{checkpointId}/standard-image")
  public Response standardImage(@PathParam("checkpointId") UUID checkpointId, @QueryParam("assignmentId") UUID assignmentId) {
    var a = ctx.assignment(assignmentId, ctx.employee());
    var cp = patrols.requireCheckpoint(checkpointId, a.post().id).checkpoint();
    byte[] data = standardImages.read(cp.standardImageObjectKey, cp.standardImageData);
    return Response.ok(data).type(cp.standardImageContentType).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
  }

  private List<String> computeFlags(EvidenceObject e, com.cajamarca.sgi.comando.patrols.PatrolCheckpoint cp) {
    List<String> f = new ArrayList<>();
    if ("GALLERY".equals(e.source)) f.add("GALLERY");
    if (e.latitude != null && e.longitude != null && cp.latitude != null && cp.longitude != null) {
      double radius = cp.radiusM == null ? patrols.defaultRadius : cp.radiusM;
      if (GeoDistance.meters(e.latitude, e.longitude, cp.latitude, cp.longitude) > radius) f.add("OUT_OF_RANGE");
    }
    if (EvidenceObject.count("instanceCountryId=?1 and sha256=?2 and eventId<>?3", e.instanceCountryId, e.sha256, e.eventId) > 0) f.add("SUSPECTED_REUSE");
    return f;
  }
  private static List<String> flags(String csv) { return csv == null || csv.isBlank() ? List.of() : List.of(csv.split(",")); }
  private static UploadResult rejected(EvidenceUploadContract.Item it, String reason) { return new UploadResult(it.clientEvidenceId(), null, "REJECTED", reason, List.of()); }
}
```
`OperatorPatrols.defaultRadius` ya tiene visibilidad de paquete, así que `computeFlags` lo lee directamente.

- [ ] **Step 4: Pruebas** → `-Test OperatorEvidenceResourceTest` PASS (6). Suite completa PASS.

- [ ] **Step 5: Commit**
```bash
git add backend/src
git commit -m "feat(operador): carga multipart de varias fotos por Hito con idempotencia y alertas"
```

---

### Task 8: Ejecución del Hito (`PATROL_CHECKPOINT_COMPLETED`)

**Files:**
- Create: `operator/PatrolExecutionContract.java`, `operator/PatrolExecutionService.java`
- Modify: `operator/OperatorResource.java` (`submit` enruta por tipo)
- Test: `operator/PatrolExecutionContractTest.java`, `operator/PatrolExecutionTest.java`

**Interfaces:**
- Consumes: `OperatorContext`, `OperatorPatrols`, `EvidenceObject`, `TaskExecution`, `ReliefContract.text/uuid/time`.
- Produces:
  - Lote JSON aceptado por `POST /api/v1/operator/executions`:
```json
{"batchId":"…","correlationId":"…","employeeId":"…","instanceCountryId":"…","deviceId":"simulador-web","capturedAt":"…",
 "events":[{"type":"PATROL_CHECKPOINT_COMPLETED","eventId":"…","assignmentId":"…","patrolRunId":"…","patrolId":"…","checkpointId":"…",
   "executedAt":"…","latitude":-2.17,"longitude":-79.92,"accuracyM":8,"observation":"texto opcional","evidenceIds":["…","…"]}]}
```
  - Respuesta: `{"serverVersion":"operator-v1","acknowledgedEventIds":["…"],"rejectedEvents":[],"results":[{"eventId":"…","status":"RECEIVED","evidenceCount":2,"validationStatus":"NOT_REQUESTED"}]}`.
  - `PatrolExecutionContract.validate(JsonNode batch, UUID employee, UUID country, Instant now)`.
  - `PatrolExecutionService.submit(JsonNode batch) -> ObjectNode`.

- [ ] **Step 1: Pruebas del contrato (fallan)**
```java
package com.cajamarca.sgi.comando.operator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import jakarta.ws.rs.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class PatrolExecutionContractTest {
  final UUID employee = UUID.randomUUID(), country = UUID.randomUUID(); final Instant now = Instant.parse("2026-09-29T22:00:00Z");
  ObjectNode batch() {
    ObjectMapper m = new ObjectMapper(); ObjectNode b = m.createObjectNode().put("batchId", UUID.randomUUID().toString()).put("correlationId", UUID.randomUUID().toString())
      .put("employeeId", employee.toString()).put("instanceCountryId", country.toString()).put("deviceId", "test").put("capturedAt", now.toString());
    ObjectNode e = b.putArray("events").addObject().put("type", "PATROL_CHECKPOINT_COMPLETED");
    for (String k : new String[]{"eventId","assignmentId","patrolRunId","patrolId","checkpointId"}) e.put(k, UUID.randomUUID().toString());
    e.put("executedAt", now.toString()); e.putArray("evidenceIds").add(UUID.randomUUID().toString());
    return b;
  }
  ObjectNode ev(ObjectNode b) { return (ObjectNode) b.path("events").get(0); }
  @Test void accepts() { assertDoesNotThrow(() -> PatrolExecutionContract.validate(batch(), employee, country, now)); }
  @Test void acceptsNoPhotos() { var b = batch(); ev(b).putArray("evidenceIds"); assertDoesNotThrow(() -> PatrolExecutionContract.validate(b, employee, country, now)); }
  @Test void refusesDuplicatePhotoIds() { var b = batch(); String id = UUID.randomUUID().toString(); ev(b).putArray("evidenceIds").add(id).add(id);
    assertThrows(BadRequestException.class, () -> PatrolExecutionContract.validate(b, employee, country, now)); }
  @Test void refusesFutureExecution() { var b = batch(); ev(b).put("executedAt", now.plusSeconds(900).toString());
    assertThrows(BadRequestException.class, () -> PatrolExecutionContract.validate(b, employee, country, now)); }
  @Test void refusesLongObservation() { var b = batch(); ev(b).put("observation", "x".repeat(1001));
    assertThrows(BadRequestException.class, () -> PatrolExecutionContract.validate(b, employee, country, now)); }
  @Test void refusesOtherEmployee() { assertThrows(ForbiddenException.class, () -> PatrolExecutionContract.validate(batch(), UUID.randomUUID(), country, now)); }
}
```

- [ ] **Step 2: Contrato**
```java
package com.cajamarca.sgi.comando.operator;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.*;
import java.time.Instant;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.ReliefContract.*;
public final class PatrolExecutionContract {
  private PatrolExecutionContract() {}
  public static final String TYPE = "PATROL_CHECKPOINT_COMPLETED";
  public static void validate(JsonNode batch, UUID employee, UUID country, Instant now) {
    uuid(batch, "batchId"); uuid(batch, "correlationId");
    if (!employee.equals(uuid(batch, "employeeId")) || !country.equals(uuid(batch, "instanceCountryId"))) throw new ForbiddenException("Identidad o instancia incorrecta");
    if (text(batch, "deviceId").length() > 120) throw new BadRequestException("deviceId demasiado largo");
    Instant captured = time(batch, "capturedAt");
    JsonNode events = batch.path("events");
    if (!events.isArray() || events.size() != 1) throw new BadRequestException("Se admite un evento por lote");
    JsonNode e = events.get(0);
    if (!TYPE.equals(text(e, "type"))) throw new BadRequestException("Tipo de evento no soportado");
    for (String k : new String[]{"eventId","assignmentId","patrolRunId","patrolId","checkpointId"}) uuid(e, k);
    Instant executed = time(e, "executedAt");
    if (executed.isAfter(now.plusSeconds(300)) || executed.isAfter(captured.plusSeconds(300))) throw new BadRequestException("Fecha de ejecución futura");
    if (e.hasNonNull("observation") && e.path("observation").asText().length() > 1000) throw new BadRequestException("Observación demasiado larga");
    JsonNode ids = e.path("evidenceIds");
    if (!ids.isArray()) throw new BadRequestException("evidenceIds debe ser una lista");
    Set<String> seen = new HashSet<>();
    for (JsonNode id : ids) { try { UUID.fromString(id.asText()); } catch (IllegalArgumentException x) { throw new BadRequestException("evidenceId inválido"); }
      if (!seen.add(id.asText())) throw new BadRequestException("Foto repetida en la ejecución"); }
  }
}
```
Run: `-Test PatrolExecutionContractTest` → PASS.

- [ ] **Step 3: Prueba de integración (falla)**
```java
package com.cajamarca.sgi.comando.operator;
import com.cajamarca.sgi.comando.storage.Digests;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.OperatorFixtures.*;
import static org.hamcrest.Matchers.*;
@QuarkusTest
class PatrolExecutionTest {
  @Inject EntityManager em; final ObjectMapper m = new ObjectMapper();
  UUID assignment, employee; Map<String,Object> ids;
  @BeforeEach void setUp() {
    assignment = ensureAgentAssignment(em);
    employee = UUID.fromString(as("agente").queryParam("assignmentId", assignment).get("/api/v1/operator/runtime").then().extract().path("employeeId"));
    ids = publishPatrolWithCheckpoint(-2.17, -79.92, 2, 3);
  }
  List<String> upload(UUID event, int n, Object checkpoint) throws Exception {
    ObjectNode meta = m.createObjectNode().put("uploadBatchId", UUID.randomUUID().toString()).put("eventId", event.toString())
      .put("assignmentId", assignment.toString()).put("targetType", "PATROL_CHECKPOINT").put("targetId", checkpoint.toString());
    ArrayNode items = meta.putArray("items"); List<UUID> cids = new ArrayList<>();
    byte[] jpg = Files.readAllBytes(Path.of("src/test/resources/fixtures/sample.jpg"));
    for (int i = 0; i < n; i++) { UUID c = UUID.randomUUID(); cids.add(c); items.addObject().put("clientEvidenceId", c.toString()).put("capturedAt", Instant.now().toString())
      .put("latitude", -2.17).put("longitude", -79.92).put("source", "CAMERA").put("sha256", Digests.sha256Hex(jpg)); }
    var r = as("agente").multiPart("metadata", meta.toString());
    for (UUID c : cids) r = r.multiPart("files", c + ".jpg", jpg, "image/jpeg");
    return r.post("/api/v1/operator/evidences").then().statusCode(200).extract().path("results.evidenceId");
  }
  ObjectNode batch(UUID event, UUID run, List<String> evidenceIds) {
    ObjectNode b = m.createObjectNode().put("batchId", UUID.randomUUID().toString()).put("correlationId", UUID.randomUUID().toString())
      .put("employeeId", employee.toString()).put("instanceCountryId", "11111111-1111-1111-1111-111111111111").put("deviceId", "test").put("capturedAt", Instant.now().toString());
    ObjectNode e = b.putArray("events").addObject().put("type", "PATROL_CHECKPOINT_COMPLETED").put("eventId", event.toString()).put("assignmentId", assignment.toString())
      .put("patrolRunId", run.toString()).put("patrolId", ids.get("patrolId").toString()).put("checkpointId", ids.get("checkpointId").toString())
      .put("executedAt", Instant.now().toString()).put("latitude", -2.17).put("longitude", -79.92);
    ArrayNode arr = e.putArray("evidenceIds"); evidenceIds.forEach(arr::add); return b;
  }
  @Test void registersExecutionWithTwoPhotosAndIsIdempotent() throws Exception {
    UUID event = UUID.randomUUID(), run = UUID.randomUUID(); List<String> ev = upload(event, 2, ids.get("checkpointId"));
    ObjectNode b = batch(event, run, ev);
    as("agente").contentType(ContentType.JSON).body(b.toString()).post("/api/v1/operator/executions").then().statusCode(200)
      .body("results[0].status", is("RECEIVED")).body("results[0].evidenceCount", is(2));
    as("agente").contentType(ContentType.JSON).body(b.toString()).post("/api/v1/operator/executions").then().statusCode(200);
    Assertions.assertEquals(2, ((Number) em.createNativeQuery("select count(*) from task_execution_evidence where task_execution_id=:e").setParameter("e", event).getSingleResult()).intValue());
    Assertions.assertEquals("ATTACHED", em.createNativeQuery("select distinct status from evidence_object where event_id=:e").setParameter("e", event).getSingleResult());
  }
  @Test void refusesFewerThanMinimum() throws Exception {
    UUID event = UUID.randomUUID(); List<String> ev = upload(event, 1, ids.get("checkpointId"));
    as("agente").contentType(ContentType.JSON).body(batch(event, UUID.randomUUID(), ev).toString()).post("/api/v1/operator/executions").then().statusCode(400);
  }
  @Test void refusesPhotosFromAnotherEvent() throws Exception {
    List<String> foreign = upload(UUID.randomUUID(), 2, ids.get("checkpointId"));
    as("agente").contentType(ContentType.JSON).body(batch(UUID.randomUUID(), UUID.randomUUID(), foreign).toString()).post("/api/v1/operator/executions").then().statusCode(400);
  }
  @Test void reliefStillRoutesToReliefContract() {
    ObjectNode b = batch(UUID.randomUUID(), UUID.randomUUID(), List.of()); ((ObjectNode) b.path("events").get(0)).put("type", "RELIEF_SUBMITTED");
    as("agente").contentType(ContentType.JSON).body(b.toString()).post("/api/v1/operator/executions").then().statusCode(400)
      .body(containsString("Campo requerido"));
  }
}
```
Run: `-Test PatrolExecutionTest` → FAIL.

- [ ] **Step 4: PatrolExecutionService**
```java
package com.cajamarca.sgi.comando.operator;
import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.execution.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import java.time.Instant;
import java.util.*;
import static com.cajamarca.sgi.comando.operator.ReliefContract.*;
@ApplicationScoped
public class PatrolExecutionService {
  @Inject OperatorContext ctx; @Inject OperatorPatrols patrols; @Inject TenantContext tenant; @Inject EntityManager em; @Inject ObjectMapper mapper;

  @Transactional
  public ObjectNode submit(JsonNode batch) {
    UUID employee = ctx.employee(); UUID t = tenant.instanceCountryId();
    PatrolExecutionContract.validate(batch, employee, t, Instant.now());
    JsonNode e = batch.path("events").get(0);
    UUID eventId = uuid(e, "eventId"), assignmentId = uuid(e, "assignmentId"), runId = uuid(e, "patrolRunId"), checkpointId = uuid(e, "checkpointId");
    var a = ctx.assignment(assignmentId, employee); ctx.lock(assignmentId);
    String digest = OperatorResource.hash(e.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    TaskExecution prev = TaskExecution.find("id=?1 and instanceCountryId=?2", eventId, t).firstResult();
    if (prev != null) { if (!prev.payloadHash.equals(digest) || !prev.username.equals(ctx.username())) throw new ClientErrorException("Identificador reutilizado con datos diferentes", 409);
      return ack(eventId, countEvidence(eventId)); }
    Instant executed = time(e, "executedAt");
    if (executed.isBefore(a.shift().startsAt.minusSeconds(43200)) || executed.isAfter(a.shift().endsAt.plusSeconds(43200))) throw new BadRequestException("Fecha fuera de la ventana del turno");
    var target = patrols.requireCheckpoint(checkpointId, a.post().id);
    if (!target.patrol().id.equals(uuid(e, "patrolId"))) throw new BadRequestException("El Hito no pertenece a la patrulla indicada");
    List<UUID> evidenceIds = new ArrayList<>(); e.path("evidenceIds").forEach(x -> evidenceIds.add(UUID.fromString(x.asText())));
    var cp = target.checkpoint();
    int min = cp.requiresEvidence ? cp.evidenceMinCount : 0;
    if (evidenceIds.size() < min || evidenceIds.size() > cp.evidenceMaxCount)
      throw new BadRequestException("Este Hito requiere entre " + min + " y " + cp.evidenceMaxCount + " fotos; se enviaron " + evidenceIds.size());
    List<EvidenceObject> evidences = new ArrayList<>();
    for (UUID id : evidenceIds) {
      EvidenceObject ev = EvidenceObject.find("id=?1 and instanceCountryId=?2", id, t).firstResult();
      if (ev == null || !ev.eventId.equals(eventId) || !ev.assignmentId.equals(assignmentId) || !ev.username.equals(ctx.username())
          || !ev.targetId.equals(checkpointId) || !"STORED".equals(ev.status)) throw new BadRequestException("Foto no autorizada o no cargada: " + id);
      evidences.add(ev);
    }
    upsertPatrolRun(runId, target, a, employee, executed);
    TaskExecution x = new TaskExecution();
    x.id = eventId; x.instanceCountryId = t; x.executionType = PatrolExecutionContract.TYPE; x.assignmentId = assignmentId; x.shiftOccurrenceId = a.shift().id;
    x.pointId = a.point().id; x.postId = a.post().id; x.employeeId = employee; x.username = ctx.username(); x.targetType = "PATROL_CHECKPOINT"; x.targetId = checkpointId;
    x.protocolId = target.protocol().id; x.protocolVersionNo = target.protocol().versionNo; x.patrolExecutionId = runId; x.executedAt = executed; x.receivedAt = Instant.now();
    x.latitude = e.hasNonNull("latitude") ? e.path("latitude").asDouble() : null; x.longitude = e.hasNonNull("longitude") ? e.path("longitude").asDouble() : null;
    x.accuracyM = e.hasNonNull("accuracyM") ? e.path("accuracyM").asDouble() : null; x.observation = e.hasNonNull("observation") ? e.path("observation").asText() : null;
    x.batchId = uuid(batch, "batchId"); x.correlationId = uuid(batch, "correlationId"); x.deviceId = text(batch, "deviceId");
    x.payloadHash = digest; x.payloadJson = e.toString(); x.status = "RECEIVED";
    try { x.persistAndFlush(); } catch (jakarta.persistence.PersistenceException dup) { throw new ClientErrorException("Este Hito ya fue registrado en la ronda", 409); }
    for (int i = 0; i < evidences.size(); i++) {
      em.createNativeQuery("insert into task_execution_evidence(task_execution_id,evidence_id,sort_order) values(:t,:e,:o)")
        .setParameter("t", eventId).setParameter("e", evidences.get(i).id).setParameter("o", i + 1).executeUpdate();
      evidences.get(i).status = "ATTACHED";
    }
    return ack(eventId, evidences.size());
  }
  private int countEvidence(UUID eventId) { return ((Number) em.createNativeQuery("select count(*) from task_execution_evidence where task_execution_id=:e").setParameter("e", eventId).getSingleResult()).intValue(); }
  private void upsertPatrolRun(UUID runId, OperatorPatrols.Applicable target, OperatorContext.Assignment a, UUID employee, Instant executed) {
    List<?> row = em.createNativeQuery("select patrol_definition_id, assignment_id from patrol_execution where id=:id").setParameter("id", runId).getResultList();
    if (row.isEmpty()) {
      em.createNativeQuery("""
        insert into patrol_execution(id,instance_country_id,patrol_plan_id,occurrence_id,employee_id,started_at,created_at,updated_at,patrol_definition_id,assignment_id,username)
        values(:id,:t,null,null,:emp,:start,now(),now(),:def,:a,:u)""").setParameter("id", runId).setParameter("t", tenant.instanceCountryId())
        .setParameter("emp", employee).setParameter("start", executed).setParameter("def", target.patrol().id).setParameter("a", a.assignment().id).setParameter("u", ctx.username()).executeUpdate();
      return;
    }
    Object[] r = (Object[]) row.get(0);
    if (!target.patrol().id.equals(r[0]) || !a.assignment().id.equals(r[1])) throw new ClientErrorException("La ronda indicada pertenece a otra patrulla o asignación", 409);
  }
  private ObjectNode ack(UUID id, int evidenceCount) {
    ObjectNode n = mapper.createObjectNode().put("serverVersion", "operator-v1");
    n.putArray("acknowledgedEventIds").add(id.toString()); n.putArray("rejectedEvents");
    n.putArray("results").addObject().put("eventId", id.toString()).put("status", "RECEIVED").put("evidenceCount", evidenceCount).put("validationStatus", "NOT_REQUESTED");
    return n;
  }
}
```
`OperatorResource.hash(...)` hoy es `static String hash(byte[])` con visibilidad de paquete; se usa desde el mismo paquete (sin cambios).

- [ ] **Step 5: Enrutar en OperatorResource**

Al inicio de `submit(JsonNode batch)`:
```java
if(PatrolExecutionContract.TYPE.equals(batch.path("events").path(0).path("type").asText())) return patrolExecutions.submit(batch);
```
con `@Inject PatrolExecutionService patrolExecutions;`.

- [ ] **Step 6: Pruebas** → `-Test "PatrolExecutionContractTest,PatrolExecutionTest,ReliefContractTest"` PASS. Suite completa PASS.

- [ ] **Step 7: Commit**
```bash
git add backend/src
git commit -m "feat(operador): registro de ejecución de Hito con varias fotos"
```

---

### Task 9: Frontend — FormData y mín/máx en configuración

**Files:**
- Modify: `frontend/src/api.ts:21-36,77,99,122`, `frontend/src/pages/PatrolConfig.tsx:15,164,222`

**Interfaces:**
- Produces (en `api.ts`):
  - `export function multipartRequest<T>(path:string, form:FormData, opts?:{onProgress?:(pct:number)=>void; signal?:AbortSignal; idempotencyKey?:string}):Promise<T>`
  - `api.uploadBitacoraStandardImage`, `api.uploadPatrolStandardImage`, `api.uploadConsignmentStandardImage` con la misma firma pública que hoy, ahora por FormData (campo `file`).
  - `api.operatorRuntime(assignmentId?:string)`, `api.uploadEvidences(form:FormData, opts)`, `api.submitExecution(batch:any)`, `api.operatorCheckpointImage(checkpointId:string, assignmentId:string):Promise<Blob>`.

- [ ] **Step 1: Instalar dependencias locales (una vez)**
```bash
cd frontend && npm ci
```

- [ ] **Step 2: `api.ts`**

Reemplazar las tres funciones `bitacoraImageUpload`, `patrolImageUpload`, `consignmentImageUpload` por:
```ts
const authHeader=()=> 'Basic '+btoa(`${currentUser}:${PASSWORD}`);
export function multipartRequest<T>(path:string,form:FormData,opts:{onProgress?:(pct:number)=>void;signal?:AbortSignal;idempotencyKey?:string}={}):Promise<T>{
 return new Promise((resolve,reject)=>{
  const xhr=new XMLHttpRequest(); xhr.open('POST',`${API}${path}`); xhr.setRequestHeader('Authorization',authHeader());
  if(opts.idempotencyKey)xhr.setRequestHeader('Idempotency-Key',opts.idempotencyKey);
  xhr.upload.onprogress=e=>{if(e.lengthComputable)opts.onProgress?.(Math.round(e.loaded*100/e.total))};
  xhr.onload=()=>xhr.status>=200&&xhr.status<300?resolve(xhr.responseText?JSON.parse(xhr.responseText):undefined as T):reject(new ApiError(xhr.status,xhr.responseText));
  xhr.onerror=()=>reject(new ApiError(0,'Error de red: no se pudo contactar al servidor'));
  xhr.onabort=()=>reject(new ApiError(0,'Carga cancelada'));
  opts.signal?.addEventListener('abort',()=>xhr.abort());
  xhr.send(form);
 });
}
function standardImageUpload(path:string,file:File){const fd=new FormData();fd.append('file',file,file.name);return multipartRequest<any>(path,fd)}
const bitacoraImageUpload=(fieldId:string,file:File)=>standardImageUpload(`/api/bitacora/fields/${encodeURIComponent(fieldId)}/standard-image`,file);
const patrolImageUpload=(checkpointId:string,file:File)=>standardImageUpload(`/api/patrols/checkpoints/${encodeURIComponent(checkpointId)}/standard-image`,file);
const consignmentImageUpload=(evidenceId:string,file:File)=>standardImageUpload(`/api/consignments/evidences/${encodeURIComponent(evidenceId)}/standard-image`,file);
```
Dentro de `export const api={…}` agregar:
```ts
 operatorRuntime:(assignmentId?:string)=>request<any>(`/api/v1/operator/runtime${assignmentId?`?assignmentId=${encodeURIComponent(assignmentId)}`:''}`),
 uploadEvidences:(form:FormData,opts:{onProgress?:(pct:number)=>void;signal?:AbortSignal;idempotencyKey?:string})=>multipartRequest<any>('/api/v1/operator/evidences',form,opts),
 submitExecution:(batch:any)=>request<any>('/api/v1/operator/executions',{method:'POST',body:JSON.stringify(batch)}),
 operatorCheckpointImage:(checkpointId:string,assignmentId:string)=>binaryRequest(`/api/v1/operator/checkpoints/${encodeURIComponent(checkpointId)}/standard-image?assignmentId=${encodeURIComponent(assignmentId)}`),
```

- [ ] **Step 3: PatrolConfig — mín/máx**

1. Tipo `Checkpoint` (línea 15): agregar `evidenceMinCount:number;evidenceMaxCount:number;`.
2. `saveCheckpoint` (línea 164): en el objeto enviado a `api.savePatrolCheckpoint` agregar `evidenceMinCount:cp.evidenceMinCount,evidenceMaxCount:cp.evidenceMaxCount`.
3. `CheckpointEditor` (línea 222): justo después del `<label className="pat-check">…Requiere evidencia…</label>` insertar:
```tsx
{cp.requiresEvidence&&<div className="pat-evidence-count"><span>Fotos del agente</span><label>Mínimo<input type="number" min={1} max={5} value={cp.evidenceMinCount} onChange={e=>{const v=Math.max(1,Math.min(5,Number(e.target.value)||1));update('evidenceMinCount',v);if(v>cp.evidenceMaxCount)update('evidenceMaxCount',v)}}/></label><label>Máximo<input type="number" min={cp.evidenceMinCount} max={5} value={cp.evidenceMaxCount} onChange={e=>update('evidenceMaxCount',Math.max(cp.evidenceMinCount,Math.min(5,Number(e.target.value)||cp.evidenceMinCount)))}/></label><small>El agente deberá enviar entre {cp.evidenceMinCount} y {cp.evidenceMaxCount} fotos.</small></div>}
```
Nota: dos `update` seguidos sobre el mismo borrador pueden pisarse; si ocurre, cambiar a un único `setPatrolDraft` que actualice ambos campos.
4. `styles.css`: 
```css
.pat-evidence-count{display:grid;grid-template-columns:auto 1fr 1fr;gap:8px 12px;align-items:center;padding:10px 12px;border:1px solid var(--line,#e5e7eb);border-radius:10px}
.pat-evidence-count>span{font-weight:700;font-size:12px}
.pat-evidence-count label{display:flex;gap:6px;align-items:center;font-size:12px}
.pat-evidence-count input{width:64px}
.pat-evidence-count small{grid-column:1/-1;color:#64748b}
```

- [ ] **Step 4: Verificar compilación**
```bash
cd frontend && npm run build
```
Expected: sin errores de TypeScript.

- [ ] **Step 5: Verificar en la app**
```bash
docker compose up -d --build frontend backend
```
Correr el script E2E existente: `node C:/Users/USER/AppData/Local/Temp/claude/.../scratchpad/pw/subir_fotos_estandar.mjs` (o su copia en `C:/Proyectos/sgi_comando/evidencias_playwright/subir_fotos_estandar.mjs`) → los 3 flujos siguen `ok: true` (ahora suben por FormData). Abrir un Hito en borrador y comprobar que mín/máx se guardan.

- [ ] **Step 6: Commit**
```bash
git add frontend/src
git commit -m "feat(front): foto estándar por FormData y mínimo/máximo de fotos por Hito"
```

---

### Task 10: Frontend — Simulador de Agente (UAT)

**Files:**
- Create: `frontend/src/lib/evidenceUpload.ts`, `frontend/src/pages/AgentSimulator.tsx`
- Modify: `frontend/src/App.tsx`, `frontend/src/components/Sidebar.tsx`, `frontend/src/components/Header.tsx:3`, `frontend/src/styles.css`

**Interfaces:**
- Consumes: `api.operatorRuntime`, `api.uploadEvidences`, `api.submitExecution`, `api.operatorCheckpointImage` (Task 9).
- Produces (`evidenceUpload.ts`):
  - `type PickedPhoto={clientEvidenceId:string;file:File;previewUrl:string;sha256:string;problem?:string}`
  - `async function pickPhotos(files:FileList|File[], existing:number, max:number):Promise<PickedPhoto[]>` — valida tipo real (bytes mágicos) y ≤ 5 MB, calcula SHA-256, marca `problem` en español.
  - `function buildEvidenceForm(meta:{uploadBatchId:string;eventId:string;assignmentId:string;targetId:string}, photos:PickedPhoto[], gps?:{latitude:number;longitude:number;accuracyM:number}):FormData`

- [ ] **Step 1: `evidenceUpload.ts`**
```ts
export type PickedPhoto={clientEvidenceId:string;file:File;previewUrl:string;sha256:string;problem?:string};
const MAX=5*1024*1024;
async function sniff(file:File):Promise<string|null>{
 const b=new Uint8Array(await file.slice(0,12).arrayBuffer());
 if(b[0]===0xFF&&b[1]===0xD8&&b[2]===0xFF)return 'image/jpeg';
 if(b[0]===0x89&&b[1]===0x50&&b[2]===0x4E&&b[3]===0x47)return 'image/png';
 if(String.fromCharCode(...b.slice(0,4))==='RIFF'&&String.fromCharCode(...b.slice(8,12))==='WEBP')return 'image/webp';
 return null;
}
async function sha256(file:File){const d=await crypto.subtle.digest('SHA-256',await file.arrayBuffer());return [...new Uint8Array(d)].map(x=>x.toString(16).padStart(2,'0')).join('')}
export async function pickPhotos(files:FileList|File[],existing:number,max:number):Promise<PickedPhoto[]>{
 const out:PickedPhoto[]=[];
 for(const file of Array.from(files)){
  const p:PickedPhoto={clientEvidenceId:crypto.randomUUID(),file,previewUrl:URL.createObjectURL(file),sha256:''};
  if(existing+out.filter(x=>!x.problem).length>=max)p.problem=`Máximo ${max} fotos para este Hito`;
  else if(file.size>MAX)p.problem='Supera 5 MB';
  else if(!(await sniff(file)))p.problem='Formato no permitido (use JPG, PNG o WebP)';
  else p.sha256=await sha256(file);
  out.push(p);
 }
 return out;
}
export function buildEvidenceForm(meta:{uploadBatchId:string;eventId:string;assignmentId:string;targetId:string},photos:PickedPhoto[],gps?:{latitude:number;longitude:number;accuracyM:number}):FormData{
 const now=new Date().toISOString();
 const items=photos.map(p=>({clientEvidenceId:p.clientEvidenceId,capturedAt:now,latitude:gps?.latitude??null,longitude:gps?.longitude??null,accuracyM:gps?.accuracyM??null,source:'GALLERY',sha256:p.sha256}));
 const fd=new FormData();
 fd.append('metadata',JSON.stringify({...meta,targetType:'PATROL_CHECKPOINT',items})); // texto, nunca Blob
 photos.forEach(p=>fd.append('files',p.file,`${p.clientEvidenceId}.${p.file.type==='image/png'?'png':p.file.type==='image/webp'?'webp':'jpg'}`));
 return fd;
}
```
Nota: en el simulador web las fotos se eligen de archivo → `source:'GALLERY'` (la alerta `GALLERY` aparecerá; es esperado).

- [ ] **Step 2: `AgentSimulator.tsx`**
```tsx
import {useEffect,useMemo,useState} from 'react';
import {Camera,CheckCircle2,Send,Trash2,Upload,AlertTriangle,Info} from 'lucide-react';
import {api,ApiError,getUser} from '../api';
import {buildEvidenceForm,pickPhotos,type PickedPhoto} from '../lib/evidenceUpload';

type Cp={checkpointId:string;code:string;name:string;description:string;requiresEvidence:boolean;evidenceMinCount:number;evidenceMaxCount:number;hasStandardImage:boolean;standardImageNotes:string;latitude:number|null;longitude:number|null};
type Patrol={protocolCode:string;patrolId:string;code:string;name:string;checkpoints:Cp[]};
type Result={clientEvidenceId:string;evidenceId?:string;status:string;reason?:string|null;flags:string[]};
const REASONS:Record<string,string>={FILE_TOO_LARGE:'Supera 5 MB',UNSUPPORTED_FORMAT:'Formato no permitido',CHECKSUM_MISMATCH:'El archivo se dañó en el envío',TOO_MANY_PHOTOS:'Se superó el máximo de fotos'};
const FLAGS:Record<string,string>={GALLERY:'De galería',OUT_OF_RANGE:'Fuera del radio GPS',SUSPECTED_REUSE:'Posible foto repetida'};
const err=(e:unknown)=>e instanceof ApiError?(e.body||e.message):String(e);

export default function AgentSimulator(){
 const [employeeId,setEmployeeId]=useState('');const [assignments,setAssignments]=useState<any[]>([]);const [assignmentId,setAssignmentId]=useState('');
 const [patrols,setPatrols]=useState<Patrol[]>([]);const [patrolId,setPatrolId]=useState('');const [checkpointId,setCheckpointId]=useState('');
 const [guideUrl,setGuideUrl]=useState('');const [photos,setPhotos]=useState<PickedPhoto[]>([]);const [results,setResults]=useState<Result[]>([]);
 const [progress,setProgress]=useState<number|null>(null);const [error,setError]=useState('');const [done,setDone]=useState('');
 const [eventId,setEventId]=useState(crypto.randomUUID());const [runId]=useState(crypto.randomUUID());
 const patrol=patrols.find(p=>p.patrolId===patrolId);const cp=patrol?.checkpoints.find(c=>c.checkpointId===checkpointId);
 const stored=results.filter(r=>r.status==='STORED'||r.status==='ALREADY_STORED');
 const valid=photos.filter(p=>!p.problem);

 useEffect(()=>{if(getUser()!=='agente'){setError('Seleccione el usuario UAT "Agente" en el encabezado.');return}
  api.operatorRuntime().then(r=>{setEmployeeId(r.employeeId);setAssignments(r.assignments??[]);setAssignmentId(r.assignments?.[0]?.assignmentId??'')}).catch(e=>setError(err(e)))},[]);
 useEffect(()=>{if(!assignmentId)return;api.operatorRuntime(assignmentId).then(r=>{setPatrols(r.patrols??[]);setPatrolId(r.patrols?.[0]?.patrolId??'');setCheckpointId(r.patrols?.[0]?.checkpoints?.[0]?.checkpointId??'')}).catch(e=>setError(err(e)))},[assignmentId]);
 useEffect(()=>{setPhotos([]);setResults([]);setDone('');setEventId(crypto.randomUUID());if(guideUrl)URL.revokeObjectURL(guideUrl);setGuideUrl('');
  if(cp?.hasStandardImage)api.operatorCheckpointImage(cp.checkpointId,assignmentId).then(b=>setGuideUrl(URL.createObjectURL(b))).catch(()=>{})},[checkpointId]);
 useEffect(()=>()=>photos.forEach(p=>URL.revokeObjectURL(p.previewUrl)),[]);

 const gps=useMemo(()=>cp?.latitude!=null&&cp?.longitude!=null?{latitude:cp.latitude,longitude:cp.longitude,accuracyM:8}:undefined,[cp]);
 const add=async(files:FileList|null)=>{if(!files||!cp)return;setPhotos([...photos,...await pickPhotos(files,valid.length+stored.length,cp.evidenceMaxCount)])};
 const remove=(id:string)=>{const p=photos.find(x=>x.clientEvidenceId===id);if(p)URL.revokeObjectURL(p.previewUrl);setPhotos(photos.filter(x=>x.clientEvidenceId!==id))};
 const upload=async()=>{if(!cp||!valid.length)return;setError('');setProgress(0);
  try{const batch=crypto.randomUUID();const r=await api.uploadEvidences(buildEvidenceForm({uploadBatchId:batch,eventId,assignmentId,targetId:cp.checkpointId},valid,gps),{onProgress:setProgress,idempotencyKey:batch});
   setResults([...results.filter(x=>!r.results.some((y:Result)=>y.clientEvidenceId===x.clientEvidenceId)),...r.results]);
   setPhotos(photos.filter(p=>!r.results.some((y:Result)=>y.clientEvidenceId===p.clientEvidenceId&&y.status!=='REJECTED')));
  }catch(e){setError(err(e))}finally{setProgress(null)}};
 const confirm=async()=>{if(!cp||!patrol)return;setError('');
  try{const r=await api.submitExecution({batchId:crypto.randomUUID(),correlationId:crypto.randomUUID(),employeeId,instanceCountryId:'11111111-1111-1111-1111-111111111111',deviceId:'simulador-web',capturedAt:new Date().toISOString(),
    events:[{type:'PATROL_CHECKPOINT_COMPLETED',eventId,assignmentId,patrolRunId:runId,patrolId:patrol.patrolId,checkpointId:cp.checkpointId,executedAt:new Date().toISOString(),...(gps??{}),evidenceIds:stored.map(s=>s.evidenceId)}]});
   setDone(`Hito ${cp.code} registrado con ${r.results[0].evidenceCount} foto(s). Estado: recibido.`)}catch(e){setError(err(e))}};
 const canConfirm=!!cp&&stored.length>=(cp.requiresEvidence?cp.evidenceMinCount:0)&&stored.length<=cp.evidenceMaxCount&&!done;

 return <div className="agent-sim">
  <div className="agent-sim-head"><h2>Simulador de Agente <span>UAT</span></h2><p>Reproduce lo que hará SGI: Operador: elegir un Hito, adjuntar varias fotos y enviarlas por FormData.</p></div>
  {error&&<div className="ser-error"><AlertTriangle size={16}/><span>{error}</span><button onClick={()=>setError('')}>Cerrar</button></div>}
  {done&&<div className="posts-notice"><CheckCircle2 size={15}/><span>{done}</span></div>}
  <section className="agent-sim-card"><h3>1 · Tarea</h3>
   <label>Asignación<select value={assignmentId} onChange={e=>setAssignmentId(e.target.value)}>{assignments.map(a=><option key={a.assignmentId} value={a.assignmentId}>{a.postName} · {new Date(a.startsAt).toLocaleString('es-EC')}</option>)}</select></label>
   <label>Patrulla<select value={patrolId} onChange={e=>{setPatrolId(e.target.value);setCheckpointId(patrols.find(p=>p.patrolId===e.target.value)?.checkpoints[0]?.checkpointId??'')}}>{patrols.map(p=><option key={p.patrolId} value={p.patrolId}>{p.protocolCode} · {p.code} {p.name}</option>)}</select></label>
   <label>Hito<select value={checkpointId} onChange={e=>setCheckpointId(e.target.value)}>{patrol?.checkpoints.map(c=><option key={c.checkpointId} value={c.checkpointId}>{c.code} · {c.name}</option>)}</select></label>
   {!patrols.length&&assignmentId&&<p className="agent-sim-empty"><Info size={14}/>No hay patrullas activas para este puesto. Publique un protocolo de Patrullas.</p>}
  </section>
  {cp&&<section className="agent-sim-card"><h3>2 · Foto guía</h3>{guideUrl?<img className="agent-sim-guide" src={guideUrl} alt="Foto estándar"/>:<p className="agent-sim-empty">Este Hito no tiene foto estándar.</p>}{cp.standardImageNotes&&<p>{cp.standardImageNotes}</p>}</section>}
  {cp&&<section className="agent-sim-card"><h3>3 · Fotos del agente <small>({stored.length+valid.length} de {cp.evidenceMinCount}–{cp.evidenceMaxCount})</small></h3>
   <label className="agent-sim-pick"><Camera size={20}/>Tomar o elegir fotos<input type="file" accept="image/jpeg,image/png,image/webp" capture="environment" multiple hidden onChange={e=>{void add(e.target.files);e.currentTarget.value=''}}/></label>
   <div className="agent-sim-grid">
    {results.filter(r=>r.status!=='REJECTED').map(r=><article key={r.clientEvidenceId} className="ok"><CheckCircle2 size={22}/><b>{r.status==='STORED'?'Guardada':'Ya estaba'}</b>{r.flags.map(f=><em key={f}>{FLAGS[f]??f}</em>)}</article>)}
    {photos.map(p=><article key={p.clientEvidenceId} className={p.problem?'bad':''}><img src={p.previewUrl} alt=""/>{p.problem&&<em>{p.problem}</em>}{results.find(r=>r.clientEvidenceId===p.clientEvidenceId&&r.status==='REJECTED')&&<em>{REASONS[results.find(r=>r.clientEvidenceId===p.clientEvidenceId)!.reason??'']}</em>}<button onClick={()=>remove(p.clientEvidenceId)} aria-label="Quitar"><Trash2 size={14}/></button></article>)}
   </div>
   {progress!==null&&<div className="agent-sim-progress"><i style={{width:`${progress}%`}}/><span>{progress}%</span></div>}
   <div className="agent-sim-actions"><button onClick={()=>void upload()} disabled={!valid.length||progress!==null}><Upload size={15}/>Subir {valid.length} foto(s)</button>
    <button className="primary" onClick={()=>void confirm()} disabled={!canConfirm}><Send size={15}/>Confirmar Hito</button></div>
  </section>}
 </div>;
}
```

- [ ] **Step 3: Navegación y usuario**

- `Header.tsx:3`: `const users:UatUser[]=['presidente','dnacional','dzonal','jregional','coord','asistente','supervisor','agente'];`
- `Sidebar.tsx`: en el grupo `Operaciones`, después de `Consignas`, agregar `{name:'Simulador Agente (UAT)',icon:Smartphone},` e importar `Smartphone` de `lucide-react`.
- `App.tsx`: `import AgentSimulator from './pages/AgentSimulator';` y en la cadena de ternarios agregar antes de `Placeholder`: `: active === 'Simulador Agente (UAT)' ? <AgentSimulator />`.

- [ ] **Step 4: Estilos (`styles.css`, al final)**
```css
.agent-sim{display:grid;gap:16px;max-width:980px}
.agent-sim-head h2{margin:0;display:flex;gap:8px;align-items:center}.agent-sim-head h2 span{font-size:11px;background:#fef3c7;color:#92400e;padding:2px 8px;border-radius:99px}
.agent-sim-card{background:#fff;border:1px solid #e5e7eb;border-radius:14px;padding:16px;display:grid;gap:10px}
.agent-sim-card h3{margin:0;font-size:15px}.agent-sim-card label{display:grid;gap:4px;font-size:12px;font-weight:600}
.agent-sim-guide{max-width:320px;border-radius:10px;border:1px solid #e5e7eb}
.agent-sim-pick{display:flex!important;gap:8px;align-items:center;justify-content:center;border:2px dashed #93c5fd;border-radius:12px;padding:18px;cursor:pointer;color:#1d4ed8}
.agent-sim-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(140px,1fr));gap:10px}
.agent-sim-grid article{position:relative;border:1px solid #e5e7eb;border-radius:10px;overflow:hidden;min-height:110px;display:grid;place-items:center;gap:4px;padding:6px}
.agent-sim-grid article img{width:100%;height:110px;object-fit:cover}
.agent-sim-grid article.ok{background:#f0fdf4;color:#15803d}.agent-sim-grid article.bad{outline:2px solid #fca5a5}
.agent-sim-grid article em{font-size:11px;font-style:normal;background:#fff7ed;color:#9a3412;padding:1px 6px;border-radius:99px}
.agent-sim-grid article button{position:absolute;top:6px;right:6px;background:#fff;border-radius:8px}
.agent-sim-progress{position:relative;height:10px;background:#e5e7eb;border-radius:99px;overflow:hidden}.agent-sim-progress i{position:absolute;inset:0 auto 0 0;background:#2563eb}.agent-sim-progress span{position:absolute;right:6px;top:-18px;font-size:11px}
.agent-sim-actions{display:flex;gap:10px;justify-content:flex-end}
.agent-sim-empty{display:flex;gap:6px;align-items:center;color:#64748b;font-size:13px}
@media (max-width:640px){.agent-sim-actions{flex-direction:column}}
```

- [ ] **Step 5: Compilar** → `cd frontend && npm run build` sin errores.

- [ ] **Step 6: Commit**
```bash
git add frontend/src
git commit -m "feat(front): simulador de agente UAT con carga de varias fotos por FormData"
```

---

### Task 11: Prueba de punta a punta, contrato y documentación

**Files:**
- Create: `C:/Proyectos/sgi_comando/evidencias_playwright/fase1_simulador_agente.mjs` (fuera del repo, como el script anterior)
- Modify: `docs/API_CONTRACTS.md`, `docs/DECISIONS.md`, `backend/src/main/resources/interconnections/sgi-comando-catalog.json` (entrada `SGI_OPR_SGI_COM_0002`)

- [ ] **Step 1: Preparar entorno**
```bash
docker compose up -d --build
docker compose exec -T postgres psql -U sgi -d sgi_comando -f - < database/uat-fixtures/DME_03_operador_agente_patrullas_UAT.sql
```
Con el usuario Coordinador en la app: Patrullas → PRO-PAT-0004 → H01 → mín 2 / máx 3 → Guardar Hito → **Publicar versión** (queda ACTIVO en GGTT01).

- [ ] **Step 2: Script E2E**

Copiar `subir_fotos_estandar.mjs` como `fase1_simulador_agente.mjs` y reemplazar los flujos por uno solo `simulador`:
1. `page.goto(APP)`; en `localStorage` fijar `sgi-uat-user=agente` y recargar.
2. Menú → **Simulador Agente (UAT)**; esperar el `select` de Asignación.
3. Elegir la patrulla de **PRO-PAT-0004** y el Hito **H01**; verificar que la foto guía se ve (`.agent-sim-guide`).
4. `setInputFiles` en `.agent-sim-pick input` con **3 fotos**: `1_patrullas_hito_porton_con_candado.jpg`, `alternativas/patrullas_porton_candado_wikimedia.jpg` y `alternativas/patrullas_extintor_gabinete_wikimedia.jpg`; verificar 3 vistas previas sin avisos. Luego agregar una 4.ª (`2_consignas_evidencia_cortina_cerrada.jpg`) y verificar que queda marcada “Máximo 3 fotos para este Hito” y se quita con el botón de papelera.
5. **Subir 3 foto(s)** → esperar 3 tarjetas `.agent-sim-grid article.ok` con “Guardada”.
6. **Confirmar Hito** → esperar el aviso “Hito H01 registrado con 3 foto(s)”.
7. Grabar video, capturas y `trace.zip` en `C:/Proyectos/sgi_comando/evidencias_playwright/fase1/`.
8. Verificar en base y MinIO:
```bash
docker compose exec -T postgres psql -U sgi -d sgi_comando -c "select t.id, t.status, count(e.*) fotos from task_execution t join task_execution_evidence e on e.task_execution_id=t.id group by 1,2 order by max(t.received_at) desc limit 1"
docker compose exec -T minio sh -c 'ls -R /data/sgi-evidence | head'
```
Expected: 1 ejecución `RECEIVED` con 3 fotos; objetos en `sgi-evidence/evidence/2026/09/<eventId>/`.

- [ ] **Step 3: Documentación del contrato**

`docs/API_CONTRACTS.md` — nueva sección “SGI_OPR → SGI_COM · Evidencias multipart (v1)” con: endpoint, campos `metadata`/`files`, JSON de metadatos, respuesta por ítem, códigos 200/400/409/503, límites (5 MB, 5 archivos, 30 MB por petición), idempotencia por `clientEvidenceId`, y el lote `PATROL_CHECKPOINT_COMPLETED` (copiar los JSON de Task 7 y Task 8).

`docs/DECISIONS.md` — agregar al final:
```markdown
## Evidencias del agente — Fase 1 (2026-09-29)
- **SGI-EVI-DEC-001:** Las fotos del agente se envían por `multipart/form-data`: campo `metadata` (JSON como texto) y 1..5 partes `files`, cada una nombrada `<clientEvidenceId>.<ext>`.
- **SGI-EVI-DEC-002:** Un Hito admite entre `evidence_min_count` y `evidence_max_count` fotos (1..5, por defecto 1..5).
- **SGI-EVI-DEC-003:** Fotos estándar y del agente se almacenan en MinIO; Postgres guarda solo la referencia (clave, sha256, tamaño). El bytea anterior se migra en segundo plano y se elimina en una versión posterior.
- **SGI-EVI-DEC-004:** Carga en dos pasos (evidencias → ejecución), idempotente por `clientEvidenceId` y por `eventId`.
```

`sgi-comando-catalog.json` — en `SGI_OPR_SGI_COM_0002_v001`, agregar a `interfaces`:
```json
{
  "interfaceId": "SGI_OPR_SGI_COM_0002_IF02",
  "method": "POST",
  "path": "/api/v1/operator/evidences",
  "purpose": "Multipart upload of 1..5 task photos with JSON metadata",
  "contentType": "multipart/form-data",
  "request": { "fields": ["metadata", "files"] },
  "response": { "fields": ["results"] }
}
```

- [ ] **Step 4: Suite completa y commit**
```bash
powershell -File scripts/backend-test.ps1
cd frontend && npm run build
git add docs backend/src/main/resources/interconnections/sgi-comando-catalog.json
git commit -m "docs: contrato de evidencias multipart y decisiones de la fase 1"
```

---

## Self-review

- **Cobertura:** MinIO (T1) · fotos estándar a MinIO + respaldo + migrador + FormData en los 3 módulos (T2) · mín/máx por Hito (T3, T9) · `evidence_object`/`task_execution`/`patrol_execution` (T4) · runtime con patrullas y foto guía (T5, T7) · `POST /evidences` multipart con varias fotos (T6, T7) · `PATROL_CHECKPOINT_COMPLETED` (T8) · simulador (T10) · E2E y contrato (T11). Consignas/Bitácora para el agente, VISINT e Impulsos: fuera de la fase 1.
- **Review Focus:** 1→T7 `storesThreePhotosAndIsIdempotent`; 2→T7 `acceptsMetadataLargerThanTwoKilobytes`; 3→T7 `rejectsDisguisedFileAndFlagsOutOfRange`; 4→T8 `refusesFewerThanMinimum`/`refusesPhotosFromAnotherEvent`; 5→T2 `legacyByteaStillReadableAndMigrates`.
- **Consistencia de nombres:** `OperatorContext.Assignment` (T5) usado en T7/T8; `OperatorPatrols.requireCheckpoint`/`Applicable` (T5) usados en T7/T8; `StandardImageStore.has/read/save` (T2) usados en T5/T7; `EvidenceUploadContract.parse/matchFiles` (T6) en T7.
