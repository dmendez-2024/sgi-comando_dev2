package com.cajamarca.sgi.comando.execution;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Tarea ejecutada por el agente (fase 1: Hito de patrulla). El id es el eventId enviado por el móvil. */
@Entity
@Table(name="task_execution")
public class TaskExecution extends PanacheEntityBase {
    @Id public UUID id;
    @Column(name="instance_country_id", nullable=false) public UUID instanceCountryId;
    @Column(name="execution_type", nullable=false, length=40) public String executionType;
    @Column(name="assignment_id", nullable=false) public UUID assignmentId;
    @Column(name="shift_occurrence_id", nullable=false) public UUID shiftOccurrenceId;
    @Column(name="point_id", nullable=false) public UUID pointId;
    @Column(name="post_id", nullable=false) public UUID postId;
    @Column(name="employee_id", nullable=false) public UUID employeeId;
    @Column(name="username", nullable=false, length=80) public String username;
    @Column(name="target_type", nullable=false, length=40) public String targetType;
    @Column(name="target_id", nullable=false) public UUID targetId;
    @Column(name="protocol_id", nullable=false) public UUID protocolId;
    @Column(name="protocol_version_no", nullable=false) public int protocolVersionNo;
    @Column(name="patrol_execution_id") public UUID patrolExecutionId;
    @Column(name="executed_at", nullable=false) public Instant executedAt;
    @Column(name="received_at", nullable=false) public Instant receivedAt;
    @Column(name="latitude") public Double latitude;
    @Column(name="longitude") public Double longitude;
    @Column(name="accuracy_m") public Double accuracyM;
    @Column(name="observation", length=1000) public String observation;
    @Column(name="batch_id", nullable=false) public UUID batchId;
    @Column(name="correlation_id", nullable=false) public UUID correlationId;
    @Column(name="device_id", nullable=false, length=120) public String deviceId;
    @Column(name="payload_hash", nullable=false, length=64) public String payloadHash;
    @Column(name="payload_json", nullable=false, columnDefinition="text") public String payloadJson;
    @Column(name="status", nullable=false, length=24) public String status;
}
