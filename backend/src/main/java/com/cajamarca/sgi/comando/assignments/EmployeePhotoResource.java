package com.cajamarca.sgi.comando.assignments;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.storage.StorageService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import java.util.Set;
import java.util.UUID;

/** Entrega fotos privadas de empleados sin exponer MinIO al navegador. */
@Path("/api/assignments/employees")
@RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
public class EmployeePhotoResource {
    private static final Set<String> OPEN_TRANSFER_STATUSES = Set.of(
        "PENDING_ACCEPTANCE", "ACCEPTED_PENDING_EFFECTIVE"
    );

    @Inject TenantContext tenant;
    @Inject AssignmentScopeService scope;
    @Inject StorageService storage;

    @GET
    @Path("/{employeeId}/photo")
    @Produces({"image/jpeg", "image/png", "image/webp"})
    public Response photo(@PathParam("employeeId") UUID employeeId) {
        EmployeeOperationalSnapshot employee = EmployeeOperationalSnapshot.find(
            "instanceCountryId=?1 and employeeId=?2", tenant.instanceCountryId(), employeeId
        ).firstResult();
        if (employee == null) throw new NotFoundException("Empleado no encontrado.");
        requireVisible(employee);
        if (blank(employee.photoBucket) || blank(employee.photoKey) || blank(employee.photoContentType)) {
            throw new NotFoundException("El empleado no tiene una fotografía sincronizada desde DHO.");
        }

        Response.ResponseBuilder response = Response.ok(
            storage.read(employee.photoBucket, employee.photoKey), employee.photoContentType
        ).header(HttpHeaders.CACHE_CONTROL, "private, max-age=300");
        if (!blank(employee.photoSha256)) response.header(HttpHeaders.ETAG, '"' + employee.photoSha256 + '"');
        return response.build();
    }

    private void requireVisible(EmployeeOperationalSnapshot employee) {
        Set<UUID> allowedCompanies = scope.allowedCompanyIds();
        if (allowedCompanies.contains(employee.companyId)) return;
        if (allowedCompanies.isEmpty()) {
            throw new jakarta.ws.rs.ForbiddenException("Colaborador fuera del alcance del usuario.");
        }
        long incoming = EmployeeCompanyTransfer.count(
            "instanceCountryId=?1 and employeeId=?2 and destinationCompanyId in ?3 and status in ?4",
            tenant.instanceCountryId(), employee.employeeId, allowedCompanies, OPEN_TRANSFER_STATUSES
        );
        if (incoming == 0) throw new jakarta.ws.rs.ForbiddenException("Colaborador fuera del alcance del usuario.");
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
