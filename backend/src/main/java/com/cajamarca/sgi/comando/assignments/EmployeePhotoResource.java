package com.cajamarca.sgi.comando.assignments;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.interconnections.InterconnectionException;
import com.cajamarca.sgi.comando.rrhh.DhoEmployeePhotoAdapter;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.Set;
import java.util.UUID;

/** Entrega fotos privadas consultadas bajo demanda en DHO. No persiste la imagen. */
@Path("/api/assignments")
@RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
public class EmployeePhotoResource {
    private static final Logger LOG = Logger.getLogger(EmployeePhotoResource.class);
    private static final Set<String> OPEN_TRANSFER_STATUSES = Set.of(
        "PENDING_ACCEPTANCE", "ACCEPTED_PENDING_EFFECTIVE"
    );

    @Inject TenantContext tenant;
    @Inject AssignmentScopeService scope;
    @Inject DhoEmployeePhotoAdapter dho;

    @GET
    @Path("/employees/{employeeId}/photo")
    @Produces({"image/jpeg", "image/png", "image/webp"})
    public Response photo(@PathParam("employeeId") UUID employeeId) {
        EmployeeOperationalSnapshot employee = EmployeeOperationalSnapshot.find(
            "instanceCountryId=?1 and employeeId=?2", tenant.instanceCountryId(), employeeId
        ).firstResult();
        if (employee == null) throw new NotFoundException("Empleado no encontrado.");
        requireVisible(employee);
        if (employee.personaId == null || employee.personaId <= 0) {
            throw new NotFoundException("El empleado no tiene una persona asociada en DHO.");
        }

        try {
            DhoEmployeePhotoAdapter.EmployeePhoto photo = dho.fetch(employee.personaId);
            if (photo == null) throw new NotFoundException("DHO no devolvió una fotografía para el empleado.");
            return Response.ok(photo.bytes(), photo.contentType())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .header("X-Content-Type-Options", "nosniff")
                .build();
        } catch (NotFoundException error) {
            throw error;
        } catch (InterconnectionException error) {
            LOG.warnf("dho_employee_photo_failed employeeId=%s code=%s", employeeId, error.code());
            int status = error.code().startsWith("DHO_EMPLOYEE_PHOTO_")
                ? Response.Status.BAD_GATEWAY.getStatusCode()
                : Response.Status.SERVICE_UNAVAILABLE.getStatusCode();
            throw new jakarta.ws.rs.WebApplicationException(Response.status(status)
                .type(MediaType.TEXT_PLAIN_TYPE)
                .entity("No fue posible obtener la fotografía del empleado desde DHO.")
                .build());
        }
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
}
