package com.cajamarca.sgi.comando.operator;

import com.cajamarca.sgi.comando.common.TenantContext;
import com.cajamarca.sgi.comando.communication.CommunicationRoom;
import com.cajamarca.sgi.comando.communication.CommunicationRooms;
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

/**
 * Comunicación en SGI Operador: mensajes propios del agente autenticado (0033), sus recibos (0034) y las salas de chat
 * donde se agrupan (0037). Solo se exponen mensajes dirigidos a ese agente; del remitente, solo su cargo.
 */
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
    @Inject CommunicationRooms roomCatalog;
    /** Destinatarios que el agente ve: en curso, leídos o vencidos (no los descartados ni los fallidos por otra causa). */
    private static final String VISIBLE="(r.status in ('PENDING','SENT','DELIVERED','READ') or (r.status='FAILED' and r.last_error='EXPIRED'))";
    public record MessageView(UUID recipientId,UUID messageId,UUID taskId,UUID pointId,UUID postId,
        String from,String text,Instant sentAt,String status,Instant deliveredAt,Instant readAt,String pointName,String postName,UUID roomId) {}
    public record Receipt(String status) {}
    /**
     * Sala de chat del agente: último mensaje que le llegó en la ventana de sincronización (o null si no hay) y cuántos
     * aún no ha leído. Del remitente, solo su cargo (`lastMessageFrom`).
     */
    public record RoomView(UUID roomId,String code,String name,boolean defaultRoom,Instant lastMessageAt,
        String lastMessageFrom,String lastMessageText,int unreadCount) {}
    @GET @Transactional
    public List<MessageView> messages(@QueryParam("since") String since) {
        UUID employee=context.employee();
        Instant cutoff=Instant.now().minus(config.operatorSyncWindow());
        if(since!=null) {
            try { Instant requested=Instant.parse(since); if(requested.isAfter(cutoff)) cutoff=requested; }
            catch(RuntimeException e) { throw new BadRequestException("since debe ser una fecha ISO-8601"); }
        }
        return queryViews("r.instance_country_id=:tenant and r.employee_id=:employee and "+VISIBLE+" "
            +"and r.created_at>=:cutoff order by m.sent_at,r.id",Map.of("tenant",tenant.instanceCountryId(),"employee",employee,"cutoff",cutoff));
    }
    /**
     * SGI_OPR_SGI_COM_0037: GET /api/v1/operator/messages/rooms. Salas activas de la instancia (la predeterminada,
     * "Monitor Consola", primero; luego por último mensaje), aunque el agente aún no tenga mensajes en ellas.
     * Por ahora las salas de la Consola OBSINT ("Monitor Consola") solo las ve el Agente de Seguridad: el Supervisor no
     * las recibe en el listado.
     */
    @GET @Path("/rooms") @Transactional
    public List<RoomView> rooms() {
        UUID employee=context.employee();
        UUID tenantId=tenant.instanceCountryId();
        boolean agent=context.isAgent();
        if(agent) roomCatalog.monitorConsole(tenantId);
        Instant cutoff=Instant.now().minus(config.operatorSyncWindow());
        List<?> rows=em.createNativeQuery("""
            select cr.id,cr.code,cr.name,cr.is_default,lm.sent_at,lm.sender_role,lm.text,coalesce(ur.total,0)
            from communication_room cr
            left join lateral (
                select m.sent_at,m.sender_role,m.text
                from obsint_agent_message_recipient r
                join obsint_agent_message m on m.id=r.message_id and m.instance_country_id=r.instance_country_id
                where r.instance_country_id=cr.instance_country_id and r.employee_id=:employee and m.room_id=cr.id
                  and r.created_at>=:cutoff and """+VISIBLE+"""
                order by m.sent_at desc,r.id desc limit 1) lm on true
            left join lateral (
                select count(*) total
                from obsint_agent_message_recipient r
                join obsint_agent_message m on m.id=r.message_id and m.instance_country_id=r.instance_country_id
                where r.instance_country_id=cr.instance_country_id and r.employee_id=:employee and m.room_id=cr.id
                  and r.created_at>=:cutoff and r.status in ('PENDING','SENT','DELIVERED')) ur on true
            where cr.instance_country_id=:tenant and cr.active and (:agent or cr.source<>:consoleSource)
            order by cr.is_default desc,lm.sent_at desc nulls last,cr.name
            """).setParameter("tenant",tenantId).setParameter("employee",employee).setParameter("cutoff",cutoff)
            .setParameter("agent",agent).setParameter("consoleSource",CommunicationRoom.SOURCE_OBSINT_CONSOLE).getResultList();
        return rows.stream().map(row->{Object[] v=(Object[])row;
            UUID id=v[0] instanceof UUID u?u:UUID.fromString(v[0].toString());
            Instant last=v[4]==null?null:instant(v[4]);
            return new RoomView(id,(String)v[1],(String)v[2],Boolean.TRUE.equals(v[3]),last,
                last==null?null:ObsintAgentMessage.fromLabel((String)v[5]),(String)v[6],((Number)v[7]).intValue());}).toList();
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
    /**
     * Proyección escalar única: no carga entidades ni dispara consultas por cada mensaje. Del remitente solo sale su cargo
     * (`from`); el usuario del monitorista (`m.operator`) queda en Comando para auditoría.
     */
    private List<MessageView> queryViews(String filter,Map<String,Object> parameters) {
        var query=em.createNativeQuery("""
            select r.id,m.id,m.task_id,c.point_id,r.post_id,m.text,m.sent_at,r.status,r.delivered_at,r.read_at,pt.name,p.name,m.sender_role,m.room_id
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
            (UUID)v[0],(UUID)v[1],(UUID)v[2],(UUID)v[3],(UUID)v[4],ObsintAgentMessage.fromLabel((String)v[12]),(String)v[5],
            instant(v[6]),(String)v[7],instant(v[8]),instant(v[9]),(String)v[10],(String)v[11],(UUID)v[13]);}).toList();
    }
    private static Instant instant(Object value) {
        if(value==null) return null;
        if(value instanceof Instant i) return i;
        if(value instanceof java.time.OffsetDateTime d) return d.toInstant();
        return ((java.sql.Timestamp)value).toInstant();
    }
}
