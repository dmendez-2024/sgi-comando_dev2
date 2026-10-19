package com.cajamarca.sgi.comando.territory;

import com.cajamarca.sgi.comando.assignments.EmployeeOperationalSnapshot;
import com.cajamarca.sgi.comando.common.TenantContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Mantiene el alcance IDENT derivado del responsable configurado en Zona, Región o Compañía. */
@ApplicationScoped
public class ResponsibleOperationalScopeService {
    private static final Set<String> SUPPORTED_SCOPE_TYPES = Set.of("ZONE", "REGION", "COMPANY");

    @Inject TenantContext tenant;
    @Inject EntityManager entityManager;

    @Transactional
    public void synchronize(String scopeType, UUID scopeId, UUID previousResponsibleEmployeeId, UUID newResponsibleEmployeeId) {
        synchronize(tenant.instanceCountryId(), scopeType, scopeId, previousResponsibleEmployeeId, newResponsibleEmployeeId);
    }

    void synchronize(UUID instanceCountryId, String scopeType, UUID scopeId, UUID previousResponsibleEmployeeId, UUID newResponsibleEmployeeId) {
        String normalizedScopeType = normalizeScopeType(scopeType);
        if (instanceCountryId == null) throw new BadRequestException("La Instancia-País es obligatoria para asignar el alcance operacional.");
        if (scopeId == null) throw new BadRequestException("El objeto territorial es obligatorio para asignar el alcance operacional.");

        String username = usernameFor(normalizedScopeType);
        Long previousPersonaId = personaId(instanceCountryId, previousResponsibleEmployeeId);
        EmployeeOperationalSnapshot newResponsible = requireNewResponsible(instanceCountryId, newResponsibleEmployeeId);
        Long newPersonaId = newResponsible == null ? null : newResponsible.personaId;

        List<UserOperationalScope> existing = UserOperationalScope.list(
            "instanceCountryId=?1 and scopeType=?2 and scopeId=?3",
            instanceCountryId, normalizedScopeType, scopeId
        );

        UserOperationalScope keeper = null;
        for (UserOperationalScope row : existing) {
            boolean belongsToNewResponsible = newPersonaId != null && Objects.equals(row.personaId, newPersonaId);
            boolean managedRow = username.equals(row.username)
                || previousPersonaId != null && Objects.equals(row.personaId, previousPersonaId)
                || belongsToNewResponsible;
            if (!managedRow) continue;

            if (belongsToNewResponsible && username.equals(row.username) && keeper == null) {
                keeper = row;
            } else {
                row.delete();
            }
        }

        if (newResponsible == null || keeper != null) return;

        // Materializa primero los DELETE pendientes para que un alias/persona anterior
        // no choque con el INSERT de reemplazo dentro de la misma transacción.
        entityManager.flush();

        // ON CONFLICT complementa los índices únicos de Flyway para reintentos o guardados concurrentes.
        entityManager.createNativeQuery("""
            INSERT INTO user_operational_scope
                (id, instance_country_id, username, scope_type, scope_id, persona_id, created_at, updated_at)
            VALUES
                (:id, :instanceCountryId, :username, :scopeType, :scopeId, :personaId, now(), now())
            ON CONFLICT DO NOTHING
            """)
            .setParameter("id", UUID.randomUUID())
            .setParameter("instanceCountryId", instanceCountryId)
            .setParameter("username", username)
            .setParameter("scopeType", normalizedScopeType)
            .setParameter("scopeId", scopeId)
            .setParameter("personaId", newPersonaId)
            .executeUpdate();
    }

    static String usernameFor(String scopeType) {
        return switch (normalizeScopeType(scopeType)) {
            case "ZONE" -> "dzonal";
            case "REGION" -> "jregional";
            case "COMPANY" -> "coord";
            default -> throw new IllegalStateException("Tipo de alcance no soportado.");
        };
    }

    private static String normalizeScopeType(String scopeType) {
        String normalized = scopeType == null ? "" : scopeType.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_SCOPE_TYPES.contains(normalized)) {
            throw new BadRequestException("Solo se administran alcances ZONE, REGION y COMPANY mediante responsables.");
        }
        return normalized;
    }

    private EmployeeOperationalSnapshot requireNewResponsible(UUID instanceCountryId, UUID employeeId) {
        if (employeeId == null) return null;
        EmployeeOperationalSnapshot employee = employee(instanceCountryId, employeeId);
        if (employee == null || !"ACTIVE".equalsIgnoreCase(employee.employmentStatus)) {
            throw new BadRequestException("El responsable seleccionado no existe o no está activo.");
        }
        if (employee.personaId == null || employee.personaId <= 0) {
            throw new BadRequestException("El responsable seleccionado no tiene persona_id sincronizado desde SIC:DHO/RRHH.");
        }
        return employee;
    }

    private Long personaId(UUID instanceCountryId, UUID employeeId) {
        EmployeeOperationalSnapshot employee = employee(instanceCountryId, employeeId);
        return employee == null ? null : employee.personaId;
    }

    private EmployeeOperationalSnapshot employee(UUID instanceCountryId, UUID employeeId) {
        if (employeeId == null) return null;
        return EmployeeOperationalSnapshot.find(
            "instanceCountryId=?1 and employeeId=?2", instanceCountryId, employeeId
        ).firstResult();
    }
}
