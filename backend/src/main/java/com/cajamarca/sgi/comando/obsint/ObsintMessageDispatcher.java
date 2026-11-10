package com.cajamarca.sgi.comando.obsint;

import com.cajamarca.sgi.comando.socket.*;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.*;
import jakarta.inject.Inject;
import jakarta.persistence.*;
import org.jboss.logging.Logger;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Despacho con bloqueo por destinatario y agregado serializado por mensaje. */
@ApplicationScoped
public class ObsintMessageDispatcher {
    private static final Logger LOG=Logger.getLogger(ObsintMessageDispatcher.class);
    @Inject SocketConfig config;
    @Inject SocketPublisher publisher;
    @Inject EntityManager em;
    private final AtomicBoolean running=new AtomicBoolean();
    void queued(@Observes(during=TransactionPhase.AFTER_SUCCESS) ObsintMessageQueued event) {
        Infrastructure.getDefaultWorkerPool().execute(this::tick);
    }
    @Scheduled(every="${sgi.socket.dispatch-every:5s}", delayed="20s", concurrentExecution=Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        if(!config.enabled() || !running.compareAndSet(false,true)) return;
        try {
            // El mismo bloqueo protege vencimientos y publicaciones en varias réplicas.
            QuarkusTransaction.requiringNew().run(this::expire);
            for(int i=0;i<config.dispatchBatchSize();i++) {
                if(!QuarkusTransaction.requiringNew().timeout((int)config.httpTimeout().toSeconds()+10).call(this::deliverOne)) break;
            }
        } catch(RuntimeException e) { LOG.warn("Despacho OBSINT pendiente; se reintentará en el siguiente ciclo"); }
        finally { running.set(false); }
    }
    void expire() {
        List<?> ids=em.createNativeQuery("""
            select id from obsint_agent_message_recipient
            where status='PENDING' and created_at<:cutoff order by next_attempt_at
            limit :batch for update skip locked
            """).setParameter("cutoff",Instant.now().minus(config.messageTtl())).setParameter("batch",config.dispatchBatchSize()).getResultList();
        for(Object id:ids) {
            ObsintAgentMessageRecipient r=ObsintAgentMessageRecipient.findById(UUID.fromString(id.toString()));
            ObsintAgentMessage m=em.find(ObsintAgentMessage.class,r.messageId,LockModeType.PESSIMISTIC_WRITE);
            r.status=ObsintAgentMessageRecipient.FAILED; r.lastError="EXPIRED";
            ObsintAgentMessageRecipient.refresh(m);
        }
    }
    boolean deliverOne() {
        List<?> ids=em.createNativeQuery("""
            select id from obsint_agent_message_recipient
            where status='PENDING' and next_attempt_at<=now()
            order by next_attempt_at limit 1 for update skip locked
            """).getResultList();
        if(ids.isEmpty()) return false;
        ObsintAgentMessageRecipient r=ObsintAgentMessageRecipient.findById(UUID.fromString(ids.getFirst().toString()));
        ObsintAgentMessage m=em.find(ObsintAgentMessage.class,r.messageId,LockModeType.PESSIMISTIC_WRITE);
        Instant now=Instant.now();
        if(r.createdAt.isBefore(now.minus(config.messageTtl()))) { r.status="FAILED"; r.lastError="EXPIRED"; }
        else if(r.personaId==null) { r.status="FAILED"; r.lastError="NO_PERSONA_LINK"; }
        else {
            ObsintCaseTask task=ObsintCaseTask.findById(m.taskId);
            com.cajamarca.sgi.comando.communication.CommunicationRoom room=m.roomId==null?null:com.cajamarca.sgi.comando.communication.CommunicationRoom.findById(m.roomId);
            Map<String,Object> payload=new LinkedHashMap<>(Map.of("messageId",m.id,"recipientId",r.id,"taskId",m.taskId,
                "pointId",task.consigna.pointId,"postId",r.postId,"from",ObsintAgentMessage.fromLabel(room==null?null:room.name,m.senderRole),"text",m.text,
                "sentAt",m.sentAt.toString(),"replyAllowed",false));
            List<?> locations=em.createNativeQuery("""
                select pt.name,p.name from post p
                left join point pt on pt.id=p.point_id and pt.instance_country_id=p.instance_country_id
                where p.id=:post and p.instance_country_id=:tenant
                """).setParameter("post",r.postId).setParameter("tenant",r.instanceCountryId).getResultList();
            Object[] names=locations.isEmpty()?new Object[]{null,null}:(Object[])locations.getFirst();
            payload.put("pointName",names[0]);payload.put("postName",names[1]);payload.put("roomId",m.roomId);
            var result=publisher.publish(r.id,config.channelFor(r.personaId),payload);
            switch(result.outcome()) {
                case ACCEPTED -> { r.status="SENT"; r.sentAt=Instant.now(); r.localRecipients=result.localRecipients(); r.lastError=null; }
                case REJECTED -> { r.status="FAILED"; r.lastError=result.reason(); }
                case UNAVAILABLE -> { r.attempts++; r.nextAttemptAt=Instant.now().plus(backoff(r.attempts)); r.lastError=result.reason(); }
            }
        }
        ObsintAgentMessageRecipient.refresh(m);
        return true;
    }
    public static Duration backoff(int attempts) { return Duration.ofSeconds(Math.min(300,5L*(1L<<Math.min(6,Math.max(0,attempts-1))))); }
}
