package com.cajamarca.sgi.comando.communication;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Sala de chat de Comunicación en SGI Operador (solo lectura para el agente). */
@Entity
@Table(name = "communication_room")
public class CommunicationRoom extends BaseEntity {
    /** Origen de los mensajes de la sala; por ahora solo la Consola OBSINT. */
    public static final String SOURCE_OBSINT_CONSOLE = "OBSINT_CONSOLE";

    @Column(nullable = false, length = 60) public String code;
    @Column(nullable = false, length = 120) public String name;
    @Column(nullable = false, length = 30) public String source;
    @Column(name = "is_default", nullable = false) public boolean defaultRoom;
    @Column(nullable = false) public boolean active = true;
}
