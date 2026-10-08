package com.cajamarca.sgi.comando.rrhh;

import com.cajamarca.sgi.comando.interconnections.CredentialRefResolver;
import com.cajamarca.sgi.comando.interconnections.InterconnectionException;
import com.cajamarca.sgi.comando.interconnections.InterconnectionIds;
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

@Path("/api/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class RrhhUnavailabilityEventResource {
    private static final String CONTRACT_VERSION = "v1";

    @Inject RrhhUnavailabilitySyncService syncService;
    @Inject CredentialRefResolver credentials;

    @ConfigProperty(name = "sgi.rrhh.inbound.credential-ref", defaultValue = "")
    Optional<String> rrhhCredentialRef;

    public record UnavailabilityEventRequest(
        Long sourceRecordId,
        Long personaId,
        String type,
        Instant startsAt,
        Instant endsAt,
        String sourceStatus,
        Long sourceReasonId,
        String sourceReasonLabel,
        String sourceState,
        Instant updatedFromSourceAt
    ) {}

    public record UnavailabilityEventResponse(
        boolean accepted,
        String correlationId,
        String sourceRef,
        String processingStatus
    ) {}

    @POST
    @Path("/inbound/sic-rrhh/unavailability-events")
    public UnavailabilityEventResponse receive(
        @HeaderParam("Authorization") String authorization,
        @HeaderParam("X-Correlation-Id") String correlationId,
        @HeaderParam("X-Interconnection-Id") String interconnectionId,
        @HeaderParam("X-Contract-Version") String contractVersion,
        @HeaderParam("Idempotency-Key") String idempotencyKey,
        UnavailabilityEventRequest request
    ) {
        requireServiceCredential(authorization);
        validateHeaders(correlationId, interconnectionId, contractVersion, idempotencyKey);
        RrhhUnavailabilitySyncService.SyncResult result = syncService.synchronize(request, trim(idempotencyKey));
        return new UnavailabilityEventResponse(true, trim(correlationId), result.sourceRef(), result.processingStatus());
    }

    private void validateHeaders(
        String correlationId,
        String interconnectionId,
        String contractVersion,
        String idempotencyKey
    ) {
        if (!InterconnectionIds.matches(
            interconnectionId,
            InterconnectionIds.DHO_UNAVAILABILITY_EVENTS
        )) {
            throw new BadRequestException("X-Interconnection-Id no corresponde al contrato de permisos/vacaciones.");
        }
        if (!CONTRACT_VERSION.equalsIgnoreCase(trim(contractVersion))) {
            throw new BadRequestException("X-Contract-Version debe ser v1.");
        }
        if (trim(correlationId).isEmpty()) {
            throw new BadRequestException("X-Correlation-Id es obligatorio.");
        }
        String key = trim(idempotencyKey);
        if (key.isEmpty() || key.length() > 160) {
            throw new BadRequestException("Idempotency-Key es obligatorio y debe tener máximo 160 caracteres.");
        }
    }

    private void requireServiceCredential(String authorization) {
        String ref = trim(rrhhCredentialRef.orElse(""));
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
