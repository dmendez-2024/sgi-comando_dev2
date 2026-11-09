package com.cajamarca.sgi.comando.socket;

import com.cajamarca.sgi.comando.obsint.*;
import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

/** Callback del gateway: validar origen real y canal antes de aplicar el recibo. */
@Path("/api/internal/socket/acks")
@PermitAll
@Consumes(MediaType.APPLICATION_JSON)
public class SocketAckResource {
    @Inject SocketConfig config;
    @Inject ObsintMessageReceipts receipts;
    @Context HttpServerRequest request;
    public record Ack(String messageId,String channel,String status,String connectionId,String at) {}
    @POST @Transactional
    public Response ack(Ack body,@HeaderParam("X-Socket-Ack-Token") String token) {
        if(!config.enabled()) throw new NotFoundException();
        if(config.ackToken().isPresent() && (token==null || !MessageDigest.isEqual(
            config.ackToken().get().getBytes(StandardCharsets.UTF_8),token.getBytes(StandardCharsets.UTF_8)))) throw new NotAuthorizedException("Socket ACK");
        if(!config.allowsRemote(request.remoteAddress().hostAddress())) throw new ForbiddenException();
        UUID id;
        try { id=UUID.fromString(body.messageId()); } catch(Exception e) { throw new NotFoundException(); }
        ObsintAgentMessageRecipient r=ObsintAgentMessageRecipient.findById(id);
        if(r==null) throw new NotFoundException();
        if(r.personaId==null || !config.channelFor(r.personaId).equals(body.channel())) throw new ClientErrorException(409);
        if(!("DELIVERED".equals(body.status())||"READ".equals(body.status())||"REJECTED".equals(body.status())))
            throw new BadRequestException("Recibo no válido");
        Instant at=null;
        try { if(body.at()!=null) at=Instant.parse(body.at()); } catch(Exception ignored) {}
        receipts.apply(r,body.status(),ObsintMessageReceipts.Via.SOCKET_ACK,at);
        return Response.noContent().build();
    }
}
