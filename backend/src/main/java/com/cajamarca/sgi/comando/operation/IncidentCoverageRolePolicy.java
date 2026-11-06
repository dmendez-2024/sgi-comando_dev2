package com.cajamarca.sgi.comando.operation;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

import java.util.Set;

/** El cargo Senior de Consola recibe MONITOR desde IDENT; no es un rol separado. */
@ApplicationScoped
public class IncidentCoverageRolePolicy {
    private static final Set<String> CURRENT_SHIFT_ROLES = Set.of(
            "PRESIDENTE", "GERENCIA_GENERAL", "DIRECTOR_OPERACIONES_LATAM",
            "DIRECTOR_OPERACIONES_NACIONAL", "DIRECTOR_NACIONAL", "MONITOR");
    private static final Set<String> FUTURE_SHIFT_ROLES = Set.of(
            "PRESIDENTE", "GERENCIA_GENERAL", "DIRECTOR_OPERACIONES_LATAM",
            "DIRECTOR_OPERACIONES_NACIONAL", "DIRECTOR_NACIONAL", "MONITOR",
            "COORDINADOR_COMPANIA", "ASISTENTE_COORDINACION");

    @Inject SecurityIdentity identity;

    public boolean canCoverCurrentShift() {
        return allowsCurrentShift(identity.getRoles());
    }

    public boolean canCoverFutureShift() {
        return allowsFutureShift(identity.getRoles());
    }

    public void requireCurrentShiftPermission() {
        if (!canCoverCurrentShift())
            throw new ForbiddenException("Su perfil solo puede reasignar turnos futuros; el turno en curso requiere Dirección Nacional o superior, o el rol Monitor.");
    }

    public void requireFutureShiftPermission() {
        if (!canCoverFutureShift())
            throw new ForbiddenException("Su perfil no puede reasignar turnos futuros desde Consola.");
    }

    static boolean allowsCurrentShift(Set<String> roles) {
        return roles.stream().anyMatch(CURRENT_SHIFT_ROLES::contains);
    }

    static boolean allowsFutureShift(Set<String> roles) {
        return roles.stream().anyMatch(FUTURE_SHIFT_ROLES::contains);
    }
}
