package com.cajamarca.sgi.comando.operations;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.territory.OperationalScopeService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.*;
import java.util.stream.Collectors;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({"PRESIDENTE","DIRECTOR_OPERACIONES_LATAM","DIRECTOR_OPERACIONES_NACIONAL","DIRECTOR_NACIONAL","DIRECTOR_ZONAL","JEFE_REGIONAL","COORDINADOR_COMPANIA","ASISTENTE_COORDINACION","SUPERVISOR_SEGURIDAD","AGENTE_SEGURIDAD","CLIENTE"})
public class OperationsResource {
  @Inject TenantContext tenant;
  @Inject OperationalScopeService scope;

  public record ClientOptionDto(UUID id, String code, String name) {}

  @GET @Path("/clients")
  public List<ClientOptionDto> clients(){
    return ClientEntity.<ClientEntity>list("instanceCountryId=?1 order by name", tenant.instanceCountryId())
        .stream().map(client -> new ClientOptionDto(client.id, client.code, client.name)).toList();
  }

  @GET @Path("/services")
  public List<ServiceEntity> services(){
    Set<UUID> allowed=scope.allowedCompanyIds(); if(allowed.isEmpty()) return List.of();
    List<PointEntity> points=PointEntity.list("instanceCountryId=?1 and companyId in ?2 and status='ACTIVE'",tenant.instanceCountryId(),allowed);
    Set<UUID> serviceIds=points.stream().map(p->p.serviceId).collect(Collectors.toSet());
    return serviceIds.isEmpty()?List.of():ServiceEntity.list("instanceCountryId=?1 and id in ?2 order by name",tenant.instanceCountryId(),serviceIds);
  }

  @GET @Path("/services/{serviceId}/points")
  public List<PointEntity> points(@PathParam("serviceId") UUID serviceId){
    Set<UUID> allowed=scope.allowedCompanyIds(); if(allowed.isEmpty()) return List.of();
    return PointEntity.list("serviceId=?1 and instanceCountryId=?2 and companyId in ?3 order by name",serviceId,tenant.instanceCountryId(),allowed);
  }

  @GET @Path("/points/{pointId}/posts")
  public List<PostEntity> posts(@PathParam("pointId") UUID pointId){ return postsForPoint(pointId); }

  @GET @Path("/posts")
  public List<PostEntity> postsByQuery(@QueryParam("pointId") UUID pointId){ if(pointId==null) throw new BadRequestException("pointId is required"); return postsForPoint(pointId); }

  private List<PostEntity> postsForPoint(UUID pointId){
    PointEntity point=PointEntity.find("id=?1 and instanceCountryId=?2",pointId,tenant.instanceCountryId()).firstResult();
    if(point==null) throw new NotFoundException(); scope.requireCompany(point.companyId);
    return PostEntity.list("pointId=?1 and instanceCountryId=?2 order by code",pointId,tenant.instanceCountryId());
  }
}
