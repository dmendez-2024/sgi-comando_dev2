package com.cajamarca.sgi.comando.secelec;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.util.UUID;

/** Único punto de contacto de Seguridad Electrónica con el resto de SGI: Comando (tenant, usuario y acceso al Punto). */
@ApplicationScoped
public class SecElecPlatform {
    @Inject TenantContext tenant;
    @Inject OperationalScopeService scope;

    public UUID instanceCountryId() { return tenant.instanceCountryId(); }
    public String username() { return scope.username(); }

    /** El Punto existe en el tenant y el usuario puede gestionar su Compañía (misma regla que Consignas). */
    public void requirePoint(UUID pointId) {
        if (pointId == null) throw new BadRequestException("pointId obligatorio");
        PointEntity point = PointEntity.find("id=?1 and instanceCountryId=?2", pointId, tenant.instanceCountryId()).firstResult();
        if (point == null) throw new NotFoundException("Punto no encontrado");
        scope.requireCompanyForCompanyRoles(point.companyId);
    }
}
