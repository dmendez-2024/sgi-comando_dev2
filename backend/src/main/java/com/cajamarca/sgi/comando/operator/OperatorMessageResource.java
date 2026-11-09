package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.obsint.*;
import com.cajamarca.sgi.comando.socket.SocketConfig;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.*;

/** Sincronización y recibos de los mensajes propios del agente autenticado. */
@Path("/api/v1/operator/messages")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OperatorMessageResource {
    @Inject OperatorContext context;
    @Inject TenantContext tenant;
    @Inject SocketConfig config;
    @Inject EntityManager em;
    @Inject ObsintMessageReceipts receipts;
    public record MessageView(UUID recipientId,UUID messageId,UUID taskId,UUID pointId,UUID postId,
        String from,String text,Instant sentAt,String status,Instant deliveredAt,Instant readAt,String pointName,String postName) {}
    public record Receipt(String status) {}
    @GET @Transactional
    public List<MessageView> messages(@QueryParam("since") String since) {
        UUID employee=context.employee();
        Instant cutoff=Instant.now().minus(config.operatorSyncWindow());
        if(since!=null) {
            try { Instant requested=Instant.parse(since); if(requested.isAfter(cutoff)) cutoff=requested; }
            catch(RuntimeException e) { throw new BadRequestException("since debe ser una fecha ISO-8601"); }
        }
        return queryViews("r.instance_country_id=:tenant and r.employee_id=:employee and "
            +"(r.status in ('PENDING','SENT','DELIVERED','READ') or (r.status='FAILED' and r.last_error='EXPIRED')) "
            +"and r.created_at>=:cutoff order by m.sent_at,r.id",Map.of("tenant",tenant.instanceCountryId(),"employee",employee,"cutoff",cutoff));
    }
    @POST @Path("/{recipientId}/receipt") @Transactional
    public MessageView receipt(@PathParam("recipientId") UUID id,Receipt body) {
        UUID employee=context.employee();
        if(body==null || !("DELIVERED".equals(body.status()) || "READ".equals(body.status())))
            throw new BadRequestException("status debe ser DELIVERED o READ");
        ObsintAgentMessageRecipient r=ObsintAgentMessageRecipient.<ObsintAgentMessageRecipient>find(
            "id=?1 and instanceCountryId=?2 and employeeId=?3",id,tenant.instanceCountryId(),employee)
            .firstResultOptional().orElseThrow(NotFoundException::new);
        receipts.apply(r,body.status(),ObsintMessageReceipts.Via.OPERATOR_API,Instant.now());
        return view(r);
    }
    private MessageView view(ObsintAgentMessageRecipient r) {
        return queryViews("r.id=:id and r.instance_country_id=:tenant",Map.of("id",r.id,"tenant",r.instanceCountryId)).getFirst();
    }
    /** Proyección escalar única: no carga entidades ni dispara consultas por cada mensaje. */
    private List<MessageView> queryViews(String filter,Map<String,Object> parameters) {
        var query=em.createNativeQuery("""
            select r.id,m.id,m.task_id,c.point_id,r.post_id,m.text,m.sent_at,r.status,r.delivered_at,r.read_at,pt.name,p.name
            from obsint_agent_message_recipient r
            join obsint_agent_message m on m.id=r.message_id and m.instance_country_id=r.instance_country_id
            join obsint_case_task t on t.id=m.task_id and t.instance_country_id=m.instance_country_id
            join obsint_consigna c on c.id=t.consigna_id and c.instance_country_id=t.instance_country_id
            left join post p on p.id=r.post_id and p.instance_country_id=r.instance_country_id
            left join point pt on pt.id=p.point_id and pt.instance_country_id=p.instance_country_id
            where
            """+filter);
        parameters.forEach(query::setParameter);
        List<?> rows=query.getResultList();
        return rows.stream().map(row->{Object[] v=(Object[])row;return new MessageView(
            (UUID)v[0],(UUID)v[1],(UUID)v[2],(UUID)v[3],(UUID)v[4],"Monitor Consola",(String)v[5],
            instant(v[6]),(String)v[7],instant(v[8]),instant(v[9]),(String)v[10],(String)v[11]);}).toList();
    }
    private static Instant instant(Object value) {
        if(value==null) return null;
        if(value instanceof Instant i) return i;
        if(value instanceof java.time.OffsetDateTime d) return d.toInstant();
        return ((java.sql.Timestamp)value).toInstant();
    }
}
