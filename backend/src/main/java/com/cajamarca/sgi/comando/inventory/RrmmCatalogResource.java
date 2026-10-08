package com.cajamarca.sgi.comando.inventory;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.companies.Company;
import com.cajamarca.sgi.comando.interconnections.InterconnectionIds;
import com.cajamarca.sgi.comando.operations.PointEntity;
import com.cajamarca.sgi.comando.operations.PostEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Catálogo operacional que SGI Comando entrega a SIC:RRMM. */
@Path("/api/v1/integration/rrmm")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class RrmmCatalogResource {
    private static final String LEGACY="SIC_RRMM__SGI_COM__00001__V0001";
    @Inject RrmmInboundServiceToken serviceToken;
    @Inject TenantContext tenant;
    @Inject ObjectMapper mapper;

    @GET @Path("/companies/{companyId}/points")
    public Response points(@PathParam("companyId") UUID companyId,@HeaderParam("Authorization") String authorization,
                           @HeaderParam("X-Correlation-Id") String correlationId,@HeaderParam("X-Interconnection-Id") String interconnectionId,
                           @HeaderParam("X-Contract-Version") String contractVersion) {
        headers(authorization,correlationId,interconnectionId,contractVersion);
        Company company=Company.find("id=?1 and instanceCountryId=?2",companyId,tenant.instanceCountryId()).firstResult();
        if(company==null) throw new NotFoundException("Compañía no encontrada.");
        ArrayNode items=mapper.createArrayNode();
        for(PointEntity point:PointEntity.<PointEntity>list("instanceCountryId=?1 and companyId=?2 and status='ACTIVE' and operationalAssignmentStatus='ASSIGNED' order by code",tenant.instanceCountryId(),companyId))
            items.addObject().put("pointId",point.id.toString()).put("code",point.code).put("name",point.name)
                .put("clientName",point.clientName).put("city",point.city).put("operationalStatus",point.operationalAssignmentStatus)
                .put("snapshotVersion",version(point.updatedAt));
        ObjectNode body=mapper.createObjectNode().put("companyId",companyId.toString()).put("companyCode",company.code)
            .put("generatedAt",Instant.now().toString()); body.set("items",items);
        return Response.ok(body).header("X-Correlation-Id",correlationId.trim()).build();
    }

    @GET @Path("/points/{pointId}/posts")
    public Response posts(@PathParam("pointId") UUID pointId,@HeaderParam("Authorization") String authorization,
                          @HeaderParam("X-Correlation-Id") String correlationId,@HeaderParam("X-Interconnection-Id") String interconnectionId,
                          @HeaderParam("X-Contract-Version") String contractVersion) {
        headers(authorization,correlationId,interconnectionId,contractVersion);
        PointEntity point=PointEntity.find("id=?1 and instanceCountryId=?2 and status='ACTIVE' and operationalAssignmentStatus='ASSIGNED'",pointId,tenant.instanceCountryId()).firstResult();
        if(point==null) throw new NotFoundException("Punto operativo no encontrado.");
        ArrayNode items=mapper.createArrayNode();
        for(PostEntity post:PostEntity.<PostEntity>list("instanceCountryId=?1 and pointId=?2 order by code",tenant.instanceCountryId(),pointId)) {
            ObjectNode item=items.addObject().put("postId",post.id.toString()).put("code",post.code).put("name",post.name)
                .put("format",post.format).put("tier",post.tier).put("configStatus",post.configStatus).put("snapshotVersion",version(post.updatedAt));
            if(post.code2!=null)item.put("code2",post.code2);
        }
        ObjectNode body=mapper.createObjectNode().put("pointId",pointId.toString()).put("generatedAt",Instant.now().toString()); body.set("items",items);
        return Response.ok(body).header("X-Correlation-Id",correlationId.trim()).build();
    }

    private void headers(String authorization,String correlationId,String interconnectionId,String contractVersion) {
        serviceToken.assertAuthorized(authorization);
        if(correlationId==null||correlationId.isBlank()) throw bad("X-Correlation-Id es obligatorio.");
        if(!InterconnectionIds.matches(interconnectionId,InterconnectionIds.RRMM_POINT_POST_CATALOG,LEGACY)) throw bad("X-Interconnection-Id no corresponde a SIC:RRMM → SGI:Comando.");
        if(!"v1".equalsIgnoreCase(contractVersion==null?"":contractVersion.trim())) throw bad("X-Contract-Version debe ser v1.");
    }
    private static String version(Instant updatedAt){return updatedAt==null?"0":updatedAt.toString();}
    private static WebApplicationException bad(String message){return new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error",message)).build());}
}
