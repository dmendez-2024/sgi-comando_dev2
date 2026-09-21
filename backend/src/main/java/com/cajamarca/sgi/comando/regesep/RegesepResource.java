package com.cajamarca.sgi.comando.regesep;
import com.cajamarca.sgi.comando.common.TenantContext;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;
@Path("/api/points/{pointId}/regesep")
@Produces(MediaType.APPLICATION_JSON)
public class RegesepResource {
 @Inject TenantContext tenant;
 @GET @Path("/current")
 @RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
 public RegesepVersion current(@PathParam("pointId") UUID pointId){
   RegesepVersion r=RegesepVersion.find("pointId=?1 and instanceCountryId=?2 and status='CURRENT' order by version desc",pointId,tenant.instanceCountryId()).firstResult();
   if(r==null) throw new NotFoundException();
   return r;
 }
}
