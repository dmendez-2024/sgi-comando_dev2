package com.cajamarca.sgi.comando.operator;

import com.fasterxml.jackson.databind.node.ArrayNode;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/** Lectura para la bandeja operativa de SGI Comando. */
@Path("/api/operational-reports") @Authenticated
@RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD"})
@Produces(MediaType.APPLICATION_JSON)
public class OperationalReportResource {
    @Inject OperatorReportResource reports;
    @GET public ArrayNode list(@QueryParam("type") String type) { return reports.rows(null,OperatorReportResource.normalizeType(type)); }
}
