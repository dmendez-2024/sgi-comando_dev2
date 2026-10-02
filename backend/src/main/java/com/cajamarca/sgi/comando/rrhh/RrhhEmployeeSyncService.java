package com.cajamarca.sgi.comando.rrhh;

import com.cajamarca.sgi.comando.assignments.CompanyMembershipEntity;
import com.cajamarca.sgi.comando.assignments.EmployeeOperationalSnapshot;
import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.companies.Company;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.InternalServerErrorException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@ApplicationScoped
public class RrhhEmployeeSyncService {
    private static final List<String> LOCAL_AVATAR_KEYS = List.of(
        "/avatars/82000000-0000-0000-0000-000000000001.png",
        "/avatars/82000000-0000-0000-0000-000000000002.png",
        "/avatars/82000000-0000-0000-0000-000000000003.png",
        "/avatars/82000000-0000-0000-0000-000000000004.png",
        "/avatars/82000000-0000-0000-0000-000000000005.png",
        "/avatars/82000000-0000-0000-0000-000000000006.png",
        "/avatars/82000000-0000-0000-0000-000000000007.png",
        "/avatars/82000000-0000-0000-0000-000000000008.png",
        "/avatars/82000000-0000-0000-0000-000000000009.png",
        "/avatars/82000000-0000-0000-0000-000000000010.png",
        "/avatars/82000000-0000-0000-0000-000000000011.png",
        "/avatars/82000000-0000-0000-0000-000000000012.png"
    );

    @Inject TenantContext tenant;

    @ConfigProperty(name = "sgi.integrations.rrhh.use-canonical-employee-id", defaultValue = "false")
    boolean useCanonicalEmployeeId;

    /**
     * Apply one versioned SIC:RRHH event. Idempotency-Key is persisted so a retry
     * with the same payload is safe, while reuse of the key with different content
     * is rejected.
     */
    @Transactional
    public void synchronize(RrhhEmployeeEventResource.EmployeeEventRequest request, String idempotencyKey) {
        validate(request);
        String key = trim(idempotencyKey);
        if (key.isEmpty() || key.length() > 160) {
            throw new BadRequestException("Idempotency-Key es obligatorio y debe tener máximo 160 caracteres.");
        }

        UUID tenantId = tenant.instanceCountryId();
        String contentHash = contentHash(request);
        RrhhEmployeeEventReceipt prior = RrhhEmployeeEventReceipt.find(
            "instanceCountryId=?1 and idempotencyKey=?2", tenantId, key
        ).firstResult();
        if (prior != null) {
            if (!prior.contentHash.equals(contentHash)) {
                throw new WebApplicationException("Idempotency-Key ya fue recibido con contenido distinto.", Response.Status.CONFLICT);
            }
            return;
        }

        Long personaId = sourcePersonaId(request);
        UUID employeeId = resolveEmployeeId(tenantId, request);
        Instant sourceTimestamp = request.updatedFromSourceAt();
        String employmentStatus = trim(request.employmentStatus()).toUpperCase();

        EmployeeOperationalSnapshot employeeById = EmployeeOperationalSnapshot.find(
            "instanceCountryId=?1 and employeeId=?2", tenantId, employeeId
        ).firstResult();
        EmployeeOperationalSnapshot employeeByPersona = personaId == null ? null : EmployeeOperationalSnapshot.find(
            "instanceCountryId=?1 and personaId=?2", tenantId, personaId
        ).firstResult();

        if (employeeById != null && employeeByPersona != null
            && !employeeById.id.equals(employeeByPersona.id)) {
            throw new BadRequestException(
                "personaId y employeeId identifican registros distintos; se requiere una migración controlada."
            );
        }

        EmployeeOperationalSnapshot employee = employeeById != null ? employeeById : employeeByPersona;
        if (employee != null && !employee.employeeId.equals(employeeId)) {
            throw new BadRequestException(
                "personaId ya está vinculado a otro employeeId; se requiere una migración controlada antes de activar el identificador canónico."
            );
        }
        if (employee != null && personaId != null) {
            if (employee.personaId != null && !employee.personaId.equals(personaId)) {
                throw new BadRequestException("employeeId ya está vinculado a otro personaId.");
            }
            employee.personaId = personaId;
        }

        if (employee == null && "INACTIVE".equals(employmentStatus)) {
            throw new BadRequestException(
                "No se puede inactivar al colaborador porque no existe en SGI:Comando. Debe sincronizarse primero su activación."
            );
        }

        if (employee != null && employee.updatedFromSourceAt != null
            && sourceTimestamp.isBefore(employee.updatedFromSourceAt)) {
            recordReceipt(tenantId, key, contentHash, sourceTimestamp, "STALE_IGNORED");
            return;
        }

        Company sourceCompany = resolveSourceCompany(tenantId, request, employee);

        if (employee == null) {
            employee = new EmployeeOperationalSnapshot();
            employee.instanceCountryId = tenantId;
            employee.employeeId = employeeId;
            employee.personaId = personaId;
            employee.companyId = sourceCompany.id;
            employee.requiredChange = false;
        } else {
            employee.companyId = sourceCompany.id;
        }
        if (blank(employee.photoKey)) employee.photoKey = localAvatarKey(employeeId);

        employee.fullName = request.fullName().trim();
        employee.roleCode = request.roleCode().trim();
        employee.employmentStatus = employmentStatus;
        employee.updatedFromSourceAt = sourceTimestamp;
        if (!employee.isPersistent()) employee.persist();

        if ("ACTIVE".equals(employmentStatus)) {
            reconcilePrimaryMembership(tenantId, employee, sourceCompany, sourceTimestamp);
        } else {
            closePrimaryMembership(tenantId, employee, sourceTimestamp);
        }
        recordReceipt(tenantId, key, contentHash, sourceTimestamp, "APPLIED");
    }

    private void reconcilePrimaryMembership(
        UUID tenantId,
        EmployeeOperationalSnapshot employee,
        Company sourceCompany,
        Instant sourceTimestamp
    ) {
        CompanyMembershipEntity membership = CompanyMembershipEntity.find(
            "instanceCountryId=?1 and employeeId=?2 and membershipType='PRIMARY' and endsAt is null",
            tenantId, employee.employeeId
        ).firstResult();

        if (membership != null && !membership.companyId.equals(sourceCompany.id)) {
            membership.endsAt = sourceTimestamp.isBefore(membership.startsAt) ? membership.startsAt : sourceTimestamp;
            membership.persistAndFlush();
            membership = null;
        }

        if (membership == null) {
            membership = new CompanyMembershipEntity();
            membership.instanceCountryId = tenantId;
            membership.companyId = sourceCompany.id;
            membership.employeeId = employee.employeeId;
            membership.membershipType = "PRIMARY";
            membership.startsAt = sourceTimestamp;
            membership.requiredChange = employee.requiredChange;
            membership.roleCode = employee.roleCode;
            membership.persist();
        } else {
            membership.roleCode = employee.roleCode;
            membership.requiredChange = employee.requiredChange;
        }
    }

    private void closePrimaryMembership(
        UUID tenantId,
        EmployeeOperationalSnapshot employee,
        Instant sourceTimestamp
    ) {
        List<CompanyMembershipEntity> memberships = CompanyMembershipEntity.list(
            "instanceCountryId=?1 and employeeId=?2 and membershipType='PRIMARY' and endsAt is null",
            tenantId, employee.employeeId
        );
        for (CompanyMembershipEntity membership : memberships) {
            membership.endsAt = sourceTimestamp.isBefore(membership.startsAt)
                ? membership.startsAt
                : sourceTimestamp;
        }
    }

    /**
     * SIC:RRHH is SoR of Persona->Compañía. New personnel must therefore arrive
     * with a company identity. For transition compatibility, an existing employee
     * may omit it and retain the already persisted source company.
     */
    private Company resolveSourceCompany(
        UUID tenantId,
        RrhhEmployeeEventResource.EmployeeEventRequest request,
        EmployeeOperationalSnapshot existingEmployee
    ) {
        Company byCore = null;
        if (request.companyCoreCatalogId() != null) {
            List<Company> rows = Company.list(
                "instanceCountryId=?1 and coreCatalogId=?2", tenantId, request.companyCoreCatalogId()
            );
            if (rows.size() != 1) {
                throw new BadRequestException("companyCoreCatalogId no resuelve exactamente una Compañía SGI del tenant.");
            }
            byCore = rows.getFirst();
        }

        Company byCode = null;
        if (!blank(request.companyCode())) {
            List<Company> rows = Company.list(
                "instanceCountryId=?1 and code=?2", tenantId, request.companyCode().trim()
            );
            if (rows.size() != 1) {
                throw new BadRequestException("companyCode no resuelve exactamente una Compañía SGI del tenant.");
            }
            byCode = rows.getFirst();
        }

        if (byCore != null && byCode != null && !byCore.id.equals(byCode.id)) {
            throw new BadRequestException("companyCoreCatalogId y companyCode identifican Compañías distintas.");
        }
        if (byCore != null) return byCore;
        if (byCode != null) return byCode;

        if (existingEmployee != null && existingEmployee.companyId != null) {
            Company existing = Company.find(
                "instanceCountryId=?1 and id=?2", tenantId, existingEmployee.companyId
            ).firstResult();
            if (existing != null) return existing;
            throw new InternalServerErrorException("La Compañía actual del empleado no existe en el tenant.");
        }

        throw new BadRequestException(
            "SIC:RRHH es SoR de Persona–Compañía: un empleado nuevo requiere companyCoreCatalogId o companyCode."
        );
    }

    private void recordReceipt(
        UUID tenantId,
        String idempotencyKey,
        String contentHash,
        Instant sourceTimestamp,
        String status
    ) {
        RrhhEmployeeEventReceipt receipt = new RrhhEmployeeEventReceipt();
        receipt.instanceCountryId = tenantId;
        receipt.idempotencyKey = idempotencyKey;
        receipt.contentHash = contentHash;
        receipt.sourceUpdatedAt = sourceTimestamp;
        receipt.processingStatus = status;
        receipt.processedAt = Instant.now();
        receipt.persist();
    }

    private String contentHash(RrhhEmployeeEventResource.EmployeeEventRequest request) {
        String canonical = String.join("\u001f",
            Objects.toString(request.employeeId(), ""),
            Objects.toString(request.personaId(), ""),
            Objects.toString(request.canonicalEmployeeId(), ""),
            trim(request.fullName()),
            trim(request.roleCode()),
            trim(request.employmentStatus()).toUpperCase(),
            Objects.toString(request.updatedFromSourceAt(), ""),
            Objects.toString(request.companyCoreCatalogId(), ""),
            trim(request.companyCode()).toUpperCase()
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new InternalServerErrorException("SHA-256 no está disponible.", e);
        }
    }

    UUID internalEmployeeId(UUID tenantId, Long sourceEmployeeId) {
        String source = "SIC_RRHH|" + tenantId + "|PERSONAS|" + sourceEmployeeId;
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    UUID resolveEmployeeId(UUID tenantId, RrhhEmployeeEventResource.EmployeeEventRequest request) {
        if (useCanonicalEmployeeId) {
            if (request.canonicalEmployeeId() == null) {
                throw new BadRequestException(
                    "canonicalEmployeeId es obligatorio cuando SGI_RRHH_USE_CANONICAL_EMPLOYEE_ID=true."
                );
            }
            return request.canonicalEmployeeId();
        }
        Long personaId = sourcePersonaId(request);
        if (personaId == null) {
            throw new BadRequestException("personaId es obligatorio para derivar el employeeId interno.");
        }
        return internalEmployeeId(tenantId, personaId);
    }

    Long sourcePersonaId(RrhhEmployeeEventResource.EmployeeEventRequest request) {
        return request.personaId() != null ? request.personaId() : request.employeeId();
    }

    String localAvatarKey(UUID employeeId) {
        return LOCAL_AVATAR_KEYS.get(Math.floorMod(employeeId.hashCode(), LOCAL_AVATAR_KEYS.size()));
    }

    private void validate(RrhhEmployeeEventResource.EmployeeEventRequest request) {
        if (request == null) throw new BadRequestException("El cuerpo del evento es obligatorio.");
        if (request.employeeId() != null && request.employeeId() <= 0) {
            throw new BadRequestException("employeeId debe ser mayor que cero cuando se envía.");
        }
        if (request.personaId() != null && request.personaId() <= 0) {
            throw new BadRequestException("personaId debe ser mayor que cero cuando se envía.");
        }
        if (request.employeeId() != null && request.personaId() != null
            && !request.employeeId().equals(request.personaId())) {
            throw new BadRequestException("employeeId legacy y personaId deben contener el mismo personas.id.");
        }
        resolveEmployeeId(tenant.instanceCountryId(), request);
        if (blank(request.fullName())) throw new BadRequestException("fullName es obligatorio.");
        if (request.fullName().trim().length() > 255) {
            throw new BadRequestException("fullName supera el máximo de 255 caracteres.");
        }
        if (blank(request.roleCode())) throw new BadRequestException("roleCode es obligatorio.");
        if (request.roleCode().trim().length() > 80) {
            throw new BadRequestException("roleCode supera el máximo de 80 caracteres.");
        }
        String employmentStatus = trim(request.employmentStatus()).toUpperCase();
        if (!"ACTIVE".equals(employmentStatus) && !"INACTIVE".equals(employmentStatus)) {
            throw new BadRequestException("Solo se sincronizan empleados activos de Seguridad Física.");
        }
        if (request.updatedFromSourceAt() == null) {
            throw new BadRequestException("updatedFromSourceAt es obligatorio.");
        }
        if (!blank(request.companyCode()) && request.companyCode().trim().length() > 80) {
            throw new BadRequestException("companyCode supera el máximo de 80 caracteres.");
        }
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static String trim(String value) { return value == null ? "" : value.trim(); }
}
