package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.security.IdentIdentity;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.ForbiddenException;
import java.util.List;
import java.util.UUID;

/**
 * Empleado de la persona de SGI Operador. Entra con IDENT y se encuentra por el persona_id de DHO que envia IDENT
 * (V29), no por usuario y contrasena de SGI (app_user ni operator_employee_binding).
 */
@ApplicationScoped
public class OperatorEmployeeLookup {
    @Inject TenantContext tenant;
    @Inject EntityManager em;

    public UUID employeeOf(SecurityIdentity identity) {
        if (!IdentIdentity.isIdent(identity)) throw new ForbiddenException("SGI Operador entra con IDENT");
        Long personaId = IdentIdentity.personaId(identity);
        if (personaId == null) throw new ForbiddenException("IDENT aún no envía el persona_id de DHO de esta persona");
        return employeeOfPersona(personaId);
    }

    /** Empleado por el persona_id de DHO (el que IDENT devuelve de la persona). */
    public UUID employeeOfPersona(long personaId) {
        List<?> rows = em.createNativeQuery("select employee_id from employee_operational_snapshot where instance_country_id=:tenant and persona_id=:persona")
            .setParameter("tenant", tenant.instanceCountryId()).setParameter("persona", personaId).getResultList();
        if (rows.size() != 1) throw new ForbiddenException("Vínculo persona empleado pendiente de configuración");
        return UUID.fromString(rows.get(0).toString());
    }
}
