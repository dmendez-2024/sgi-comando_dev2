package com.cajamarca.sgi.comando.obsint;

import com.cajamarca.sgi.comando.common.BaseEntity;
import com.cajamarca.sgi.comando.secelec.SecElecComponent;
import jakarta.persistence.*;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Consigna OBSINT de un protocolo del Punto: qué verificar, en cuánto tiempo y sobre qué cámaras (componentes VIDEO).
 * Es el origen de cada tarea del caso; la tarea copia instrucción y tiempo para que cambiar la consigna no altere casos ya creados.
 */
@Entity
@Table(name = "obsint_consigna")
public class ObsintConsigna extends BaseEntity {
    @Column(name = "protocol_id", nullable = false) public UUID protocolId;
    @Column(name = "point_id", nullable = false) public UUID pointId;
    @Column(nullable = false, length = 40) public String code;
    /** Código de la consigna de Consignas (marcada OBSINT) de la que sale esta tarea; null en tareas anteriores al vínculo. */
    @Column(name = "consignment_code", length = 32) public String consignmentCode;
    @Column(nullable = false, length = 160) public String title;
    @Column(nullable = false, length = 1000) public String description = "";
    @Column(nullable = false, length = 1000) public String instruction;
    @Column(nullable = false, length = 24) public String category;
    @Column(nullable = false, length = 24) public String frequency = "EVERY_SHIFT";
    @Column(name = "time_limit_seconds", nullable = false) public int timeLimitSeconds;
    @Column(nullable = false) public int position;
    @Column(nullable = false) public boolean active = true;

    @ManyToMany
    @JoinTable(name = "obsint_consigna_camera", joinColumns = @JoinColumn(name = "consigna_id"), inverseJoinColumns = @JoinColumn(name = "component_id"))
    @OrderColumn(name = "position")
    public List<SecElecComponent> cameras = new ArrayList<>();

    /** Horario de ejecución: un tramo por día (1 = lunes … 7 = domingo). */
    @ElementCollection
    @CollectionTable(name = "obsint_consigna_schedule", joinColumns = @JoinColumn(name = "consigna_id"))
    public List<Slot> schedule = new ArrayList<>();

    @Embeddable
    public static class Slot {
        @Column(name = "day_of_week", nullable = false) public int dayOfWeek;
        @Column(name = "start_time", nullable = false) public LocalTime startTime;
        @Column(name = "end_time", nullable = false) public LocalTime endTime;

        public Slot() {}
        public Slot(int dayOfWeek, LocalTime startTime, LocalTime endTime) { this.dayOfWeek = dayOfWeek; this.startTime = startTime; this.endTime = endTime; }
    }

    /** true si el horario cubre ese día y hora (inicio incluido, fin excluido). */
    public boolean scheduledAt(int dayOfWeek, LocalTime time) {
        return schedule.stream().anyMatch(s -> s.dayOfWeek == dayOfWeek && !time.isBefore(s.startTime) && time.isBefore(s.endTime));
    }
}
