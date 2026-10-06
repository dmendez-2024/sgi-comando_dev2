package com.cajamarca.sgi.comando.rrhh;

import com.cajamarca.sgi.comando.interconnections.InterconnectionIds;
import com.cajamarca.sgi.comando.interconnections.CredentialRefResolver;
import com.cajamarca.sgi.comando.interconnections.InterconnectionException;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
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

    @ConfigProperty(name = "sgi.rrhh.inbound.credential-ref", defaultValue = "")
    Optional<String> rrhhCredentialRef;

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
        requireServiceCredential(authorization, rrhhCredentialRef, "SIC:RRHH");
        validateRrhhContractHeaders(correlationId, interconnectionId, contractVersion);
        if (trim(idempotencyKey).isEmpty()) {
            throw new BadRequestException("Idempotency-Key es obligatorio.");
        }
        syncService.synchronize(request, idempotencyKey);
        return new EmployeeEventResponse(true, trim(correlationId));
    }

    private void validateRrhhContractHeaders(
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
        validateCommonContractHeaders(correlationId, contractVersion);
    }

    private void validateCommonContractHeaders(String correlationId, String contractVersion) {
        if (!CONTRACT_VERSION.equalsIgnoreCase(trim(contractVersion))) {
            throw new BadRequestException("X-Contract-Version debe ser v1.");
        }
        String effectiveCorrelationId = trim(correlationId);
        if (effectiveCorrelationId.isEmpty()) {
            throw new BadRequestException("X-Correlation-Id es obligatorio.");
        }
    }

    private void requireServiceCredential(
        String authorization,
        Optional<String> configuredCredentialRef,
        String integrationName
    ) {
        String ref = trim(configuredCredentialRef.orElse(""));
        if (ref.isEmpty()) {
            throw new ServiceUnavailableException(
                "La credential_ref de integración " + integrationName + " no está configurada."
            );
        }

        final String configured;
        try {
            configured = credentials.resolve(ref);
        } catch (InterconnectionException e) {
            throw new ServiceUnavailableException(
                "La credencial de integración " + integrationName + " no está disponible."
            );
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
                    .entity("Credencial de integración " + integrationName + " inválida.")
                    .type(MediaType.TEXT_PLAIN)
                    .build()
            );
        }
    }

    private static String trim(String value) { return value == null ? "" : value.trim(); }
}
