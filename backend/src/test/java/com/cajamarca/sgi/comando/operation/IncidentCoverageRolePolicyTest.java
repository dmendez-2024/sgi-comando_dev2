package com.cajamarca.sgi.comando.operation;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentCoverageRolePolicyTest {
    @Test
    void coordinatorsAndAssistantsCanOnlyCoverFutureShifts() {
        for (String role : Set.of("COORDINADOR_COMPANIA", "ASISTENTE_COORDINACION")) {
            assertTrue(IncidentCoverageRolePolicy.allowsFutureShift(Set.of(role)));
            assertFalse(IncidentCoverageRolePolicy.allowsCurrentShift(Set.of(role)));
        }
    }

    @Test
    void nationalDirectorsAndHigherCanCoverCurrentAndFutureShifts() {
        for (String role : Set.of("DIRECTOR_NACIONAL", "DIRECTOR_OPERACIONES_NACIONAL",
                "DIRECTOR_OPERACIONES_LATAM", "GERENCIA_GENERAL", "PRESIDENTE")) {
            assertTrue(IncidentCoverageRolePolicy.allowsFutureShift(Set.of(role)));
            assertTrue(IncidentCoverageRolePolicy.allowsCurrentShift(Set.of(role)));
        }
    }

    @Test
    void monitorRoleCoversBothMonitorAndSeniorConsoleCargo() {
        assertTrue(IncidentCoverageRolePolicy.allowsFutureShift(Set.of("MONITOR")));
        assertTrue(IncidentCoverageRolePolicy.allowsCurrentShift(Set.of("MONITOR")));
    }

    @Test
    void otherRolesDoNotGainCoveragePermission() {
        for (String role : Set.of("JEFE_REGIONAL", "DIRECTOR_ZONAL", "SUPERVISOR_SEGURIDAD", "AGENTE_SEGURIDAD")) {
            assertFalse(IncidentCoverageRolePolicy.allowsFutureShift(Set.of(role)));
            assertFalse(IncidentCoverageRolePolicy.allowsCurrentShift(Set.of(role)));
        }
    }
}
