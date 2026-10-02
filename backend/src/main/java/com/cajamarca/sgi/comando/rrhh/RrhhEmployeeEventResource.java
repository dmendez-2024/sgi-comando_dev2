package com.cajamarca.sgi.comando.rrhh;

import com.cajamarca.sgi.comando.assignments.CompanyMembershipEntity;
import com.cajamarca.sgi.comando.assignments.EmployeeOperationalSnapshot;
import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.companies.Company;
import com.cajamarca.sgi.comando.interconnections.InterconnectionIds;
import com.cajamarca.sgi.comando.interconnections.CredentialRefResolver;
import com.cajamarca.sgi.comando.interconnections.InterconnectionException;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Path("/api/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class RrhhEmployeeEventResource {
    private static final String CONTRACT_VERSION = "v1";

    @Inject RrhhEmployeeSyncService syncService;
    @Inject CredentialRefResolver credentials;
    @Inject TenantContext tenant;

    @ConfigProperty(name = "sgi.rrhh.inbound.credential-ref", defaultValue = "")
    Optional<String> credentialRef;

    public record EmployeeEventRequest(
        Long employeeId,
        Long personaId,
        UUID canonicalEmployeeId,
        String fullName,
        String roleCode,
        String employmentStatus,
        Instant updatedFromSourceAt,
        UUID companyCoreCatalogId,
        String companyCode
    ) {}

    public record EmployeeEventResponse(boolean accepted, String correlationId) {}
    public record EmployeeCompanyResponse(
        UUID id,
        UUID coreCatalogId,
        String code,
        String name,
        String status
    ) {}
    public record ActiveEmployeeResponse(
        Long personaId,
        UUID employeeId,
        String fullName,
        String roleCode,
        String employmentStatus,
        EmployeeCompanyResponse company,
        boolean companyMembershipActive
    ) {}
    private record MembershipKey(UUID employeeId, UUID companyId) {}
    public record ApiError(String code, String message) {}

    @POST
    @Path("/inbound/sic-rrhh/employee-events")
    public EmployeeEventResponse receive(
        @HeaderParam("Authorization") String authorization,
        @HeaderParam("X-Correlation-Id") String correlationId,
        @HeaderParam("X-Interconnection-Id") String interconnectionId,
        @HeaderParam("X-Contract-Version") String contractVersion,
        @HeaderParam("Idempotency-Key") String idempotencyKey,
        EmployeeEventRequest request
    ) {
        requireServiceCredential(authorization);
        validateContractHeaders(correlationId, interconnectionId, contractVersion);
        if (trim(idempotencyKey).isEmpty()) {
            throw new BadRequestException("Idempotency-Key es obligatorio.");
        }
        syncService.synchronize(request, idempotencyKey);
        return new EmployeeEventResponse(true, trim(correlationId));
    }

    @GET
    @Path("/employees")
    public List<ActiveEmployeeResponse> findActiveByInstanceCountry(
        @QueryParam("instanceCountryId") UUID instanceCountryId,
        @HeaderParam("Authorization") String authorization,
        @HeaderParam("X-Correlation-Id") String correlationId,
        @HeaderParam("X-Interconnection-Id") String interconnectionId,
        @HeaderParam("X-Contract-Version") String contractVersion
    ) {
        requireServiceCredential(authorization);
        validateContractHeaders(correlationId, interconnectionId, contractVersion);
        if (instanceCountryId == null) {
            throw apiError(
                Response.Status.BAD_REQUEST,
                "INVALID_INSTANCE_COUNTRY_ID",
                "instanceCountryId es obligatorio."
            );
        }

        UUID tenantId = tenant.instanceCountryId();
        if (!tenantId.equals(instanceCountryId)) {
            throw apiError(
                Response.Status.NOT_FOUND,
                "INSTANCE_COUNTRY_NOT_FOUND",
                "La empresa indicada no corresponde al instanceCountryId vigente de SGI:Comando."
            );
        }

        List<EmployeeOperationalSnapshot> employees = EmployeeOperationalSnapshot.list(
            "instanceCountryId=?1 and employmentStatus='ACTIVE' order by fullName, employeeId",
            instanceCountryId
        );
        Map<UUID, Company> companiesById = new HashMap<>();
        for (Company company : Company.<Company>list("instanceCountryId=?1", instanceCountryId)) {
            companiesById.put(company.id, company);
        }

        Set<MembershipKey> activeMemberships = new HashSet<>();
        List<CompanyMembershipEntity> memberships = CompanyMembershipEntity.list(
            "instanceCountryId=?1 and membershipType='PRIMARY' and endsAt is null",
            instanceCountryId
        );
        for (CompanyMembershipEntity membership : memberships) {
            activeMemberships.add(new MembershipKey(membership.employeeId, membership.companyId));
        }

        return employees.stream()
            .map(employee -> activeEmployeeResponse(employee, companiesById, activeMemberships))
            .toList();
    }

    private ActiveEmployeeResponse activeEmployeeResponse(
        EmployeeOperationalSnapshot employee,
        Map<UUID, Company> companiesById,
        Set<MembershipKey> activeMemberships
    ) {
        Company company = companiesById.get(employee.companyId);
        if (company == null) {
            throw apiError(
                Response.Status.INTERNAL_SERVER_ERROR,
                "EMPLOYEE_COMPANY_NOT_FOUND",
                "La compañía asociada al empleado no existe dentro de la empresa indicada."
            );
        }

        boolean activeMembership = activeMemberships.contains(
            new MembershipKey(employee.employeeId, employee.companyId)
        );

        return new ActiveEmployeeResponse(
            employee.personaId,
            employee.employeeId,
            employee.fullName,
            employee.roleCode,
            employee.employmentStatus,
            new EmployeeCompanyResponse(
                company.id,
                company.coreCatalogId,
                company.code,
                company.name,
                company.status
            ),
            activeMembership
        );
    }

    private void validateContractHeaders(
        String correlationId,
        String interconnectionId,
        String contractVersion
    ) {
        if (!InterconnectionIds.matches(
            interconnectionId,
            InterconnectionIds.DHO_MASTER_EVENTS,
            InterconnectionIds.TRANSITIONAL_RRHH_MASTER_EVENTS,
            InterconnectionIds.LEGACY_RRHH_MASTER_EVENTS
        )) {
            throw new BadRequestException("X-Interconnection-Id no corresponde al contrato SIC:DHO → SGI:Comando.");
        }
        if (!CONTRACT_VERSION.equalsIgnoreCase(trim(contractVersion))) {
            throw new BadRequestException("X-Contract-Version debe ser v1.");
        }
        String effectiveCorrelationId = trim(correlationId);
        if (effectiveCorrelationId.isEmpty()) {
            throw new BadRequestException("X-Correlation-Id es obligatorio.");
        }
    }

    private WebApplicationException apiError(Response.Status status, String code, String message) {
        return new WebApplicationException(
            Response.status(status)
                .entity(new ApiError(code, message))
                .type(MediaType.APPLICATION_JSON)
                .build()
        );
    }

    private void requireServiceCredential(String authorization) {
        String ref = trim(credentialRef.orElse(""));
        if (ref.isEmpty()) {
            throw new ServiceUnavailableException("La credential_ref de integración SIC:RRHH no está configurada.");
        }

        final String configured;
        try {
            configured = credentials.resolve(ref);
        } catch (InterconnectionException e) {
            throw new ServiceUnavailableException("La credencial de integración SIC:RRHH no está disponible.");
        }

        String prefix = "Bearer ";
        String provided = authorization != null && authorization.startsWith(prefix)
            ? authorization.substring(prefix.length()).trim()
            : "";
        if (!MessageDigest.isEqual(
            configured.getBytes(StandardCharsets.UTF_8),
            provided.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new WebApplicationException(
                Response.status(Response.Status.UNAUTHORIZED)
                    .entity("Credencial de integración SIC:RRHH inválida.")
                    .type(MediaType.TEXT_PLAIN)
                    .build()
            );
        }
    }

    private static String trim(String value) { return value == null ? "" : value.trim(); }
}
