package com.cajamarca.sgi.comando.siccom;

import com.cajamarca.sgi.comando.interconnections.InterconnectionIds;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;

@Path("/api/v1/inbound/sic-com/commercial-events")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SicComInboundResource {
  @Inject SicComCommercialCatalogService catalog;
  @Inject SicComInboundServiceTokenFilter serviceToken;

  @POST
  public Response receive(
      SicComCommercialCatalogService.CatalogEventRequest request,
      @HeaderParam("Authorization") String authorization,
      @HeaderParam("X-Correlation-Id") String correlationId,
      @HeaderParam("X-Interconnection-Id") String interconnectionId,
      @HeaderParam("X-Contract-Version") String contractVersion,
      @HeaderParam("Idempotency-Key") String idempotencyKey) {
    serviceToken.assertAuthorized(authorization);
    if (blank(correlationId) || blank(idempotencyKey)) throw badRequest("X-Correlation-Id e Idempotency-Key son obligatorios.");
    if (!InterconnectionIds.SIC_COM_EVENTS.equals(interconnectionId)) throw badRequest("X-Interconnection-Id no corresponde a SIC_COM -> SGI_COM.");
    if (!"v1".equals(contractVersion)) throw badRequest("X-Contract-Version no soportada.");
    if (request == null || !idempotencyKey.equals(request.eventId())) throw badRequest("Idempotency-Key debe coincidir con eventId.");
    try {
      return Response.ok(catalog.apply(request, correlationId)).header("X-Correlation-Id", correlationId).build();
    } catch (SicComCommercialCatalogService.CatalogException e) {
      throw new WebApplicationException(Response.status(e.status()).entity(Map.of("error", e.getMessage(), "correlationId", correlationId)).build());
    }
  }

  private boolean blank(String value) { return value == null || value.isBlank(); }
  private WebApplicationException badRequest(String message) {
    return new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", message)).build());
  }
}
