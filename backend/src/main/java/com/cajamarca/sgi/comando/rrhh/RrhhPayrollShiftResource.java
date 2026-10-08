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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Path("/api/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class RrhhPayrollShiftResource {
    private static final String CONTRACT_VERSION = "v1";

    @Inject RrhhPayrollShiftService payrollShiftService;
    @Inject CredentialRefResolver credentials;

    @ConfigProperty(name = "sgi.rrhh.inbound.credential-ref", defaultValue = "")
    Optional<String> rrhhCredentialRef;

    public record PayrollShiftQuery(Long personaId, Instant startsAt, Instant endsAt) {}

    public record PayrollShift(
        UUID assignmentId,
        UUID shiftOccurrenceId,
        Long personaId,
        String clientTaxIdentifier,
        String clientName,
        String tier,
        String rotationCode,
        Instant startsAt,
        Instant endsAt
    ) {}

    public record PayrollShiftQueryResponse(
        boolean successful,
        String correlationId,
        List<PayrollShift> items
    ) {}

    @POST
    @Path("/inbound/sic-rrhh/payroll-shifts/query")
    public PayrollShiftQueryResponse query(
        @HeaderParam("Authorization") String authorization,
        @HeaderParam("X-Correlation-Id") String correlationId,
        @HeaderParam("X-Interconnection-Id") String interconnectionId,
        @HeaderParam("X-Contract-Version") String contractVersion,
        PayrollShiftQuery request
    ) {
        requireServiceCredential(authorization);
        validateHeaders(correlationId, interconnectionId, contractVersion);
        return new PayrollShiftQueryResponse(
            true,
            trim(correlationId),
            payrollShiftService.findPublishedShifts(request)
        );
    }

    static void validateContract(PayrollShiftQuery request) {
        if (request == null) throw new BadRequestException("El cuerpo de consulta es obligatorio.");
        if (request.personaId() == null || request.personaId() <= 0) {
            throw new BadRequestException("personaId es obligatorio y debe ser mayor que cero.");
        }
        if (request.startsAt() == null || request.endsAt() == null
            || !request.endsAt().isAfter(request.startsAt())) {
            throw new BadRequestException(
                "startsAt y endsAt son obligatorios; endsAt debe ser posterior a startsAt."
            );
        }
    }

    private void validateHeaders(String correlationId, String interconnectionId, String contractVersion) {
        if (!InterconnectionIds.matches(
            interconnectionId,
            InterconnectionIds.DHO_PAYROLL_SHIFT_QUERY
        )) {
            throw new BadRequestException("X-Interconnection-Id no corresponde al contrato de nómina.");
        }
        if (!CONTRACT_VERSION.equalsIgnoreCase(trim(contractVersion))) {
            throw new BadRequestException("X-Contract-Version debe ser v1.");
        }
        if (trim(correlationId).isEmpty()) {
            throw new BadRequestException("X-Correlation-Id es obligatorio.");
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
