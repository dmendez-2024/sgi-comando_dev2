package com.cajamarca.sgi.comando.rrhh;

import com.cajamarca.sgi.comando.assignments.AssignmentRolePolicy;
import com.cajamarca.sgi.comando.assignments.CompanyMembershipEntity;
import com.cajamarca.sgi.comando.assignments.EmployeeOperationalSnapshot;
import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.companies.Company;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
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
    private static final UUID KAIBIL_CORE_CATALOG_ID =
        UUID.fromString("a2000000-0000-0000-0000-000000000005");
    private static final String KAIBIL_CODE = "KAI-001";

    @Inject TenantContext tenant;
    @Inject AssignmentRolePolicy rolePolicy;

    @ConfigProperty(name = "sgi.integrations.rrhh.use-canonical-employee-id", defaultValue = "false")
    boolean useCanonicalEmployeeId;

    @Transactional
    public void synchronize(RrhhEmployeeEventResource.EmployeeEventRequest request) {
        validate(request);
        UUID tenantId = tenant.instanceCountryId();
        Long personaId = sourcePersonaId(request);
        UUID employeeId = resolveEmployeeId(tenantId, request);
        Instant sourceTimestamp = request.updatedFromSourceAt();

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

        if (employee != null && employee.updatedFromSourceAt != null
            && sourceTimestamp.isBefore(employee.updatedFromSourceAt)) {
            return;
        }

        if (employee == null) {
            employee = new EmployeeOperationalSnapshot();
            employee.instanceCountryId = tenantId;
            employee.employeeId = employeeId;
            employee.personaId = personaId;
            employee.companyId = kaibil(tenantId).id;
            employee.requiredChange = false;
        }
        if (blank(employee.photoKey)) employee.photoKey = localAvatarKey(employeeId);

        employee.fullName = request.fullName().trim();
        employee.roleCode = request.roleCode().trim();
        employee.employmentStatus = "ACTIVE";
        employee.updatedFromSourceAt = sourceTimestamp;
        if (!employee.isPersistent()) employee.persist();

        CompanyMembershipEntity membership = CompanyMembershipEntity.find(
            "instanceCountryId=?1 and employeeId=?2 and membershipType='PRIMARY' and endsAt is null",
            tenantId, employeeId
        ).firstResult();
        if (membership == null) {
            membership = new CompanyMembershipEntity();
            membership.instanceCountryId = tenantId;
            membership.companyId = employee.companyId;
            membership.employeeId = employeeId;
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
        if (!"ACTIVE".equalsIgnoreCase(trim(request.employmentStatus()))) {
            throw new BadRequestException("Solo se sincronizan empleados activos de Seguridad Física.");
        }
        if (request.updatedFromSourceAt() == null) {
            throw new BadRequestException("updatedFromSourceAt es obligatorio.");
        }
    }

    private Company kaibil(UUID tenantId) {
        List<Company> canonical = Company.list(
            "instanceCountryId=?1 and coreCatalogId=?2", tenantId, KAIBIL_CORE_CATALOG_ID
        );
        if (canonical.size() == 1) return canonical.get(0);
        if (canonical.size() > 1) {
            throw new InternalServerErrorException("Existe más de una Compañía vinculada al catálogo CORE de Kaibil.");
        }
        List<Company> compatible = Company.list(
            "instanceCountryId=?1 and code=?2 and companyType='COORDINATION' and alwaysActive=true",
            tenantId, KAIBIL_CODE
        );
        if (compatible.size() == 1) return compatible.get(0);
        throw new InternalServerErrorException("No se pudo resolver la Compañía Kaibil activa del tenant.");
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private static String trim(String value) { return value == null ? "" : value.trim(); }
}
