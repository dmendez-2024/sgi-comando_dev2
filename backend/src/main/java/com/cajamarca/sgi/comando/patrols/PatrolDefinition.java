package com.cajamarca.sgi.comando.patrols;

import com.cajamarca.sgi.comando.common.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Definición canónica de una Patrulla.
 *
 * La tabla existe desde el baseline y también es referenciada por las tablas de
 * ejecución (patrol_plan / patrol_execution). SER la evoluciona; no crea una
 * segunda tabla de configuración paralela.
 */
@Entity
@Table(name="patrol_definition")
public class PatrolDefinition extends BaseEntity {
    // Campos baseline que se conservan por compatibilidad operacional.
    @Column(name="point_id", nullable=false) public UUID pointId;
    @Column(name="status", nullable=false, length=32) public String status;
    @Column(name="version", nullable=false) public int legacyVersion;

    // Campos SER de configuración/versionado.
    @Column(name="protocol_id", nullable=false) public UUID protocolId;
    @Column(name="code", nullable=false, length=32) public String code;
    @Column(name="name", nullable=false, length=180) public String name;
    @Column(name="description", nullable=false, length=1000) public String description;
    @Column(name="structure_type", nullable=false, length=16) public String structureType;
    @Column(name="schedule_type", nullable=false, length=20) public String scheduleType;
    @Column(name="sequence_type", length=16) public String sequenceType;
    @Column(name="window_start") public LocalTime windowStart;
    @Column(name="window_end") public LocalTime windowEnd;
    @Column(name="repetitions", nullable=false) public int repetitions;
    @Column(name="version_no", nullable=false) public int versionNo;
    @Column(name="updated_by_username", nullable=false, length=80) public String updatedByUsername;
}
