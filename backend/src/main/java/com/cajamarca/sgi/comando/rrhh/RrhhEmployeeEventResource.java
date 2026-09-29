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

@Path("/api/v1/inbound/sic-rrhh/employee-events")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class RrhhEmployeeEventResource {
    private static final String CONTRACT_VERSION = "v1";

    @Inject RrhhEmployeeSyncService syncService;
    @Inject CredentialRefResolver credentials;

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

    @POST
    public EmployeeEventResponse receive(
        @HeaderParam("Authorization") String authorization,
        @HeaderParam("X-Correlation-Id") String correlationId,
        @HeaderParam("X-Interconnection-Id") String interconnectionId,
        @HeaderParam("X-Contract-Version") String contractVersion,
        @HeaderParam("Idempotency-Key") String idempotencyKey,
        EmployeeEventRequest request
    ) {
        requireServiceCredential(authorization);
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
        if (trim(idempotencyKey).isEmpty()) {
            throw new BadRequestException("Idempotency-Key es obligatorio.");
        }
        syncService.synchronize(request, idempotencyKey);
        return new EmployeeEventResponse(true, effectiveCorrelationId);
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
