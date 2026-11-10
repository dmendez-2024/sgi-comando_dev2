package com.cajamarca.sgi.comando.obsint;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

/** Destinatario y cola persistente de entrega de un mensaje. */
@Entity
@Table(name="obsint_agent_message_recipient")
public class ObsintAgentMessageRecipient extends BaseEntity {
    public static final String PENDING="PENDING", SENT="SENT", DELIVERED="DELIVERED", READ="READ", FAILED="FAILED";
    @Column(name="message_id", nullable=false) public UUID messageId;
    @Column(name="employee_id", nullable=false) public UUID employeeId;
    @Column(name="assignment_id", nullable=false) public UUID assignmentId;
    @Column(name="post_id", nullable=false) public UUID postId;
    @Column(name="persona_id") public Long personaId;
    @Column(name="local_recipients") public Integer localRecipients;
    @Column(name="delivered_via",length=20) public String deliveredVia;
    @Column(name="read_via",length=20) public String readVia;
    @Column(name="app_rejected_at") public Instant appRejectedAt;
    @Column(name="full_name",length=200) public String fullName;
    @Column(name="post_name",length=200) public String postName;
    @Column(nullable=false, length=20) public String status;
    @Column(nullable=false) public int attempts;
    @Column(name="next_attempt_at", nullable=false) public Instant nextAttemptAt;
    @Column(name="last_error", length=200) public String lastError;
    @Column(name="sent_at") public Instant sentAt;
    @Column(name="delivered_at") public Instant deliveredAt;
    @Column(name="read_at") public Instant readAt;
    /** Push FCM: cuándo lo aceptó FCM, o por qué no salió (auditoría; no cambia el estado del destinatario). */
    @Column(name="push_sent_at") public Instant pushSentAt;
    @Column(name="push_error", length=120) public String pushError;
    public static int progress(String status) {
        return switch(status) { case PENDING -> 0; case SENT -> 1; case DELIVERED -> 2; case READ -> 3; default -> -1; };
    }
    public static String aggregate(List<String> statuses) {
        return statuses.stream().filter(s -> progress(s)>=0).min(Comparator.comparingInt(ObsintAgentMessageRecipient::progress)).orElse(FAILED);
    }
    /** El llamador debe bloquear primero el mensaje padre para serializar el agregado entre destinatarios. */
    public static void refresh(ObsintAgentMessage message) {
        List<ObsintAgentMessageRecipient> recipients=list("messageId=?1 and instanceCountryId=?2", message.id, message.instanceCountryId);
        message.deliveryStatus=aggregate(recipients.stream().map(r -> r.status).toList());
        message.failureReason=FAILED.equals(message.deliveryStatus) ? recipients.stream().map(r -> r.lastError)
            .filter(Objects::nonNull).findFirst().map(e -> e.substring(0,Math.min(60,e.length()))).orElse("DELIVERY_FAILED") : null;
    }
}
