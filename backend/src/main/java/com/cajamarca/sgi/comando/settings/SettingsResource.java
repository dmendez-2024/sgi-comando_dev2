package com.cajamarca.sgi.comando.settings;

import com.cajamarca.sgi.comando.common.TenantContext;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.Set;

/** Configuración → parámetros operativos de la instancia. Por ahora: radio GPS predeterminado de las fotos del agente. */
@Path("/api/settings/evidence-location") @Produces(MediaType.APPLICATION_JSON)
public class SettingsResource {
    /** Roles que pueden cambiar parámetros de toda la instancia (país). */
    static final Set<String> EDITORS = Set.of("PRESIDENTE", "DIRECTOR_OPERACIONES_LATAM", "DIRECTOR_OPERACIONES_NACIONAL", "DIRECTOR_NACIONAL");

    public record EvidenceLocationDto(int defaultRadiusM, int systemRadiusM, boolean configured, String updatedBy, Instant updatedAt,
                                      boolean canEdit, int minRadiusM, int maxRadiusM) {}
    public record SaveEvidenceLocation(Integer defaultRadiusM) {}

    @Inject EvidenceLocationSettings settings;
    @Inject TenantContext tenant;
    @Inject SecurityIdentity identity;

    @GET
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
    public EvidenceLocationDto get() { return dto(); }

    @PUT @Consumes(MediaType.APPLICATION_JSON) @Transactional
    @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL"})
    public EvidenceLocationDto save(SaveEvidenceLocation req) {
        if (req == null || req.defaultRadiusM() == null) throw new BadRequestException("Ingresa el radio predeterminado en metros");
        settings.saveDefaultRadius(tenant.instanceCountryId(), req.defaultRadiusM(), identity.getPrincipal().getName());
        return dto();
    }

    private EvidenceLocationDto dto() {
        Object[] audit = settings.audit(tenant.instanceCountryId());
        boolean canEdit = identity.getRoles().stream().anyMatch(EDITORS::contains);
        Instant at = audit == null ? null : audit[1] instanceof Instant i ? i : audit[1] instanceof java.sql.Timestamp ts ? ts.toInstant()
            : audit[1] instanceof java.time.OffsetDateTime o ? o.toInstant() : null;
        return new EvidenceLocationDto(settings.defaultRadius(), settings.fallbackRadius(), audit != null, audit == null ? null : audit[0].toString(), at,
            canEdit, EvidenceLocationSettings.MIN_RADIUS, EvidenceLocationSettings.MAX_RADIUS);
    }
}
