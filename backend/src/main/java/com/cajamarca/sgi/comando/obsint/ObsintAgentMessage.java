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
    public static final String STATUS_PENDING="PENDING", STATUS_SENT="SENT", STATUS_DELIVERED="DELIVERED", STATUS_READ="READ", STATUS_FAILED="FAILED";
    @Column(name="failure_reason", length=60) public String failureReason;
    @Column(nullable=false,length=10) public String target="POINT";

    @Column(name = "task_id", nullable = false) public UUID taskId;
    @Column(nullable = false, length = 160) public String operator;
    /** Cargo del monitorista al enviar. El agente ve como remitente (`from`) el nombre de la sala; el cargo, solo si el mensaje no tiene sala. */
    @Column(name = "sender_role", length = 120) public String senderRole;
    /** Sala de Comunicación del mensaje; los de la Consola OBSINT van a "Monitor Consola". */
    @Column(name = "room_id") public UUID roomId;
    @Column(nullable = false, length = TEXT_MAX) public String text;
    @Column(name = "sent_at", nullable = false) public Instant sentAt;
    /**
     * TEST con socket apagado; PENDING, SENT, DELIVERED, READ o FAILED durante el envío real.
     */
    @Column(name = "delivery_status", nullable = false, length = 20) public String deliveryStatus;

    /** Lo que ve el agente cuando no se conoce el cargo del remitente. */
    public static final String DEFAULT_FROM = "Monitor Consola";

    /** Título del mensaje en SGI Operador: el cargo del monitorista, nunca su nombre ni su usuario. */
    public static String fromLabel(String senderRole) {
        return senderRole == null || senderRole.isBlank() ? DEFAULT_FROM : senderRole.trim();
    }

    /**
     * Remitente que ve el agente: si el mensaje está en una sala de Comunicación, el nombre de la sala (p. ej. "Monitor
     * Consola"); si no, el cargo del monitorista. El cargo queda guardado en Comando (`sender_role`) para auditoría.
     */
    public static String fromLabel(String roomName, String senderRole) {
        return roomName == null || roomName.isBlank() ? fromLabel(senderRole) : roomName.trim();
    }
}
