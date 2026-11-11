package com.cajamarca.sgi.comando.operator;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import jakarta.inject.Inject;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

/** Lectura para la bandeja operativa de SGI Comando. */
@Path("/api/operational-reports")
@RolesAllowed({"PRESIDENTE","GERENCIA_GENERAL","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","MONITOR"})
@Produces(MediaType.APPLICATION_JSON)
public class OperationalReportResource {
    @Inject OperatorReportResource reports;
    @Inject OperationalScopeService scope;
    @GET public ArrayNode list(@QueryParam("type") String type) { return reports.rows(null,OperatorReportResource.normalizeType(type),scope.allowedCompanyIdsForCompanyRoles()); }
    @GET @Path("/{id}") public JsonNode detail(@PathParam("id") UUID id) {
        ArrayNode found=reports.rows(null,null,scope.allowedCompanyIdsForCompanyRoles(),id);
        if(found.isEmpty()) throw new NotFoundException("Reporte no encontrado en el alcance del usuario.");
        return found.get(0);
    }
}
