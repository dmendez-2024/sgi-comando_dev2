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
    /** Modo prueba: el mensaje queda registrado, pero no se envía al agente. */
    public static final String STATUS_TEST = "TEST";

    @Column(name = "task_id", nullable = false) public UUID taskId;
    @Column(nullable = false, length = 160) public String operator;
    @Column(nullable = false, length = TEXT_MAX) public String text;
    @Column(name = "sent_at", nullable = false) public Instant sentAt;
    /**
     * TEST (modo prueba: registrado, no enviado), SENT o FAILED. Hoy siempre TEST: el envío real al agente (SGI Operador)
     * aún no existe. SENT y FAILED quedan para cuando exista.
     */
    @Column(name = "delivery_status", nullable = false, length = 20) public String deliveryStatus;
}
