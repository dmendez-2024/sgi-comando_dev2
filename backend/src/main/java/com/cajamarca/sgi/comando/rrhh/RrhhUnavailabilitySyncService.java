package com.cajamarca.sgi.comando.rrhh;

import com.cajamarca.sgi.comando.assignments.EmployeeOperationalSnapshot;
import com.cajamarca.sgi.comando.assignments.EmployeeUnavailabilitySnapshot;
import com.cajamarca.sgi.comando.common.TenantContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class RrhhUnavailabilitySyncService {
    private static final Set<String> TYPES = Set.of("VACATION", "MEDICAL_LEAVE", "PERMISSION");
    private static final Set<String> SOURCE_STATUSES = Set.of("ACTIVE", "INACTIVE");

    @Inject TenantContext tenant;

    public record SyncResult(String sourceRef, String processingStatus) {}

    @Transactional
    public SyncResult synchronize(
        RrhhUnavailabilityEventResource.UnavailabilityEventRequest request,
        String idempotencyKey
    ) {
        validateContract(request);
        String key = trim(idempotencyKey);
        if (key.isEmpty() || key.length() > 160) {
            throw new BadRequestException("Idempotency-Key es obligatorio y debe tener máximo 160 caracteres.");
        }

        UUID tenantId = tenant.instanceCountryId();
        String hash = contentHash(request);
        RrhhUnavailabilityEventReceipt prior = RrhhUnavailabilityEventReceipt.find(
            "instanceCountryId=?1 and idempotencyKey=?2", tenantId, key
        ).firstResult();
        if (prior != null) {
            if (!prior.contentHash.equals(hash)) {
                throw new WebApplicationException(
                    "Idempotency-Key ya fue recibido con contenido distinto.",
                    Response.Status.CONFLICT
                );
            }
            return new SyncResult(sourceRef(request.sourceRecordId()), "DUPLICATE_IGNORED");
        }

        EmployeeOperationalSnapshot employee = EmployeeOperationalSnapshot.find(
            "instanceCountryId=?1 and personaId=?2", tenantId, request.personaId()
        ).firstResult();
        if (employee == null) {
            throw new BadRequestException("personaId no identifica un empleado de la instancia vigente en SGI:Comando.");
        }

        String reference = sourceRef(request.sourceRecordId());
        EmployeeUnavailabilitySnapshot row = EmployeeUnavailabilitySnapshot.find(
            "instanceCountryId=?1 and sourceRef=?2", tenantId, reference
        ).firstResult();
        if (row != null && !row.employeeId.equals(employee.employeeId)) {
            throw new WebApplicationException(
                "sourceRecordId ya está vinculado a otro empleado.",
                Response.Status.CONFLICT
            );
        }
        if (row != null && row.updatedFromSourceAt != null
            && request.updatedFromSourceAt().isBefore(row.updatedFromSourceAt)) {
            recordReceipt(tenantId, key, hash, request.updatedFromSourceAt(), "STALE_IGNORED");
            return new SyncResult(reference, "STALE_IGNORED");
        }

        if (row == null) {
            row = new EmployeeUnavailabilitySnapshot();
            row.instanceCountryId = tenantId;
            row.employeeId = employee.employeeId;
            row.sourceRef = reference;
        }
        row.type = upper(request.type());
        row.startsAt = request.startsAt();
        row.endsAt = request.endsAt();
        row.sourceStatus = upper(request.sourceStatus());
        row.sourceReasonId = request.sourceReasonId();
        row.sourceReasonLabel = request.sourceReasonLabel().trim();
        row.sourceState = upper(request.sourceState());
        row.updatedFromSourceAt = request.updatedFromSourceAt();
        if (!row.isPersistent()) row.persist();

        recordReceipt(tenantId, key, hash, request.updatedFromSourceAt(), "APPLIED");
        return new SyncResult(reference, "APPLIED");
    }

    static void validateContract(RrhhUnavailabilityEventResource.UnavailabilityEventRequest request) {
        if (request == null) throw new BadRequestException("El cuerpo del evento es obligatorio.");
        if (request.sourceRecordId() == null || request.sourceRecordId() <= 0) {
            throw new BadRequestException("sourceRecordId es obligatorio y debe ser mayor que cero.");
        }
        if (request.personaId() == null || request.personaId() <= 0) {
            throw new BadRequestException("personaId es obligatorio y debe ser mayor que cero.");
        }
        String type = upper(request.type());
        if (!TYPES.contains(type)) {
            throw new BadRequestException("type debe ser VACATION, MEDICAL_LEAVE o PERMISSION.");
        }
        if (request.startsAt() == null || request.endsAt() == null
            || !request.endsAt().isAfter(request.startsAt())) {
            throw new BadRequestException("startsAt y endsAt son obligatorios; endsAt debe ser posterior a startsAt.");
        }
        if (!SOURCE_STATUSES.contains(upper(request.sourceStatus()))) {
            throw new BadRequestException("sourceStatus debe ser ACTIVE o INACTIVE.");
        }
        if (request.sourceReasonId() == null || request.sourceReasonId() <= 0) {
            throw new BadRequestException("sourceReasonId es obligatorio y debe ser mayor que cero.");
        }
        if (blank(request.sourceReasonLabel()) || request.sourceReasonLabel().trim().length() > 512) {
            throw new BadRequestException("sourceReasonLabel es obligatorio y admite máximo 512 caracteres.");
        }
        if (blank(request.sourceState()) || request.sourceState().trim().length() > 32) {
            throw new BadRequestException("sourceState es obligatorio y admite máximo 32 caracteres.");
        }
        if (request.updatedFromSourceAt() == null) {
            throw new BadRequestException("updatedFromSourceAt es obligatorio.");
        }
    }

    static String sourceRef(Long sourceRecordId) {
        return "SIC_DHO:PERMISO:" + sourceRecordId;
    }

    static String contentHash(RrhhUnavailabilityEventResource.UnavailabilityEventRequest request) {
        String canonical = String.join("\u001f",
            Objects.toString(request.sourceRecordId(), ""),
            Objects.toString(request.personaId(), ""),
            upper(request.type()),
            Objects.toString(request.startsAt(), ""),
            Objects.toString(request.endsAt(), ""),
            upper(request.sourceStatus()),
            Objects.toString(request.sourceReasonId(), ""),
            trim(request.sourceReasonLabel()),
            upper(request.sourceState()),
            Objects.toString(request.updatedFromSourceAt(), "")
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new InternalServerErrorException("SHA-256 no está disponible.", e);
        }
    }

    private void recordReceipt(
        UUID tenantId,
        String idempotencyKey,
        String contentHash,
        Instant sourceTimestamp,
        String status
    ) {
        RrhhUnavailabilityEventReceipt receipt = new RrhhUnavailabilityEventReceipt();
        receipt.instanceCountryId = tenantId;
        receipt.idempotencyKey = idempotencyKey;
        receipt.contentHash = contentHash;
        receipt.sourceUpdatedAt = sourceTimestamp;
        receipt.processingStatus = status;
        receipt.processedAt = Instant.now();
        receipt.persist();
    }

    private static String upper(String value) { return trim(value).toUpperCase(Locale.ROOT); }
    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static String trim(String value) { return value == null ? "" : value.trim(); }
}
