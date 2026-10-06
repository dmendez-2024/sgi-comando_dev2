package com.cajamarca.sgi.comando.siccom;

import com.cajamarca.sgi.comando.assignments.PostPlanningCycleSnapshot;
import com.cajamarca.sgi.comando.assignments.PostShiftTemplate;
import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.operations.ClientEntity;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.operations.PostEntity;
import com.cajamarca.sgi.comando.operations.ServiceEntity;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class SicComCommercialCatalogService {
  static final String SIC_COM = "SIC_COM";
  static final String ACTIVE = "ACTIVE";
  static final String INACTIVE = "INACTIVE";
  static final String CREATED = "COMMERCIAL_CATALOG_CREATED";
  static final String UPDATED = "COMMERCIAL_CATALOG_UPDATED";
  static final String INACTIVATED = "COMMERCIAL_CATALOG_INACTIVATED";
  static final int MAX_CYCLE_LENGTH_DAYS = 366;

  @Inject TenantContext tenant;
  @Inject ObjectMapper objectMapper;
  @Inject EntityManager entityManager;

  public record CatalogEventRequest(
      String eventId,
      String eventType,
      UUID instanceCountryId,
      String commercialVersion,
      Instant occurredAt,
      ClientInput client,
      ServiceInput service,
      List<PointInput> points,
      List<ServiceInput> services) {}

  public record ClientInput(String code, String name, String commercialStatus) {}
  /**
   * {@code points} is used by the batch contract ({@code services[]}).  The
   * top-level points field remains available for the original one-service
   * contract.
   */
  public record ServiceInput(String code, String name, String clientCode, String commercialStatus, List<PointInput> points) {}
  public record PointInput(String code, String name, String province, String city, String status, List<PostInput> posts) {}
  public record PostInput(
      String code,
      @JsonProperty("code2") @JsonAlias("Code2") String code2,
      String name,
      String format,
      BigDecimal fhe,
      String tier,
      String commercialStatus,
      RotationInput rotation,
      List<ShiftInput> shifts) {}
  public record RotationInput(String code, Integer cycleLengthDays) {}
  public record ShiftInput(String code, String name, LocalTime startTime, LocalTime endTime, Integer dayMask, Boolean active) {}

  public record AppliedEntity(
      String sourceCode,
      @JsonInclude(JsonInclude.Include.NON_NULL) String sourceCode2,
      UUID sgiId,
      String action) {}
  public record AppliedPoint(String sourceCode, UUID sgiId, String action, List<AppliedEntity> posts) {}
  public record AppliedService(AppliedEntity service, List<AppliedPoint> points) {}
  public record CatalogResponse(
      String status,
      String eventId,
      String commercialVersion,
      AppliedEntity client,
      @JsonInclude(JsonInclude.Include.NON_NULL)
      AppliedEntity service,
      @JsonInclude(JsonInclude.Include.NON_EMPTY)
      List<AppliedPoint> points,
      String correlationId,
      List<String> warnings,
      List<AppliedService> services) {}

  public static final class CatalogException extends RuntimeException {
    private final int status;
    CatalogException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
  }

  @Transactional
  public CatalogResponse apply(CatalogEventRequest request, String correlationId) {
    validate(request);
    UUID instanceCountryId = tenant.instanceCountryId();
    if (!instanceCountryId.equals(request.instanceCountryId())) {
      throw validation("instanceCountryId no corresponde a la instancia configurada.");
    }

    String contentHash = contentHash(request);
    SicComCommercialEventReceipt receipt = SicComCommercialEventReceipt.find(
        "instanceCountryId=?1 and eventId=?2", instanceCountryId, request.eventId()).firstResult();
    if (receipt != null) {
      if (!receipt.contentHash.equals(contentHash)) {
        throw conflict("eventId ya fue recibido con contenido distinto.");
      }
      return readRecordedResponse(receipt);
    }

    Upsert<ClientEntity> client = upsertClient(instanceCountryId, request.client(), request.commercialVersion());
    List<AppliedService> appliedServices = new ArrayList<>();
    for (ServicePayload payload : servicePayloads(request)) {
      Upsert<ServiceEntity> service = upsertService(instanceCountryId, payload.service(), client.value, request.commercialVersion());
      List<AppliedPoint> appliedPoints = new ArrayList<>();
      for (PointInput pointInput : payload.points()) {
        Upsert<PointEntity> point = upsertPoint(instanceCountryId, pointInput, service.value, client.value.name);
        List<AppliedEntity> appliedPosts = new ArrayList<>();
        for (PostInput postInput : safe(pointInput.posts())) {
          Upsert<PostEntity> post = upsertPost(instanceCountryId, postInput, pointInput, request.client(), point.value);
          applyPlanningSnapshot(instanceCountryId, post.value, postInput, request.commercialVersion());
          appliedPosts.add(applied(post.value.code, postInput.code2(), post.value.id, post.action));
        }
        appliedPoints.add(new AppliedPoint(pointInput.code(), point.value.id, point.action, appliedPosts));
      }
      appliedServices.add(new AppliedService(applied(service.value.code, service.value.id, service.action), appliedPoints));
    }

    boolean legacyRequest = request.service() != null;
    AppliedService legacyService = legacyRequest ? appliedServices.getFirst() : null;
    CatalogResponse response = new CatalogResponse(
        "APPLIED", request.eventId(), request.commercialVersion(),
        applied(request.client().code(), client.value.id, client.action),
        legacyService == null ? null : legacyService.service(),
        legacyService == null ? List.of() : legacyService.points(),
        correlationId, List.of(), appliedServices);
    recordReceipt(instanceCountryId, request, contentHash, response);
    return response;
  }

  private Upsert<ClientEntity> upsertClient(UUID instanceCountryId, ClientInput input, String version) {
    ClientEntity client = ClientEntity.find("instanceCountryId=?1 and code=?2", instanceCountryId, input.code()).firstResult();
    if (client == null) {
      List<ClientEntity> legacy = ClientEntity.list(
          "instanceCountryId=?1 and sourceSystem='LEGACY' and lower(name)=lower(?2)", instanceCountryId, input.name());
      if (legacy.size() == 1) client = legacy.getFirst();
    }
    boolean created = client == null;
    if (created) {
      client = new ClientEntity();
      client.instanceCountryId = instanceCountryId;
    }
    client.code = input.code();
    client.name = input.name();
    client.commercialStatus = commercialStatus(input.commercialStatus());
    client.sourceSystem = SIC_COM;
    client.sourceVersion = version;
    if (created) client.persist();
    return new Upsert<>(client, created ? "CREATED" : "UPDATED");
  }

  private Upsert<ServiceEntity> upsertService(UUID instanceCountryId, ServiceInput input, ClientEntity client, String version) {
    ServiceEntity service = ServiceEntity.find("instanceCountryId=?1 and code=?2", instanceCountryId, input.code()).firstResult();
    boolean created = service == null;
    if (created) {
      service = new ServiceEntity();
      service.instanceCountryId = instanceCountryId;
      service.configStatus = "TO_CONFIGURE";
    }
    service.code = input.code();
    service.name = input.name();
    service.clientId = client.id;
    service.clientName = client.name;
    service.commercialStatus = commercialStatus(input.commercialStatus());
    service.sourceSystem = SIC_COM;
    service.sourceVersion = version;
    if (created) service.persist();
    return new Upsert<>(service, created ? "CREATED" : "UPDATED");
  }

  private Upsert<PointEntity> upsertPoint(UUID instanceCountryId, PointInput input, ServiceEntity service, String clientName) {
    PointEntity point = PointEntity.find("instanceCountryId=?1 and code=?2", instanceCountryId, input.code()).firstResult();
    boolean created = point == null;
    if (!created && !point.serviceId.equals(service.id)) {
      throw conflict("El Punto " + input.code() + " pertenece a otro Servicio.");
    }
    if (created) {
      point = new PointEntity();
      point.instanceCountryId = instanceCountryId;
      point.serviceId = service.id;
      point.companyId = null;
      point.operationalAssignmentStatus = "PENDING";
      point.receivedFromSicComAt = Instant.now();
    }
    point.code = input.code();
    point.name = input.name();
    point.province = input.province();
    point.city = input.city();
    point.clientName = clientName;
    point.status = commercialStatus(input.status());
    if (created) point.persist();
    return new Upsert<>(point, created ? "CREATED" : "UPDATED");
  }

  private Upsert<PostEntity> upsertPost(
      UUID instanceCountryId, PostInput input, PointInput pointInput, ClientInput clientInput, PointEntity point) {
    String inboundCode = blankToNull(input.code());
    PostEntity post = inboundCode == null ? null
        : PostEntity.find("instanceCountryId=?1 and code=?2", instanceCountryId, inboundCode).firstResult();
    if (inboundCode != null && post == null) {
      throw validation("posts.code solo puede identificar un Puesto SGI existente; omítalo para crear uno nuevo.");
    }
    boolean created = post == null;
    if (!created && !post.pointId.equals(point.id)) {
      throw conflict("El Puesto " + input.code() + " pertenece a otro Punto.");
    }
    if (created) {
      post = new PostEntity();
      post.instanceCountryId = instanceCountryId;
      post.pointId = point.id;
      post.configStatus = "TO_CONFIGURE";
      if (blank(input.name()) || blank(input.format()) || input.fhe() == null || blank(input.tier())) {
        throw validation("Un Puesto nuevo requiere name, format, fhe y tier.");
      }
      try {
        post.code = nextGeneratedPostCode(instanceCountryId, pointInput, clientInput, input);
      } catch (IllegalArgumentException e) {
        throw validation(e.getMessage());
      }
    }
    if (input.code2() != null) post.code2 = blankToNull(input.code2());
    if (input.name() != null) post.name = input.name();
    if (input.format() != null) post.format = input.format();
    if (input.fhe() != null) post.fhe = input.fhe();
    if (input.tier() != null) post.tier = input.tier();
    post.commercialStatus = commercialStatus(input.commercialStatus());
    if (INACTIVE.equals(post.commercialStatus)) {
      PostShiftTemplate.update("active=false where instanceCountryId=?1 and postId=?2", instanceCountryId, post.id);
    }
    if (created) post.persist();
    return new Upsert<>(post, created ? "CREATED" : "UPDATED");
  }

  private String nextGeneratedPostCode(
      UUID instanceCountryId, PointInput point, ClientInput client, PostInput post) {
    String baseCode = PostCodeGenerator.baseCode(point.province(), point.city(), client.name(), post.name());
    String signature = PostCodeGenerator.sourceSignature(point.province(), point.city(), client.name(), post.name());

    // All allocators for a base share a transaction-scoped PostgreSQL advisory lock.
    // This prevents two simultaneous SIC:COM events from receiving the same suffix.
    entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtext(?1))")
        .setParameter(1, instanceCountryId + ":" + baseCode)
        .getSingleResult();

    PostCodeAllocation allocation = PostCodeAllocation.find(
        "instanceCountryId=?1 and baseCode=?2 and sourceSignature=?3", instanceCountryId, baseCode, signature).firstResult();
    if (allocation == null) {
      allocation = new PostCodeAllocation();
      allocation.instanceCountryId = instanceCountryId;
      allocation.baseCode = baseCode;
      allocation.sourceSignature = signature;
      allocation.discriminator = nextDiscriminator(instanceCountryId, baseCode);
      allocation.lastSequence = 0;
      allocation.persist();
    }

    int sequence = allocation.lastSequence;
    String code;
    do {
      sequence++;
      code = baseCode + allocation.discriminator + sequence;
      if (code.length() > 64) throw unprocessable("El código generado para el Puesto supera los 64 caracteres.");
    } while (PostEntity.count("instanceCountryId=?1 and code=?2", instanceCountryId, code) > 0);
    allocation.lastSequence = sequence;
    return code;
  }

  private String nextDiscriminator(UUID instanceCountryId, String baseCode) {
    Set<String> used = new HashSet<>(PostCodeAllocation.<PostCodeAllocation>list(
        "instanceCountryId=?1 and baseCode=?2", instanceCountryId, baseCode)
        .stream().map(allocation -> allocation.discriminator).toList());
    for (int index = 0; ; index++) {
      String candidate = PostCodeGenerator.discriminator(index);
      if (!used.contains(candidate)) return candidate;
    }
  }

  private void applyPlanningSnapshot(UUID instanceCountryId, PostEntity post, PostInput input, String version) {
    if (input.rotation() != null) {
      PostPlanningCycleSnapshot cycle = PostPlanningCycleSnapshot.find("instanceCountryId=?1 and postId=?2", instanceCountryId, post.id).firstResult();
      if (cycle == null) {
        cycle = new PostPlanningCycleSnapshot();
        cycle.instanceCountryId = instanceCountryId;
        cycle.postId = post.id;
      }
      cycle.rotationCode = required(input.rotation().code(), "rotation.code");
      cycle.cycleLengthDays = input.rotation().cycleLengthDays();
      if (cycle.cycleLengthDays == null || cycle.cycleLengthDays < 1 || cycle.cycleLengthDays > MAX_CYCLE_LENGTH_DAYS) {
        throw unprocessable("rotation.cycleLengthDays debe estar entre 1 y " + MAX_CYCLE_LENGTH_DAYS + ".");
      }
      cycle.sourceSystem = SIC_COM;
      cycle.sourceVersion = version;
      if (cycle.id == null) cycle.persist();
    }
    for (ShiftInput shiftInput : safe(input.shifts())) {
      PostShiftTemplate shift = PostShiftTemplate.find(
          "instanceCountryId=?1 and postId=?2 and shiftCode=?3", instanceCountryId, post.id, shiftInput.code()).firstResult();
      if (shift == null) {
        shift = new PostShiftTemplate();
        shift.instanceCountryId = instanceCountryId;
        shift.postId = post.id;
      }
      shift.shiftCode = required(shiftInput.code(), "shifts.code");
      shift.shiftName = required(shiftInput.name(), "shifts.name");
      shift.startTime = shiftInput.startTime();
      shift.endTime = shiftInput.endTime();
      if (shift.startTime == null || shift.endTime == null) throw validation("shifts.startTime y shifts.endTime son obligatorios.");
      shift.dayMask = shiftInput.dayMask();
      shift.active = shiftInput.active();
      shift.commercialVersion = version;
      if (shift.id == null) shift.persist();
    }
  }

  private void recordReceipt(UUID instanceCountryId, CatalogEventRequest request, String hash, CatalogResponse response) {
    SicComCommercialEventReceipt receipt = new SicComCommercialEventReceipt();
    receipt.instanceCountryId = instanceCountryId;
    receipt.eventId = request.eventId();
    receipt.contentHash = hash;
    receipt.commercialVersion = request.commercialVersion();
    receipt.processingStatus = "APPLIED";
    receipt.responseJson = write(response);
    receipt.occurredAt = request.occurredAt();
    receipt.processedAt = Instant.now();
    receipt.persist();
  }

  private CatalogResponse readRecordedResponse(SicComCommercialEventReceipt receipt) {
    try {
      return objectMapper.readValue(receipt.responseJson, CatalogResponse.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("No se pudo leer el recibo comercial persistido.", e);
    }
  }

  private void validate(CatalogEventRequest request) {
    if (request == null) throw validation("Body obligatorio.");
    required(request.eventId(), "eventId");
    required(request.commercialVersion(), "commercialVersion");
    if (request.instanceCountryId() == null || request.occurredAt() == null) throw validation("instanceCountryId y occurredAt son obligatorios.");
    if (!List.of(CREATED, UPDATED, INACTIVATED).contains(request.eventType())) throw validation("eventType no soportado.");
    if (request.client() == null) throw validation("client es obligatorio.");
    required(request.client().code(), "client.code"); required(request.client().name(), "client.name");
    boolean batchRequest = !safe(request.services()).isEmpty();
    if (batchRequest && (request.service() != null || !safe(request.points()).isEmpty())) {
      throw validation("Use service y points, o services, pero no ambos formatos en el mismo evento.");
    }
    if (!batchRequest && (request.service() == null || safe(request.points()).isEmpty())) {
      throw validation("client, service y points son obligatorios; alternativamente envíe services.");
    }

    boolean inactivation = INACTIVE.equals(commercialStatus(request.client().commercialStatus()));
    for (ServicePayload payload : servicePayloads(request)) {
      ServiceInput service = payload.service();
      if (service == null) throw validation("services no puede contener valores nulos.");
      required(service.code(), "services.code"); required(service.name(), "services.name");
      if (!request.client().code().equals(service.clientCode())) throw unprocessable("services.clientCode debe coincidir con client.code.");
      if (payload.points().isEmpty()) throw validation("services.points es obligatorio.");
      inactivation |= INACTIVE.equals(commercialStatus(service.commercialStatus()));
      for (PointInput point : payload.points()) {
        if (point == null) throw validation("services.points no puede contener valores nulos.");
        required(point.code(), "points.code"); required(point.name(), "points.name"); required(point.province(), "points.province"); required(point.city(), "points.city");
        if (safe(point.posts()).isEmpty()) throw validation("points.posts es obligatorio.");
        inactivation |= INACTIVE.equals(commercialStatus(point.status()));
        for (PostInput post : point.posts()) {
          if (post == null) throw validation("points.posts no puede contener valores nulos.");
          boolean postInactive = INACTIVE.equals(commercialStatus(post.commercialStatus()));
          inactivation |= postInactive;
          if (!postInactive || !INACTIVATED.equals(request.eventType())) {
            required(post.name(), "posts.name"); required(post.format(), "posts.format");
            if (post.fhe() == null || post.tier() == null) throw validation("posts.fhe y posts.tier son obligatorios.");
          }
          boolean hasPlanning = post.rotation() != null || !safe(post.shifts()).isEmpty();
          if (!INACTIVATED.equals(request.eventType()) || hasPlanning) validatePlanning(post);
        }
      }
    }
    if (INACTIVATED.equals(request.eventType()) && !inactivation) {
      throw unprocessable("Un evento de inactivacion debe marcar explicitamente al menos una entidad como INACTIVE.");
    }
  }

  private void validatePlanning(PostInput post) {
    if (post.rotation() == null) throw validation("posts.rotation es obligatorio.");
    required(post.rotation().code(), "posts.rotation.code");
    Integer cycleLengthDays = post.rotation().cycleLengthDays();
    if (cycleLengthDays == null || cycleLengthDays < 1 || cycleLengthDays > MAX_CYCLE_LENGTH_DAYS) {
      throw unprocessable("posts.rotation.cycleLengthDays debe estar entre 1 y " + MAX_CYCLE_LENGTH_DAYS + ".");
    }
    List<ShiftInput> shifts = safe(post.shifts());
    if (shifts.isEmpty()) throw validation("posts.shifts debe incluir al menos un turno.");
    for (ShiftInput shift : shifts) {
      required(shift.code(), "posts.shifts.code");
      required(shift.name(), "posts.shifts.name");
      if (shift.startTime() == null || shift.endTime() == null) {
        throw validation("posts.shifts.startTime y posts.shifts.endTime son obligatorios.");
      }
      if (shift.dayMask() == null) throw validation("posts.shifts.dayMask es obligatorio.");
      if (shift.active() == null) throw validation("posts.shifts.active es obligatorio.");
    }
  }

  private String commercialStatus(String value) {
    if (value == null || value.isBlank()) return ACTIVE;
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if (!ACTIVE.equals(normalized) && !INACTIVE.equals(normalized)) throw validation("commercialStatus/status solo admite ACTIVE o INACTIVE.");
    return normalized;
  }

  private String required(String value, String field) {
    if (value == null || value.isBlank()) throw validation(field + " es obligatorio.");
    return value.trim();
  }

  private String blankToNull(String value) { return blank(value) ? null : value.trim(); }
  private boolean blank(String value) { return value == null || value.isBlank(); }

  private <T> List<T> safe(List<T> values) { return values == null ? List.of() : values; }
  private List<ServicePayload> servicePayloads(CatalogEventRequest request) {
    if (!safe(request.services()).isEmpty()) {
      return request.services().stream()
          .map(service -> new ServicePayload(service, service == null ? List.of() : safe(service.points())))
          .toList();
    }
    return request.service() == null ? List.of() : List.of(new ServicePayload(request.service(), safe(request.points())));
  }
  private AppliedEntity applied(String code, UUID id, String action) {
    return applied(code, null, id, action);
  }

  private AppliedEntity applied(String code, String sourceCode2, UUID id, String action) {
    return new AppliedEntity(code, sourceCode2, id, action);
  }
  private CatalogException validation(String message) { return new CatalogException(400, message); }
  private CatalogException conflict(String message) { return new CatalogException(409, message); }
  private CatalogException unprocessable(String message) { return new CatalogException(422, message); }

  private String contentHash(CatalogEventRequest request) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(write(request).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 no disponible.", e);
    }
  }

  private String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("No se pudo serializar el evento comercial.", e);
    }
  }

  private record Upsert<T>(T value, String action) {}
  private record ServicePayload(ServiceInput service, List<PointInput> points) {}
}
