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
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Optional;
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
    public record EmployeeByPersonaResponse(
        Long personaId,
        UUID employeeId,
        String fullName,
        String roleCode,
        String employmentStatus,
        EmployeeCompanyResponse company,
        boolean companyMembershipActive
    ) {}
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
    @Path("/employees/by-persona/{personaId}")
    public EmployeeByPersonaResponse findByPersonaId(
        @PathParam("personaId") Long personaId,
        @HeaderParam("Authorization") String authorization,
        @HeaderParam("X-Correlation-Id") String correlationId,
        @HeaderParam("X-Interconnection-Id") String interconnectionId,
        @HeaderParam("X-Contract-Version") String contractVersion
    ) {
        requireServiceCredential(authorization);
        validateContractHeaders(correlationId, interconnectionId, contractVersion);
        if (personaId == null || personaId <= 0) {
            throw apiError(Response.Status.BAD_REQUEST, "INVALID_PERSONA_ID", "personaId debe ser mayor que cero.");
        }

        UUID tenantId = tenant.instanceCountryId();
        EmployeeOperationalSnapshot employee = EmployeeOperationalSnapshot.find(
            "instanceCountryId=?1 and personaId=?2", tenantId, personaId
        ).firstResult();
        if (employee == null) {
            throw apiError(
                Response.Status.NOT_FOUND,
                "EMPLOYEE_NOT_FOUND",
                "No existe un empleado asociado al personaId indicado en esta Instancia-País."
            );
        }

        Company company = Company.find(
            "instanceCountryId=?1 and id=?2", tenantId, employee.companyId
        ).firstResult();
        if (company == null) {
            throw apiError(
                Response.Status.INTERNAL_SERVER_ERROR,
                "EMPLOYEE_COMPANY_NOT_FOUND",
                "La compañía asociada al empleado no existe en esta Instancia-País."
            );
        }

        boolean activeMembership = CompanyMembershipEntity.count(
            "instanceCountryId=?1 and employeeId=?2 and companyId=?3 "
                + "and membershipType='PRIMARY' and endsAt is null",
            tenantId, employee.employeeId, employee.companyId
        ) > 0;

        return new EmployeeByPersonaResponse(
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
        if (!InterconnectionIds.matches(interconnectionId, InterconnectionIds.RRHH_MASTER_EVENTS, InterconnectionIds.LEGACY_RRHH_MASTER_EVENTS)) {
            throw new BadRequestException("X-Interconnection-Id no corresponde al contrato SIC:RRHH → SGI:Comando.");
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
