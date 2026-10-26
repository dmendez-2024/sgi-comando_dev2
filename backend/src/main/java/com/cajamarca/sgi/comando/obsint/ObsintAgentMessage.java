package com.cajamarca.sgi.comando.obsint;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Mensaje del monitorista al agente en sitio (canal unidireccional). */
@Entity
@Table(name = "obsint_agent_message")
public class ObsintAgentMessage extends BaseEntity {
    public static final int TEXT_MAX = 500;

    @Column(name = "task_id", nullable = false) public UUID taskId;
    @Column(nullable = false, length = 160) public String operator;
    @Column(nullable = false, length = TEXT_MAX) public String text;
    @Column(name = "sent_at", nullable = false) public Instant sentAt;
    /** SENT o FAILED. Hoy siempre SENT: el envío real al agente (SGI Operador) aún no está definido. */
    @Column(name = "delivery_status", nullable = false, length = 20) public String deliveryStatus;
}
